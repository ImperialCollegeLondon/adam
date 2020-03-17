package uk.ac.imperial.cisbio.adam;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;

import org.apache.commons.io.FileUtils;

import play.Logger;

public class Util {

	public static byte[] toBytes(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(image, format, baos);
		return baos.toByteArray();
	}

	public static String humanReadableByteCount(long bytes) {
		int unit = 1024;
		if (bytes < unit) return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		return String.format("%.1f %siB", bytes / Math.pow(unit, exp), "KMGTPE".charAt(exp - 1));
	}

	//takes a folder, returns folder + all child folders (recursive)
	public static List<File> folders(File folder) {
		List<File> folders = new ArrayList<File>(Collections.singleton(folder));
		for (File childFolder : folder.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.isDirectory();
			}
		})) {
			folders.addAll(folders(childFolder));
		}
		return folders;
	}

	// in: /a/b/c.jpg out: /a/b/c.jpg, a/b, a
	public static List<String> paths(String path) {
		List<String> paths = new ArrayList<String>();
		for (File file = new File(path); file.getParentFile() != null; file = file.getParentFile()) {
			paths.add(file.getPath());
		}
		return paths;
	}

	public static void zip(File directory, OutputStream out) {
		try {
			ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(out));
			zos.setLevel(ZipOutputStream.STORED);
			zip(directory, directory.getParentFile(), zos);
			zos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static boolean upload(String username, File file, String path, String root) throws IOException {
		File folder = new File(new File(root, username), path);
		if (folder.isDirectory()) {
			FileUtils.moveFileToDirectory(file, folder, false);
			//			File dest = new File(folder, file.getName());
			//			boolean success = file.renameTo(dest);
			//			if (success) {
			//				//Runtime.getRuntime().exec(new String[]{ "chown", username, dest.getPath() });
			//				return true;
			//			}
		}
		Logger.error(String.format("Failed to upload %s %s %s", username, file.getName(), path));
		return false;
	}

	private static void zip(File file, File rootDirectory, ZipOutputStream out) throws IOException {
		if (file.isDirectory()) {
			//Note that we don't create entries for empty directories - they aren't supported by the zip format
			for (File f : file.listFiles()) {
				zip(f, rootDirectory, out);
			}
		} else {
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
			out.putNextEntry(new ZipEntry(file.getPath().substring(rootDirectory.getPath().length())));
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			out.closeEntry();
			in.close();
		}
	}

	public static long size(File file) throws IOException {
		return new Scanner(Runtime.getRuntime().exec(new String[]{"du", "-s", file.getPath()}).getInputStream()).nextLong();
	}
}
