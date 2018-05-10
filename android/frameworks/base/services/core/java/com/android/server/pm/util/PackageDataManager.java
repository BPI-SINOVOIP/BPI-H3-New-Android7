package com.android.server.pm.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageParser;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.FileUtils;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.Log;


public class PackageDataManager {

    static final String TAG = "PkgDataM";
    private static PackageDataManager instance;

    private static final int MAX_DUMP_THREADS = Runtime.getRuntime().availableProcessors()/2+1;
    private static final int GET_PKG_TIMEOUT = 1000;

    static final String DB_NAME = "/data/system/package.db";
    static final String DISPLAY_FILE = "/data/system/lastDisplayMetris";
    static final int DB_VERSION = 2;
    static final String HEAD_TABLE = "head";
    static final String SYSTEMAPPPKG_TABLE = "systemapp";
    static final String DATAAPPPKG_TABLE = "dataapp";
    static class HeadTableColumns {
        public static final String _KEY = "key";
        public static final String _VALUE = "value";
        public static final String K_FINGERPRINT = "FINGERPRINT";
        public static final String K_SCANORDER = "SCANORDER";
    }

    static class PackageTableColumns {
        public static final String _CODEPATH = "codePath";
        public static final String _LASTMODEFY = "modify";
        public static final String _SIZE = "size";
        public static final String _CONTENT = "content";
    }

    class DbHelper {

        private File validateFilePath(String name) {
            File dir = null;
            File f = null;

            int pos = name.lastIndexOf(File.separatorChar);
            if (pos > 0) {
                String dirPath = name.substring(0, pos);
                dir = new File(dirPath);
                name = name.substring(pos);
                f = new File(dir, name);
            } else {
                f = new File(name);
            }

            if (dir != null && !dir.isDirectory() && dir.mkdir()) {
                FileUtils.setPermissions(dir.getPath(),
                        FileUtils.S_IRWXU|FileUtils.S_IRWXG|FileUtils.S_IXOTH,
                        -1, -1);
            }
            return f;
        }

        public SQLiteDatabase getWritableDatabase(){
            File f = validateFilePath(DB_NAME);
            int flags = SQLiteDatabase.CREATE_IF_NECESSARY;
            SQLiteDatabase db = SQLiteDatabase.openDatabase(f.getPath(), null, flags, null);
            final int version = db.getVersion();
            if (version != DB_VERSION) {
                if (version == 0) {
                    onCreate(db);
                } else {
                    clearDb(db);
                }
                db.setVersion(DB_VERSION);
            }
            return db;
        }
        public void onCreate(SQLiteDatabase db) {
            createHeadTable(db);
            createPkgTable(db,SYSTEMAPPPKG_TABLE);
            createPkgTable(db,DATAAPPPKG_TABLE);
        }

        private void createHeadTable(SQLiteDatabase db) {
            final String sql = "CREATE TABLE " + HEAD_TABLE + " (" + HeadTableColumns._KEY +" TEXT PRIMARY KEY, "
                + HeadTableColumns._VALUE +" TEXT);";
            db.execSQL(sql);
            insertHeadTable(db, HeadTableColumns.K_FINGERPRINT, Build.FINGERPRINT);
        }

        private void insertHeadTable(SQLiteDatabase db, String key, String value) {
            final String sql = "INSERT INTO " + HEAD_TABLE + " (" + HeadTableColumns._KEY + " , " + HeadTableColumns._VALUE + " ) "
                + " VALUES ('" + key + "', '" + value + "');";
            db.execSQL(sql);
        }
        private void createPkgTable( SQLiteDatabase db, String tableName) {

            StringBuilder sb = new StringBuilder("CREATE TABLE " + tableName + " (");
            sb.append(PackageTableColumns._CODEPATH + " TEXT PRIMARY KEY");
            sb.append(", " + PackageTableColumns._LASTMODEFY + " LONG");
            sb.append(", " + PackageTableColumns._SIZE + " LONG");
            sb.append(", " + PackageTableColumns._CONTENT + " TEXT");
            sb.append(");");
            db.execSQL(sb.toString());
        }

        public void clearDb(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + HEAD_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + SYSTEMAPPPKG_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + DATAAPPPKG_TABLE);
            createHeadTable(db);
            createPkgTable(db,SYSTEMAPPPKG_TABLE);
            createPkgTable(db,DATAAPPPKG_TABLE);
        }
    }


    DbHelper helper = null;
    SQLiteDatabase mDb = null;
    boolean isWriting = false;
    boolean isReading = false;

    //mode = 0, do nothing; =1 io works, =2 cpu works
    int mode = 0;

    ArrayMap<String, PkgWraper> appDataToRead = new ArrayMap<String, PkgWraper>();
    ArrayMap<String, PkgWraper> appDataToWrite = new ArrayMap<String, PkgWraper>();

    ExecutorService readTasks;
    ArrayList<String> dbScanOrder =  new ArrayList<String>();
    ArrayList<String> trueScanOrder =  new ArrayList<String>();
    DisplayMetrics mDisplayMetrics = null;
    private PackageDataManager(int pmsMode) {
        mode = pmsMode;
        if (mode == 1) {
            File f = new File(DISPLAY_FILE);
            if (!f.exists()) {
                return;
            }
            try {
                ObjectInputStream in = new ObjectInputStream(new FileInputStream(DISPLAY_FILE));
                DisplayMetricsGhost dmg = (DisplayMetricsGhost)in.readObject();
                in.close();
                mDisplayMetrics = dmg.dumpFromGhost();
                Log.w(TAG, " mDisplayMetrics : " + mDisplayMetrics.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (mode == 2) {
            /*
            helper = new DbHelper();
            CertificateGhost g = new CertificateGhost(null);
            ManifestDigestGhost m = new ManifestDigestGhost(null);
            */
        }

    }

    public static PackageDataManager getInstance(int mode) {
        if (instance == null) {
            instance = new PackageDataManager(mode);
        }
        return instance;
    }

    public static class PkgWraper {
        long modifyTime;
        long size;
        String path;
        byte[] content;
        PackageParser.Package pkg;
        boolean beScaned = false;
    }

    private void initDb() {
        if (mDb == null) {
            mDb = helper.getWritableDatabase();
            isWriting =false;
        }
    }

    private void closeDb() {
        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
    }

    private static final boolean isApkFile(File file) {
        return isApkPath(file.getName());
    }

    private static boolean isApkPath(String path) {
        return path.endsWith(".apk");
    }

    private static File getApkFile(String path) {
        File apkPath = new File(path);
        if (apkPath.isDirectory()) {
            final File[] files = apkPath.listFiles();
            for (File file : files) {
                if (isApkFile(file)) {
                    return file;
                }
            }
        } else if (isApkFile(apkPath)) {
            return apkPath;
        }
        return null;
    }
    private static String converStringListToString(ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (s == null || s.equals("")) {
                continue;
            }
            sb.append(s);
            sb.append(";");
        }
        return sb.toString();
    }

    private static void converStringToStringList(String srcString, ArrayList<String> list) {
        if (srcString == null || list == null)
            return;
        list.clear();
        String[] ss = srcString.split(";");
        for (String s : ss) {
            if (s.equals("")) {
                continue;
            }
            list.add(s);
        }
    }

    public boolean addPkgParserData(PackageParser.Package pkg) {
        /*
        if (mode < 2) {
            return true;
        }
        if (pkg == null) {
            return false;
        }
        if (pkg.mPath == null) {
            return false;
        }
        if (isWriting) {
            return false;
        }
        PkgWraper pw = new PkgWraper();
        pw.path = pkg.mPath;
        File apkFile = new File(pkg.mPath);
        pw.modifyTime = apkFile.lastModified();
        pw.size = apkFile.length();
        pw.content = Helper.serializePackage(pkg);
        appDataToWrite.put(pw.path, pw);
        */
        return true;
    }

    public boolean updateData(DisplayMetrics displayMetrics) {
        Log.w(TAG, " updateData  with mode:"+mode);
        if (mode == 1) {
            if (displayMetrics == null) {
                Log.w(TAG, " displayMetrics == null ??? !!!");
                return false;
            }
            try {
                ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(DISPLAY_FILE));
                DisplayMetricsGhost dmg = new DisplayMetricsGhost(displayMetrics);
                out.writeObject(dmg);
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return true;
        } else if (mode == 2) {
            /*
            if (isWriting) {
                return false;
            }
            if (readTasks != null && (!readTasks.isTerminated())) {
                Log.w(TAG, " read task not finish !!!");
                return false;
            }
            isWriting = true;
            initDb();
            ContentValues cv = new ContentValues();

            Collection<PkgWraper> set = appDataToRead.values();
            Log.i(TAG, " begin clean not scan pkg");
            for (PkgWraper pw : set) {
                if (!pw.beScaned) {
                    Log.i(TAG, " clean not scan pkg " + pw.path );
                    final String whereClause = PackageTableColumns._CODEPATH + " = ?";
                    String[] whereArgs = {pw.path};
                    if (pw.path.startsWith("/system")) {
                        mDb.delete(SYSTEMAPPPKG_TABLE, whereClause, whereArgs); 
                    } else if (pw.path.startsWith("/data")) {
                        mDb.delete(DATAAPPPKG_TABLE, whereClause, whereArgs); 
                    }
                }
            }
            appDataToRead.clear();

            Set<String> keySet = appDataToWrite.keySet();
            Iterator<String> iterator = keySet.iterator();
            Log.i(TAG, " begin save pkg");
            while (iterator.hasNext()) {
                String key = iterator.next();
                PkgWraper pw = appDataToWrite.get(key);
                if (pw != null && pw.content != null) {
                    cv.put(PackageTableColumns._CODEPATH, pw.path);
                    cv.put(PackageTableColumns._LASTMODEFY, pw.modifyTime);
                    cv.put(PackageTableColumns._SIZE, pw.size);
                    cv.put(PackageTableColumns._CONTENT, pw.content);
                    Log.i(TAG, "save pkg : " + pw.path);

                    if (pw.path.startsWith("/system")) {
                        mDb.insertWithOnConflict(SYSTEMAPPPKG_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    } else if (pw.path.startsWith("/data")){
                        mDb.insertWithOnConflict(DATAAPPPKG_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }
                cv.clear();
            }
            if (trueScanOrder.size() > 0) {
                String scanOrder = converStringListToString(trueScanOrder);
                cv.put(HeadTableColumns._KEY, HeadTableColumns.K_SCANORDER);
                cv.put(HeadTableColumns._VALUE, scanOrder);
                Log.i(TAG, " save scanorder ");
                mDb.insertWithOnConflict(HEAD_TABLE,null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                trueScanOrder.clear();
            }
            closeDb();
            appDataToWrite.clear();
            isWriting = false;
            return true;
            */
        }
        return true;
    }

    public PackageParser.Package getPkgParserData(String apkPath) {
        if (mode == 0) {
            return null;
        }
        trueScanOrder.add(apkPath);
        PkgWraper pw = appDataToRead.get(apkPath);
        if (pw == null) {
            return null;
        }
        if (mode == 1) {
            return pw.pkg;
        } else if (mode == 2) {
            /*
            File f = getApkFile(apkPath);
            if (f.lastModified() == pw.modifyTime && f.length() == pw.size) {
                synchronized (pw){
                    if (pw.pkg != null) {
                        pw.beScaned = true;
                        return pw.pkg;
                    }
                    try {
                        pw.wait(GET_PKG_TIMEOUT);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                        return null;
                    }
                    if (pw.pkg != null) {
                        pw.beScaned = true;
                        return pw.pkg;
                    }
                }
            }
            */
        }
        return null;
    }

    /*
    static class DumpPkgFromGhost implements Runnable {
        final PkgWraper mPw;
        public DumpPkgFromGhost(PkgWraper pw) {
            mPw = pw;
        }
        @Override
        public void run() {
            // TODO Auto-generated method stub
            if (mPw == null) return;
            synchronized (mPw){
                Log.i(TAG, " dumping "+mPw.path);
                mPw.pkg = Helper.parsePackage(mPw.content);
                mPw.notify();
            }
        }
    }
    */
    static class ParsePkgTask implements Runnable {
        final String path;
        final ArrayMap<String, PkgWraper> appDataToRead;
        final DisplayMetrics mDisplayMetrics;
        public ParsePkgTask(String aPath, ArrayMap<String, PkgWraper> list, DisplayMetrics m) {
            path = aPath;
            appDataToRead = list;
            mDisplayMetrics = m;
        }
        @Override
        public void run() {
            // TODO Auto-generated method stub
            int parseFlags = PackageParser.PARSE_MUST_BE_APK;
            PackageParser pp = new PackageParser();
            //pp.setSeparateProcesses(null);
            pp.setOnlyCoreApps(false);
            File scanFile = new File(path);
            if (path.startsWith("/system/framework")) {
                parseFlags |= (PackageParser.PARSE_IS_SYSTEM | PackageParser.PARSE_IS_SYSTEM_DIR
                        | PackageParser.PARSE_IS_PRIVILEGED);
            } else if (path.startsWith("/system/priv-app")) {
                parseFlags |= (PackageParser.PARSE_IS_SYSTEM | PackageParser.PARSE_IS_SYSTEM_DIR
                        | PackageParser.PARSE_IS_PRIVILEGED);
            } else if (path.startsWith("/system/app")){
                parseFlags |= PackageParser.PARSE_IS_SYSTEM | PackageParser.PARSE_IS_SYSTEM_DIR;
            } else if (path.startsWith("/data/app/")){
            } else if (path.startsWith("/data/app-private")){
                parseFlags |= PackageParser.PARSE_FORWARD_LOCK;
            } else {
                return;
            }
            Log.i(TAG,"parse "+path + " "+parseFlags);
            PkgWraper pw = new PkgWraper();
            pp.setDisplayMetrics(mDisplayMetrics);
            try {
            pw.pkg = pp.parsePackage(scanFile, parseFlags);
            } catch(Exception e) {
            }
            synchronized(appDataToRead){
                appDataToRead.put(path, pw);
            }
        }
    }

    private static final boolean isPackageFilename(String name) {
        return name != null && name.endsWith(".apk");
    }

    public void readData() {
        Log.w(TAG, " readData  with mode:"+mode);
        if (mode == 1) {
            if (mDisplayMetrics == null) {
                return;
            }
            readTasks = Executors.newFixedThreadPool(MAX_DUMP_THREADS);
            readTasks.submit(new ParsePkgTask("/system/framework/framework-res.apk", appDataToRead, mDisplayMetrics));
            File privilegedAppDir = new File("/system/priv-app");
            String[] files = privilegedAppDir.list();
            for (int i=0; i<files.length; i++) {
                File file = new File(privilegedAppDir, files[i]);
                /*
                if ( !file.isDirectory() || !isPackageFilename(files[i])) {
                    // Ignore entries which are not apk's
                    continue;
                }
                */
                readTasks.submit(new ParsePkgTask(file.getPath(), appDataToRead, mDisplayMetrics));
            }
            File systemAppDir = new File("/system/app");
            files = systemAppDir.list();
            for (int i=0; i<files.length; i++) {
                File file = new File(systemAppDir, files[i]);
                readTasks.submit(new ParsePkgTask(file.getPath(), appDataToRead, mDisplayMetrics));
            }
            File mAppInstallDir = new File("/data/app");
            files = mAppInstallDir.list();
            for (int i=0; i<files.length; i++) {
                File file = new File(mAppInstallDir, files[i]);
                readTasks.submit(new ParsePkgTask(file.getPath(), appDataToRead, mDisplayMetrics));
            }
            File mDrmAppPrivateInstallDir = new File("/data/app-private");
            files = mDrmAppPrivateInstallDir.list();
            for (int i=0; i<files.length; i++) {
                File file = new File(mDrmAppPrivateInstallDir, files[i]);
                if (!file.isDirectory() || !isPackageFilename(files[i])) {
                    // Ignore entries which are not apk's
                    continue;
                }
                readTasks.submit(new ParsePkgTask(file.getPath(), appDataToRead, mDisplayMetrics));
            }
            readTasks.shutdown();
            return;
        } else if (mode == 2) {
            /*
            if (isReading) {
                return;
            }
            if (appDataToRead.size()>0 ) {
                return;
            }
            isReading = true;
            Thread readingThread = new Thread(){
                @Override
                    public void run() {
                        initDb();
                        Cursor cursor = mDb.query(HEAD_TABLE, null , null, null, null, null, null);
                        final int headTableValuePos = cursor.getColumnIndex(HeadTableColumns._VALUE);
                        final int headTableKeyPos = cursor.getColumnIndex(HeadTableColumns._KEY);
                        String dbFingerPrint = null;
                        while (cursor.moveToNext()) {
                            String key = cursor.getString(headTableKeyPos);
                            if (key.equals(HeadTableColumns.K_FINGERPRINT)) {
                                dbFingerPrint = cursor.getString(headTableValuePos);
                            }
                            if (key.equals(HeadTableColumns.K_SCANORDER)) {
                                converStringToStringList(cursor.getString(headTableValuePos), dbScanOrder);
                            }
                        }
                        if (!Build.FINGERPRINT.equals(dbFingerPrint)) {
                            clear();
                            return;
                        }

                        readTasks = Executors.newFixedThreadPool(MAX_DUMP_THREADS);

                        final boolean dumpGhostInScanOrder = (dbScanOrder.size() != 0);

                        cursor = mDb.query(SYSTEMAPPPKG_TABLE, null , null, null, null, null, null);
                        int pathPos = cursor.getColumnIndex(PackageTableColumns._CODEPATH);
                        int lmPos = cursor.getColumnIndex(PackageTableColumns._LASTMODEFY);
                        int sizePos = cursor.getColumnIndex(PackageTableColumns._SIZE);
                        int ctPos = cursor.getColumnIndex(PackageTableColumns._CONTENT);

                        while (cursor.moveToNext()) {
                            PkgWraper pw = new PkgWraper();
                            pw.path = cursor.getString(pathPos);
                            pw.modifyTime = cursor.getLong(lmPos);
                            pw.size = cursor.getLong(sizePos);
                            pw.content = cursor.getBlob(ctPos);
                            appDataToRead.put(pw.path, pw);
                            if (!dumpGhostInScanOrder) {
                                readTasks.submit(new DumpPkgFromGhost(pw));
                            }
                        }
                        cursor = mDb.query(DATAAPPPKG_TABLE, null , null, null, null, null, null);
                        while (cursor.moveToNext()) {
                            PkgWraper pw = new PkgWraper();
                            pw.path = cursor.getString(pathPos);
                            pw.modifyTime = cursor.getLong(lmPos);
                            pw.size = cursor.getLong(sizePos);
                            pw.content = cursor.getBlob(ctPos);
                            appDataToRead.put(pw.path, pw);
                            if (!dumpGhostInScanOrder) {
                                readTasks.submit(new DumpPkgFromGhost(pw));
                            }
                        }
                        for (int i = 0; i < dbScanOrder.size(); i++) {
                            readTasks.submit(new DumpPkgFromGhost(appDataToRead.get(dbScanOrder.get(i))));
                        }
                        readTasks.shutdown();
                        closeDb();
                        isReading = false;
                    }
            };
            readingThread.run();
            */
        }
    }

    public void waitForReadTasksFinish() {
        if (mode == 1 && readTasks != null) {
            try {
                readTasks.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public void clear() {
        if (mode == 1) {
            return;
        } else if (mode == 2) {
            /*
            initDb();
            helper.clearDb(mDb);
            closeDb();
            */
        }
    }

}
