package uk.ac.imperial.cisbio.adam.handlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import javax.activation.MimeType;

import org.apache.tika.metadata.Metadata;

import uk.ac.imperial.cisbio.adam.Handler;
import uk.ac.imperial.cisbio.adam.Item;
import uk.ac.imperial.cisbio.adam.ItemDAO;

public class MSDatasetHandler extends Handler {

	public MSDatasetHandler() {
		super("dataset", "inode/directory");
	}

	@Override
	public boolean handles(MimeType mimeType, File file) {
		return super.handles(mimeType, file) && file.getName().endsWith(".raw") && new File(file, "_HEADER.TXT").exists();
	}

	@Override
	public boolean handle(Item item, Metadata metadata, ItemDAO items) throws Exception {
		Map<String, String> header = readHeader(item.getFile());
		item.subtype = "MS";
		item.title = header.get("Acquired Name");
		item.modified = new SimpleDateFormat("dd-MMM-yyyy").parse(header.get("Acquired Date")).getTime() / 1000;
		return true;
	}

	private static Map<String, String> readHeader(File raw) throws IOException {
		Map<String, String> header = new HashMap<String, String>();
		BufferedReader br = new BufferedReader(new FileReader(new File(raw, "_HEADER.TXT")));
		for (String line = br.readLine(); line != null; line = br.readLine()) {
			String[] parts = line.split("\\$\\$ ")[1].split(":", 2);
			if (!"".equals(parts[1].trim())) {
				header.put(parts[0], parts[1].trim());
			}
		}
		return header;
	}

}
