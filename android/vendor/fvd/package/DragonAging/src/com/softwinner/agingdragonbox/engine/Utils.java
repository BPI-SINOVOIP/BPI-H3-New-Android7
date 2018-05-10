package com.softwinner.agingdragonbox.engine;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import com.softwinner.agingdragonbox.R;
import com.softwinner.agingdragonbox.engine.testcase.CaseComprehensive;
import com.softwinner.agingdragonbox.engine.testcase.CaseGPUStress;
import com.softwinner.agingdragonbox.engine.testcase.CaseMemory;
import com.softwinner.agingdragonbox.engine.testcase.CaseThreeDimensional;
import com.softwinner.agingdragonbox.engine.testcase.CaseVideo;

public class Utils {

    public static final String                   APP_TAG            = "DragonBox";
    public static final String                   VERSION            = "v1.1.2";
    static {
        Log.w(APP_TAG, "DragonBox version:" + VERSION);
    }

    public static final String                   CONFIGURATION_NODE = "TestCase";

    public static final String[]                 ALL_CASES          = {CaseComprehensive.class
                                                                            .getSimpleName() };

    public static final HashMap<String, Integer> CASE_MAP
        = new HashMap<String, Integer>();
    static {
        // CASE_MAP.put(CaseComprehensive.class.getSimpleName(),
        // R.string.case_comprehensive);
        CASE_MAP.put(CaseMemory.class.getSimpleName(), R.string.case_memory_name);
        CASE_MAP.put(CaseVideo.class.getSimpleName(), R.string.case_video_name);
        CASE_MAP.put(CaseThreeDimensional.class.getSimpleName(),
                R.string.case_threedimensional_name);
        CASE_MAP.put(CaseGPUStress.class.getSimpleName(), R.string.case_threedimensional_name);

    }

    public static BaseCase createCase(String caseName) {
        BaseCase aCase = null;
        try {
            BaseCase clazz = (BaseCase) Class.forName(
                    "com.softwinner.agingdragonbox.engine.testcase." + caseName).newInstance();
            aCase = clazz;
        } catch (ClassNotFoundException e) {
            Log.w(APP_TAG, e.getMessage());
        } catch (ClassCastException e) {
            Log.w(APP_TAG, e.getMessage());
        } catch (InstantiationException e) {
            Log.w(APP_TAG, e.getMessage());
        } catch (IllegalAccessException e) {
            Log.w(APP_TAG, e.getMessage());
        }
        return aCase;
    }

    public static boolean search(ArrayList<File> result, File folder, String[] types,
            boolean returnFirst) {
        File[] fileList = folder.listFiles();
        boolean b = false;
        if (fileList == null) {
            return b;
        }
        for (File file : fileList) {

            if (file.isFile()) {
                boolean tag = false;
                for (String type : types) {
                    // Log.d(Utils.APP_TAG,"file:" + file.getName() + type);
                    tag |= file.getName().endsWith(type);
                }
                if (tag) {
                    result.add(file);
                }
                b |= tag;
            }
            if (returnFirst && result.size() > 0) {
                return b;
            }
            if (file.isDirectory()) {
                b |= search(result, file, types, returnFirst);
            }
        }
        return b;
    }

    public static Object callMethod(Object obj, String methodName, Object[] args,
            Class<?>... aClassType) throws SecurityException, NoSuchMethodException,
            IllegalArgumentException, IllegalAccessException, InvocationTargetException,
            ClassNotFoundException {
        Class<?> aClass = obj.getClass();
        Method method = aClass.getDeclaredMethod(methodName, aClassType);
        Object object = method.invoke(obj, args);
        return object;
    }

    public static Object getFields(Class<?> aClass, String fieldsName) throws SecurityException,
            NoSuchMethodException, IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, NoSuchFieldException {
        Field field = aClass.getField(fieldsName);
        // 原来是field.getInt(_class);改成get(_class)，不知是否会出现问题
        Object object = field.get(aClass);
        return object;
    }

    private static final String[]   CLASS_PATH_PROP  = {"java.class.path", "java.library.path" };

    private static List<File> classPathArray = getClassPath();

    private static List<File> getClassPath() {
        List<File> ret = new ArrayList<File>();
        String delim = ":";
        if (System.getProperty("os.name").indexOf("Windows") != -1) {
            delim = ";";
        }
        for (String pro : CLASS_PATH_PROP) {
            String str = System.getProperty(pro);
            Log.d(APP_TAG, "getProperty:" + System.getProperty(pro));
            if (str != null) {
                String[] pathes = str.split(delim);
                for (String path : pathes) {
                    ret.add(new File(path));
                }
            }
        }
        return ret;
    }

    public static List<String> getClassInPackage(String pkgName) {
        List<String> ret = new ArrayList<String>();
        String rPath = pkgName.replace('.', '/') + "/";
        try {
            for (File classPath : classPathArray) {
                if (!classPath.exists()) {
                    continue;
                }
                if (classPath.isDirectory()) {
                    File dir = new File(classPath, rPath);
                    if (!dir.exists()) {
                        continue;
                    }
                    for (File file : dir.listFiles()) {
                        if (file.isFile()) {
                            String clsName = file.getName();
                            clsName = pkgName + "." + clsName.substring(0, clsName.length() - 6);
                            ret.add(clsName);
                        }
                    }
                } else {
                    FileInputStream fis = new FileInputStream(classPath);
                    JarInputStream jis = new JarInputStream(fis, false);
                    JarEntry e = null;
                    while ((e = jis.getNextJarEntry()) != null) {
                        String eName = e.getName();
                        if (eName.startsWith(rPath) && !eName.endsWith("/")) {
                            ret.add(eName.replace('/', '.').substring(0, eName.length() - 6));
                        }
                        jis.closeEntry();
                    }
                    jis.close();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ret;
    }

    public static void getCaseList(String packageName) throws IOException {
        List<String> cls = getClassInPackage(packageName);
        for (String s : cls) {
            System.out.println(s);
        }
    }
}
