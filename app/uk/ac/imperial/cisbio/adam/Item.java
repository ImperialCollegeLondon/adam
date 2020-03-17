package uk.ac.imperial.cisbio.adam;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Id;
import com.google.code.morphia.annotations.Indexed;
import com.mongodb.DBRef;

@Entity(value="items", noClassnameStored = true)
public class Item {
	@Id ObjectId id;

	//all types
	@Indexed(unique=true) public String path;

	//all recognised types (pre-population)
	@Indexed public String type;
	public String format;
	//all recognised types (post-population)
	@Indexed public Long created_at;
	@Indexed public Long modified_at;
	@Indexed public String name;
	public Long size;
	@Indexed public String owner;

	//all recognised types (if/when shared)
	public String recipient;

	//folders
	public Long fileCount;

	//files
	public String title;//currently articles, documents and images
	//String md5sum;
	public String subtype;//'spreadsheet', 'python', 'nmr' etc.
	//images
	public Long image_count;
	public DBRef thumbnail;
	public String barcode;
	//documents
	public Long line_count;
	public Long word_count;
	public String text;
	public String last_author;
	public Long modified;
	@Indexed public String language;
	public Set<Map<String, String>> entities;
	//articles
	public List<String> creator;
	public String source;
	public String pmid;
	public String date;
	public String identifier;
	public Set<String> mesh;
	//microarray datasets
	public String array_type;
	public String algorithm;
	public String geo_platform;
	//public Integer average_intensity;
	//public String organism;//temporary
	public Set<String> tags = new HashSet<String>();//only currently for datasets, but generally intended to store non-user specific tags
	//nmr datasets
	public Integer assays;

	public Item() {
	}

	public Item(String path) {
		this.path = path;
	}

	public File getFile() {
		return new File(ADAM.root, path);
	}

	@Override
	public String toString() {
		return path;
	}

	//      public static void main(String[] args) throws Exception {
	//              Mongo mongo = new Mongo();
	//              ItemDAO items = new ItemDAO(new Morphia(), mongo);
	//              items.getDatastore().ensureIndexes();
	//              items.save(new Item("/home/a.txt"));
	//              items.save(new Item("/home/a.txt"));
	//              System.out.println(items.find().asList());
	//              System.out.println(JSON.serialize(mongo.getDB("myDB").getCollection("Item").find()));
	//              new ObjectMapper().writeValue(System.out, items.find().asList());
	//      }
}