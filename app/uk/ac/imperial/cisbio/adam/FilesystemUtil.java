package uk.ac.imperial.cisbio.adam;


public class FilesystemUtil {
	private FilesystemUtil() throws InstantiationException {
	}

	// /home/mwoodbri/a/b.txt, /home/mwoodbri -> /a/b.txt
	public static String relpath(String root, String path) {
		return path.substring(root.length());
	}
}
