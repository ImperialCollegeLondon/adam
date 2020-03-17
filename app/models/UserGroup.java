package models;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;

import play.db.jpa.GenericModel;

@Entity
public class UserGroup extends GenericModel {

	@Id
	public String id;
	@ManyToMany
	public Set<User> users = new HashSet<User>();

	@Override
	public String toString() {
		return id;
	}
}
