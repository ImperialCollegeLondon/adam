package uk.ac.imperial.cisbio.adam;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import models.User;
import models.UserGroup;

public class UsersUtil {
	private File root;

	public UsersUtil(File root) {
		this.root = root;
	}

	public File home(String user) {
		return new File(root, user);
	}

	public Set<String> getUsers() {
		Set<String> users = new HashSet<String>();
		for (User user : User.<User>findAll()) {
			users.add(user.id);
		}
		return users;
	}

	public Set<String> getGroups(String username) {
		Set<String> groups = new HashSet<String>();
		for (UserGroup group : User.<User>findById(username).groups) {
			groups.add(group.id);
		}
		return groups;
	}

	public Set<String> getUsers(String group) {
		Set<String> users = new HashSet<String>();
		for (User user : UserGroup.<UserGroup>findById(group).users) {
			users.add(user.id);
		}
		return users;
	}

	public Map<String, Object> getUserData(final User user) throws IOException {
		//TODO do this serialization automatically
		return new HashMap<String, Object>() {{
			put("id", user.id);
			put("admin", user.admin);
			put("email", user.email);
			put("cn", user.cn);
			put("groups", ADAM.uu.getGroups(user.id));
			put("diskUsage", Util.humanReadableByteCount(Util.size(home(user.id)) * 1024));
			put("itemCount", ADAM.du.itemCount(user.id));
		}};
	}
}
