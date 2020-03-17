package uk.ac.imperial.cisbio.adam;

import com.mongodb.DBRef;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;

public class FSUtil {
	private GridFS fs;

	public FSUtil(GridFS fs) {
		this.fs = fs;
	}

	public DBRef store(byte[] data, String type) {
		GridFSInputFile file = fs.createFile(data);
		file.setContentType(type);
		file.save();
		return new DBRef(fs.getDB(), fs.getBucketName(), file.getId());
	}
}

