package uk.ac.imperial.cisbio.adam.handlers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.apache.tika.metadata.Metadata;

import uk.ac.imperial.cisbio.adam.Handler;
import uk.ac.imperial.cisbio.adam.Item;
import uk.ac.imperial.cisbio.adam.ItemDAO;

public class ScriptHandler extends Handler {
	private static Map<String, String> subtypes = new HashMap<String, String>() {{
		put("text/x-python", "Python");
		put("text/x-matlab", "MATLAB");
		put("text/x-r", "R");
	}};

	public ScriptHandler() {
		super("script", subtypes.keySet().toArray(new String[0]));
	}

	@Override
	public boolean handle(Item item, Metadata metadata, ItemDAO items) throws Exception {
		item.line_count = lines(item.getFile());
		item.subtype = subtypes.get(item.format);
		return true;
	}

	private static long lines(File file) throws IOException {
		return new Scanner(Runtime.getRuntime().exec(new String[]{"wc", "-l", file.getPath()}).getInputStream()).nextLong();
	}
}
