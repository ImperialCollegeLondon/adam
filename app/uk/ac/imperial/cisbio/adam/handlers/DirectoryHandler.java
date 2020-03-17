package uk.ac.imperial.cisbio.adam.handlers;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

import org.apache.tika.metadata.Metadata;

import uk.ac.imperial.cisbio.adam.Handler;
import uk.ac.imperial.cisbio.adam.Item;
import uk.ac.imperial.cisbio.adam.ItemDAO;

public class DirectoryHandler extends Handler {
	public DirectoryHandler() {
		super("folder", "inode/directory");
	}

	@Override
	public boolean handle(Item item, Metadata metadata, ItemDAO items) throws Exception {
		item.fileCount = fileCount(item.getFile());
		return true;
	}

	private static long fileCount(File folder) throws IOException {
		return new Scanner(Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", String.format("ls -1 '%s' | wc -l", folder.getPath())}).getInputStream()).nextLong();
	}
}
