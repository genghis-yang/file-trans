package tk.genghis.filetrans;

import java.io.File;

/**
 * FileTrans功能类的接口，所有功能类必须实现此接口。
 * 
 * @author Genghis
 * @version 1.0
 * 
 */
public interface IFileTransFunction {

	/**
	 * 执行入口
	 * 
	 * @param file
	 * @param arguments
	 */
	void execute(File file, String... arguments);

	/**
	 * 帮助信息
	 * 
	 * @param msg
	 */
	void helpInfo(StringBuffer msg);
}
