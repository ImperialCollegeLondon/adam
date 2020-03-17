package controllers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.comparator.NameFileComparator;

import play.mvc.Controller;
import uk.ac.imperial.cisbio.adam.ADAM;
import uk.ac.imperial.cisbio.adam.FilesystemUtil;
import uk.ac.imperial.cisbio.adam.Util;
import controllers.Secure.Security;

public class Download extends Controller {
	private static Pattern pattern = Pattern.compile("/([a-z0-9]{1,8})/.*");
	public static void index(String path, String shared, Boolean zip) throws IOException {
		final String username = Security.connected();
		if (path != null) {
			Matcher m = pattern.matcher(path);
			if (m.matches()) {
				String owner = m.group(1);
				//either: I'm logged in and the file is owned by me or it has been shared with me
				if ((username != null && (owner.equals(username) || ADAM.du.shared(path, username)))
						//or: I'm not logged in but the file has been publicly shared (and the key is in the URL)
						|| (shared != null && ADAM.du.published(path, shared))) {
					File file = new File(ADAM.root, path);
					if (file.isDirectory()) {
						if (Boolean.TRUE.equals(zip)) {
							File tmp = File.createTempFile("adam", null);
							OutputStream os = new FileOutputStream(tmp);
							Util.zip(file, os);
							renderBinary(tmp, String.format("%s.zip", file.getName()));
						} else {
							File[] files = file.listFiles();
							Arrays.sort(files, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);
							String parent = !file.getParent().equals(ADAM.root) ? FilesystemUtil.relpath(ADAM.root, file.getParent()) : null;
							render(path, files, parent, shared);
						}
					} else if (file.isFile()) {
						renderBinary(file);
					}
				} else {
					forbidden();
				}
			}
		}
	}
}
