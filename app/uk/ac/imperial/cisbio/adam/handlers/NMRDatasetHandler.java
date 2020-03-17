package uk.ac.imperial.cisbio.adam.handlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.MimeType;

import org.apache.tika.metadata.Metadata;

import uk.ac.imperial.cisbio.adam.Handler;
import uk.ac.imperial.cisbio.adam.Item;
import uk.ac.imperial.cisbio.adam.ItemDAO;

public class NMRDatasetHandler extends Handler {

	public NMRDatasetHandler() {
		super("dataset", "inode/directory");
	}

	@Override
	public boolean handles(MimeType mimeType, File file) {
		if (super.handles(mimeType, file)) {
			File[] subfolders = file.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pathname.isDirectory();
				}
			});
			if (subfolders.length > 0) {
				boolean valid = true;
				for (File f : subfolders) {
					valid &= NMR_ACQUISITION_FILTER.accept(f);
				}
				return valid;
			}
		}
		return false;
	}

	@Override
	public boolean handle(Item item, Metadata metadata, ItemDAO items) throws Exception {
		item.tags.add("NMR");
		item.subtype = "NMR";
		File[] subfolders = item.getFile().listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory();
			}
		});
		item.assays = subfolders.length;
		item.modified = 0L;
		for (File f : subfolders) {
			Map<String, String> acqus = readAcqus(f);
			item.modified = Math.max(item.modified , Long.parseLong(acqus.get("DATE")));
		}
		return true;
	}

	private static final FileFilter NMR_ACQUISITION_FILTER = new FileFilter() {
		@Override
		public boolean accept(File pathname) {
			return new File(pathname, "acqus").exists();
		}
	};

	private static Map<String, String> readAcqus(File raw) throws IOException {
		Pattern p1 = Pattern.compile("##[$](\\S+)= (\\S+)");
		Pattern p2 = Pattern.compile("[(]0[.][.](\\d+)[)]");
		Map<String, String> map = new HashMap<String, String>();
		BufferedReader br = new BufferedReader(new FileReader(new File(raw, "acqus")));
		String line;
		while ((line = br.readLine()) != null) {
			Matcher m1 = p1.matcher(line);
			if (m1.find()) {
				Matcher m2 = p2.matcher(m1.group(2));
				if (m2.find()) {
					for (int i = 0; i < Integer.parseInt(m2.group(1));) {
						for (String s : br.readLine().split(" ")) {
							map.put(String.format("%s[%d]", m1.group(1), i), s);
							i++;
						}
					}
				} else {
					map.put(m1.group(1), m1.group(2));
				}
			}
		}
		return map;
	}
}
