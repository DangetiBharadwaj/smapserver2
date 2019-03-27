package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.HashMap;

import com.google.gson.JsonElement;

public class Instance {
	public HashMap<String, String> values = new HashMap<>();
	public JsonElement geometry; 
	public HashMap<String, ArrayList<Instance>> repeats;	
}
