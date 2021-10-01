package org.smap.sdal.model;

public class Assignment {
	public int assignment_id;
	public String assignment_status;
	public String task_comment;
	public int task_id;
	public long dbId;		// included to match fieldTask definition
	public String uuid;		// The identifier of the data created by this task
	public User user;
}
