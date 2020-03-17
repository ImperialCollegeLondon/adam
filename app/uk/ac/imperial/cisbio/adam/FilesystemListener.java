package uk.ac.imperial.cisbio.adam;

public interface FilesystemListener {
	void create(Item item);
	void update(Item item);
	void delete(Item item);
}