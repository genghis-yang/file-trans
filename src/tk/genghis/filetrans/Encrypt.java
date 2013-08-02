package tk.genghis.filetrans;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 文件加密类，将文件每一字节与加密秘钥按位异或。
 * 
 * @author Genghis
 * @version 1.0
 * 
 */
public class Encrypt implements IFileTransFunction {

	/**
	 * 文件读写缓冲区大小
	 */
	private static final int BUFFER_SIZE = 2048;
	/**
	 * 加密密钥
	 */
	private static final int KEY = 0x38;

	@Override
	public void execute(File file, String... arguments) {
		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;
		File outFile = new File(arguments[0]);
		try {
			try {
				bis = new BufferedInputStream(new FileInputStream(file));
			} catch (FileNotFoundException e) {
				throw new RuntimeException("No file found: " + file.getAbsolutePath(), e);
			}
			try {
				bos = new BufferedOutputStream(new FileOutputStream(outFile));
			} catch (Exception e) {
				throw new RuntimeException("Cannot create file: " + outFile.getAbsolutePath(), e);
			}
			byte[] cbuf = new byte[BUFFER_SIZE];
			int readCount = readSrcFile(file, bis, cbuf);
			while (readCount != -1) {
				encrypt(cbuf, readCount);
				writeToFile(bos, outFile, cbuf, readCount);
				readCount = readSrcFile(file, bis, cbuf);
			}
		} finally {
			closeStream(bos);
			closeStream(bis);
		}
	}

	/**
	 * 将源文件内容读入缓冲区
	 * 
	 * @param file
	 *            源文件
	 * @param bis
	 * @param cbuf
	 *            缓冲区
	 * @return 读取的字节数
	 */
	private int readSrcFile(File file, BufferedInputStream bis, byte[] cbuf) {
		try {
			return bis.read(cbuf, 0, BUFFER_SIZE);
		} catch (IOException e) {
			throw new RuntimeException("File cannot be read: " + file.getAbsolutePath(), e);
		}
	}

	/**
	 * 将缓冲区内容写入文件
	 * 
	 * @param bos
	 * @param outFile
	 *            输出的文件
	 * @param cbuf
	 *            缓冲区
	 * @param readCount
	 *            需要写入的字节数
	 */
	private void writeToFile(BufferedOutputStream bos, File outFile, byte[] cbuf, int readCount) {
		try {
			bos.write(cbuf, 0, readCount);
		} catch (IOException e) {
			throw new RuntimeException("File cannot be written: " + outFile.getAbsolutePath(), e);
		}
	}

	/**
	 * 加密算法（每个字节按位与密钥异或）
	 * 
	 * @param cbuf
	 *            缓冲区
	 * @param readCount
	 *            需要处理的字节数
	 */
	private void encrypt(byte[] cbuf, int readCount) {
		for (int i = 0; i < readCount; i++) {
			cbuf[i] = (byte) (cbuf[i] ^ KEY);
		}
	}

	/**
	 * 关闭流
	 * 
	 * @param stream
	 */
	private void closeStream(Closeable stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
			}
		}
	}

	@Override
	public void helpInfo(StringBuffer msg) {
		String lSep = System.getProperty("line.separator");
		String tab = "  ";
		msg.append(this.getClass().getSimpleName() + " function argument:").append(lSep);
		msg.append(tab).append("-<outfile>                 the encrypted file name.").append(lSep);
	}
}
