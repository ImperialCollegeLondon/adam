package models;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import javax.persistence.Transient;

import play.Logger;
import play.Play;
import play.data.validation.Email;
import play.data.validation.Required;
import play.db.jpa.GenericModel;
import uk.ac.imperial.cisbio.adam.ADAM;
import util.BCrypt;
import util.Properties;

@Entity
public class User extends GenericModel {

	@Id
	@Required
	public String id;
	@Required
	public String password;
	@Transient
	public String _password;
	@Required
	public boolean admin;

	@Email
	public String email;
	@Required
	public String cn;

	@ManyToMany(mappedBy="users")
	public Set<UserGroup> groups = new HashSet<UserGroup>();

	@PostPersist
	public void postPersist() throws Exception {
		addUser(id, _password);
		_password = null;
	}

	@PostRemove
	public void postRemove() throws Exception {
		deleteUser(id);
	}

	@PostUpdate
	public void postUpdate() throws Exception {
		if (_password != null) {
			updatePassword(id, _password);
			_password = null;
		}
	}

	public void setPassword(String password) {
		if (!password.startsWith("$")) {
			_password = password;
			this.password = BCrypt.hashpw(password, BCrypt.gensalt());
		}
	}

	@Override
	public String toString() {
		return id;
	}

	private static void addUser(String username, String password) throws InterruptedException, IOException {
		if (Runtime.getRuntime().exec(String.format("useradd -b %s -m -k %s -s /bin/false %s", Properties.getRoot(), new File(Play.applicationPath, "skel"), username)).waitFor() == 0) {
			Process process = Runtime.getRuntime().exec(String.format("smbpasswd -s -a %s", username));
			PrintWriter writer = new PrintWriter(process.getOutputStream());
			writer.println(password);
			writer.println(password);
			writer.close();
			if (process.waitFor() == 0) {
				Logger.info("Added system user %s", username);
				return;
			}
		}
		Logger.error("Failed to add system user %s", username);
	}

	private static void deleteUser(String username) throws InterruptedException, IOException {
		if (Runtime.getRuntime().exec(String.format("pdbedit -x %s", username)).waitFor() == 0) {
			if (Runtime.getRuntime().exec(String.format("userdel %s", username)).waitFor() == 0) {
				if (Runtime.getRuntime().exec(String.format("rm -fr %s", ADAM.uu.home(username).getPath())).waitFor() == 0) {
					Logger.info("Deleted system user %s", username);
					return;
				}
			}
		}
		Logger.error("Failed to delete system user %s", username);
	}

	private static void updatePassword(String username, String password) throws IOException, InterruptedException {
		Process process = Runtime.getRuntime().exec(String.format("smbpasswd -s %s", username));
		PrintWriter writer = new PrintWriter(process.getOutputStream());
		writer.println(password);
		writer.println(password);
		writer.close();
		if (process.waitFor() == 0) {
			Logger.info("Updated system user %s", username);
			return;
		}
		Logger.error("Failed to update system user %s", username);
	}
}
