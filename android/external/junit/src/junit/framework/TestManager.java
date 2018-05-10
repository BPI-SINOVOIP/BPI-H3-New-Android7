package junit.framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;

/** @hide - not needed for public API */
public class TestManager extends Object {

	private boolean os; //false Linux true android
	private ArrayList <String> func;
	private final String FUNC_PATH = "/system/etc/func.list";
	private final String FUNC_RESERVE0_PATH = "/Reserve0/func.list";
	public TestManager(){
		File osFile = new File("/system/build.prop");
		if(osFile.exists()){
			os = true;
			func = new ArrayList <String>();
			loadConfigFile(FUNC_PATH);
			loadConfigFile(FUNC_RESERVE0_PATH);
		}
	}
	boolean isThisCaseOk(String name){
		if(os){
			AndroidLogs("CTSTestManager",name);
			return testFuncInConfig(name);
		}
		return false;
	}
	private void AndroidLogs(String tag,String log){
		try{
			Class<?> callClazz = Class.forName("android.util.Log");
			Method method = callClazz.getMethod("d",String.class,String.class);
			method.invoke(null,tag,log);
		}catch(Exception e){
		}
	}
	private void loadConfigFile(String path){
		//readfile
		//decodefile
            try {
                char[] buffer = new char[2028];
                FileReader file = new FileReader(path);
                try {
                    int len = file.read(buffer, 0, 2028);
		    String [] funcs = String.copyValueOf(buffer).split("\n");
		    for(int i=0;i<funcs.length;i++){
			//AndroidLogs("CTSTestManager","read " + funcs[i]);
			func.add(funcs[i]);
		    }
		    //decode(buffer);
                } finally {
                    file.close();
                }
            } catch (Exception e) {
            }
	}
	private String decodeFunc(String func){
	    	return null;
	}
	private boolean testFuncInConfig(String name){
		for(int i=0;i<func.size();i++){
			if(name.startsWith(func.get(i))){
				AndroidLogs("CTSTestManager","found_test item" + func.get(i));
				return true;
			}
		}
		return false;
	}

}
