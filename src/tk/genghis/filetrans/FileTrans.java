package tk.genghis.filetrans;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 文件转换工具
 * 
 * @author Genghis
 * @version 1.0
 */
public class FileTrans {

	private static final int MAX_ARGUMENT_NUM = 20;

	private String versionInfo = "Version 1.0\nFileTrans tool is created by Genghis Yang. All rights reserved.";

	/**
	 * 入口
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		FileTrans fileTrans = new FileTrans();
		fileTrans.startFileTrans(args);
	}

	/**
	 * 启动方法
	 * 
	 * @param args
	 */
	public void startFileTrans(String[] args) {
		try {
			processArgs(args);
		} catch (Throwable t) {
			printExceptionMessage(t);
			System.exit(1);
			return;
		}
	}

	/**
	 * 参数分析处理
	 * 
	 * @param args
	 */
	private void processArgs(String[] args) {
		if (args[0].equals("-help") || args[0].equals("-h")) {
			printUsage();
		} else if (args[0].equals("-version") || args[0].equals("-v")) {
			printVersion();
		}
		for (int i = 1; i < args.length; i++) {
			callFunction(new File(args[0]), args[i]);
		}
	}

	/**
	 * 调用对应的功能类，并执行
	 * 
	 * @param arg
	 */
	private void callFunction(File file, String arg) {
		String[] splitedCommand = arg.split("-", MAX_ARGUMENT_NUM);
		String functionClassName = this.getClass().getPackage().getName() + "." + splitedCommand[1];
		try {
			Class<?> functionClazz = Class.forName(functionClassName);
			Method executeMethod = functionClazz.getMethod("execute", File.class, String[].class);
			String[] arguments = new String[splitedCommand.length - 2];
			System.arraycopy(splitedCommand, 2, arguments, 0, splitedCommand.length - 2);
			executeMethod.invoke(functionClazz.newInstance(), file, (Object[]) arguments);
			System.out.println("\nThe function " + splitedCommand[1] + " execute successfully.\n");
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException
				| InstantiationException e) {
			throw new RuntimeException("Not found function: " + splitedCommand[1]);
		} catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
			throw new RuntimeException("Invalid arguments for this function: " + arg);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e.getCause());
		}
	}

	/**
	 * 打印版本信息
	 */
	private void printVersion() {
		System.out.println(versionInfo);
	}

	/**
	 * 打印用法信息
	 */
	private void printUsage() {
		String lSep = System.getProperty("line.separator");
		String tab = "  ";
		StringBuffer msg = new StringBuffer();
		msg.append("Usage:").append(lSep);
		msg.append(tab)
				.append("filetrans <file> -function[-argument1][-argument2][...] [-function2[-argument3][...]] [...]")
				.append(lSep);
		msg.append("Options: ").append(lSep);
		msg.append(tab).append("-function                  the function to be executed" + lSep);
		msg.append(tab).append("-argument1,-argument2,...  arguments for the special function" + lSep);
		msg.append(tab).append("-help, -h                  print this usage infomation" + lSep);
		msg.append(tab).append("-version, -v               print the version information and exit" + lSep);
		msg.append(lSep).append("Following are usages of various functions").append(lSep);
		try {
			appendFuntionsInfo(msg);
		} catch (IOException e) {
			System.err.println("Cannot read function class file properly.");
			e.printStackTrace();
		}
		System.out.println(msg);
	}

	/**
	 * 附加各个function的用法信息
	 * 
	 * @param msg
	 * @throws IOException
	 */
	private void appendFuntionsInfo(StringBuffer msg) throws IOException {
		String packageName = this.getClass().getPackage().getName();
		String packagePath = packageName.replace('.', '/');
		Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(packagePath);
		Set<Class<?>> clazzes = new LinkedHashSet<Class<?>>();
		while (resources.hasMoreElements()) {
			URL url = resources.nextElement();
			String protocol = url.getProtocol();
			if ("file".equals(protocol)) {
				fetchClassesFromClassFile(packageName, url, clazzes);
			} else if ("jar".equals(protocol)) {
				fetchClassesFromJar(packagePath, url, clazzes);
			}
		}
		for (Class<?> clazz : clazzes) {
			try {
				((IFileTransFunction) clazz.newInstance()).helpInfo(msg);
			} catch (InstantiationException | IllegalAccessException | ClassCastException e) {
			}
		}
	}

	/**
	 * 查找class文件中具有指定包名的类
	 * 
	 * @param packageName
	 * @param url
	 * @param clazzes
	 * @throws IOException
	 */
	private void fetchClassesFromClassFile(String packageName, URL url, Set<Class<?>> clazzes) throws IOException {
		String packagePath = URLDecoder.decode(url.getFile(), "UTF-8");
		File packageDir = new File(packagePath);
		// 如果不存在或者 也不是目录就直接返回
		if (!packageDir.exists() || !packageDir.isDirectory()) {
			return;
		}
		// 如果存在 就获取包下的所有文件 包括目录
		File[] dirfiles = packageDir.listFiles(new FileFilter() {
			// 自定义过滤规则 如果可以循环(包含子目录) 或则是以.class结尾的文件(编译好的java类文件)
			public boolean accept(File file) {
				return (!file.isDirectory() && file.getName().endsWith(".class"));
			}
		});
		for (File file : dirfiles) {
			String className = file.getName().substring(0, file.getName().length() - 6);
			try {
				// 添加到集合中去
				clazzes.add(Class.forName(packageName + '.' + className));
			} catch (ClassNotFoundException e) {
				throw new RuntimeException("Read function class " + className + " failed.", e);
			}
		}
	}

	/**
	 * 在Jar文件中检索具有指定包名的类
	 * 
	 * @param packagePath
	 * @param url
	 * @param clazzes
	 * @throws IOException
	 */
	private void fetchClassesFromJar(String packagePath, URL url, Set<Class<?>> clazzes) throws IOException {
		JarFile jar = ((JarURLConnection) url.openConnection()).getJarFile();
		Enumeration<JarEntry> entries = jar.entries();
		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			String entryName = entry.getName();
			if (entryName.charAt(0) == '/') {
				entryName = entryName.substring(1);
			}
			if (entryName.startsWith(packagePath)) { // FIXME: not package?
				int idx = entryName.lastIndexOf('/');
				// 以"/"结尾 是一个包,是一个.class文件,而且不是目录
				if (idx != -1 && entryName.endsWith(".class") && !entry.isDirectory()) {
					String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
					try {
						clazzes.add(Class.forName(className));
					} catch (ClassNotFoundException e) {
						throw new RuntimeException("Read function class " + className + " failed.|", e);
					}
				}
			}
		}
	}

	/**
	 * 打印错误信息
	 * 
	 * @param t
	 */
	private void printExceptionMessage(Throwable t) {
		String message = t.getMessage();
		if (message != null) {
			System.err.println(message);
		}
	}
}
