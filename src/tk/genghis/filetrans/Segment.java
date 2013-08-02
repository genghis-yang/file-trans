package tk.genghis.filetrans;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件分割合并类，将文件按用户设定大小进行分割，并能够将分割文件合并
 * 
 * @author Genghis
 * 
 */
public class Segment implements IFileTransFunction {

	/**
	 * 文件读写缓冲区大小
	 */
	private static final int BUFFER_SIZE = 4096;

	@Override
	public void execute(File file, String... arguments) {
		byte[] buf = new byte[BUFFER_SIZE];
		try {
			if (arguments[0].equalsIgnoreCase("M")) {
				mergeFile(file, buf, arguments);
			} else if (arguments[0].equalsIgnoreCase("S")) {
				segregateFile(file, buf, arguments);
			} else {
				throw new RuntimeException("Function FileSegment only support argument segment(S) or Merge(M)");
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new RuntimeException("Function FileSegment cannot run with out arguments.");
		}
	}

	/**
	 * 合并文件
	 * 
	 * @param file
	 *            合并文件中的一个
	 * @param buf
	 *            缓冲区
	 * @param arguments
	 *            用户输入的参数
	 */
	private void mergeFile(File file, byte[] buf, String[] arguments) {
		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;
		List<File> premergeFiles = new ArrayList<File>();
		String mergeFileName = checkFileName(file, premergeFiles);
		try {
			try {
				bos = new BufferedOutputStream(new FileOutputStream(mergeFileName));
			} catch (Exception e) {
				throw new RuntimeException("Cannot create file: " + mergeFileName, e);
			}
			for (File premergeFile : premergeFiles) {
				try {
					bis = new BufferedInputStream(new FileInputStream(premergeFile));
				} catch (FileNotFoundException e) {
					throw new RuntimeException("No file found: " + premergeFile, e);
				}
				int readBytes = readSrcFile(premergeFile, bis, buf);
				while (-1 != readBytes) {
					writeToFile(bos, mergeFileName, buf, readBytes);
					readBytes = readSrcFile(premergeFile, bis, buf);
				}
			}
		} finally {
			closeStream(bos);
			closeStream(bis);
		}
	}

	/**
	 * 将缓冲区内容写入文件
	 * 
	 * @param bos
	 *            输出流
	 * @param mergeFileName
	 *            输出的文件
	 * @param cbuf
	 *            缓冲区
	 * @param readCount
	 *            需要写入的字节数
	 */
	private void writeToFile(BufferedOutputStream bos, String mergeFileName, byte[] cbuf, int readCount) {
		try {
			bos.write(cbuf, 0, readCount);
		} catch (IOException e) {
			throw new RuntimeException("File cannot be written: " + mergeFileName, e);
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
	 * 校验并解析文件名。生成需要合并的文件列表，放入premergeFiles；同时返回合并后的文件名。
	 * 
	 * @param file
	 *            需要合并的文件之一
	 * @param premergeFiles
	 *            需要合并的文件列表，由本函数填充
	 * @return 合并后的文件名
	 */
	private String checkFileName(File file, List<File> premergeFiles) {
		Pattern suffixPattern = Pattern.compile("(\\.part(\\d+)_(\\d+)\\.seg$)");
		Matcher suffixMatcher = suffixPattern.matcher(file.getName());
		if (suffixMatcher.find()) {
			String suffix = suffixMatcher.group(1);
			int thisFileNum = Integer.parseInt(suffixMatcher.group(2));
			int totalNum = Integer.parseInt(suffixMatcher.group(3));
			if (thisFileNum > totalNum) {
				throw new RuntimeException("File " + file.getName()
						+ " is not illegal. Please make sure not change file name after segment.");
			}
			String baseFileName = file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf(suffix));
			for (int i = 1; i <= totalNum; i++) {
				File premergeFile = new File(baseFileName + ".part" + i + "_" + totalNum + ".seg");
				if (!premergeFile.exists()) {
					throw new RuntimeException("Lack file. " + premergeFile.getAbsolutePath() + " cannot be found.");
				}
				premergeFiles.add(premergeFile);
			}
			return baseFileName;
		} else {
			throw new RuntimeException("File " + file.getName() + " is not a segregated file, cannot be merged.");
		}
	}

	/**
	 * 分割文件
	 * 
	 * @param file
	 *            原始文件对象
	 * @param bis
	 *            输入流
	 * @param bos
	 *            输出流
	 * @param buf
	 *            缓冲区
	 * @param arguments
	 *            用户输入的参数
	 */
	private void segregateFile(File file, byte[] buf, String... arguments) {
		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;
		int segSize;
		try {
			segSize = Integer.parseInt(arguments[1]);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Function FileSegment found a wrong argument: " + arguments[1]
					+ " is not a number.");
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new RuntimeException("Segregate file must set two argument: source file and segment size.");
		}
		try {
			try {
				bis = new BufferedInputStream(new FileInputStream(file));
			} catch (FileNotFoundException e) {
				throw new RuntimeException("No file found: " + file.getAbsolutePath());
			}
			long fileSize = file.length();
			List<Long> fragmentsSize = calculateSegment(fileSize, segSize);
			for (int i = 0; i < fragmentsSize.size(); i++) {
				Long fileSeq = fragmentsSize.get(i);
				String segFileName = file.getAbsolutePath() + ".part" + (i + 1) + "_" + fragmentsSize.size() + ".seg";
				try {
					bos = new BufferedOutputStream(new FileOutputStream(segFileName));
				} catch (FileNotFoundException e) {
					throw new RuntimeException("Cannot create file " + segFileName, e);
				}

				long maxReadBytes = fileSeq > BUFFER_SIZE ? BUFFER_SIZE : fileSeq;
				long readBytes = readAndWrite(bis, bos, buf, maxReadBytes);
				long leftFragmentSize = fileSeq - readBytes;
				while (leftFragmentSize > BUFFER_SIZE) {
					readBytes = readAndWrite(bis, bos, buf, maxReadBytes);
					leftFragmentSize -= readBytes;
				}
				readBytes = readAndWrite(bis, bos, buf, leftFragmentSize);
				closeStream(bos);
			}
		} finally {
			closeStream(bis);
			closeStream(bos);
		}
	}

	/**
	 * 从输入流中读取指定字节数到缓冲区中，然后将缓冲区写入输出流
	 * 
	 * @param bis
	 *            输入流
	 * @param bos
	 *            输出流
	 * @param buf
	 *            缓冲区
	 * @param maxReadBytes
	 *            本次最多读入的字节数，也是最多写入的字节数
	 * @return 读取的字节数
	 */
	private long readAndWrite(BufferedInputStream bis, BufferedOutputStream bos, byte[] buf, long maxReadBytes) {
		long readBytes = -1;
		try {
			readBytes = bis.read(buf, 0, (int) maxReadBytes);
			bos.write(buf, 0, (int) readBytes);
		} catch (IOException e) {
			throw new RuntimeException("Read or write file failed.", e);
		}
		return readBytes;
	}

	/**
	 * 计算分割后，每个文件的大小
	 * 
	 * @param fileSize
	 *            待分割文件总大小
	 * @param segSize
	 *            用户设定的分割阈值
	 * @return 分割后每个文件大小的列表
	 */
	private List<Long> calculateSegment(long fileSize, long segSize) {
		List<Long> fragmentsSize = new ArrayList<Long>();
		while (fileSize > segSize) {
			fragmentsSize.add(segSize);
			fileSize -= segSize;
		}
		fragmentsSize.add(fileSize);
		return fragmentsSize;
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
		msg.append(tab).append("-S-<number>                segment the file with <number> bytes.").append(lSep);
		msg.append(tab).append("-M                         merge special files.").append(lSep);
	}
}
