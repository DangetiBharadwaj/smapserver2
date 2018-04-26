package surveyKPI;

/*
This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

 */

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.MessagingManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.MetaItem;
import org.smap.sdal.model.SurveyLinkDetails;
import org.smap.server.utilities.GetXForm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

/*
 * Return meta information about a survey
 *  For each table in the survey
 *  	Table Name
 *  	Form Name
 *  	Number of rows
 *  	If the table has a geometry column
 *      If it does, the bounding box of the survey
 *  For the whole survey
 *      The survey id
 *  	For each date column
 *  		The question id
 *  		The question name
 */

@Path("/survey/{sId}")
public class Survey extends Application {

	Authorise a = null;

	private static Logger log =
			Logger.getLogger(Survey.class.getName());

	LogManager lm = new LogManager();		// Application log

	public Survey() {

		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.VIEW_DATA);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);

	}

	@Path("/download")
	@GET
	public Response getSurveyDownload(@Context HttpServletRequest request,
			@QueryParam("type") String type,
			@QueryParam("language") String language,
			@PathParam("sId") int sId) { 

		ResponseBuilder builder = Response.ok();
		Response response = null;

		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey-getSurveyDownload");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation

		if(type == null) {
			type = "xml";
		}

		PreparedStatement pstmt = null;
		try {
			
			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(connectionSD, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
						
			String sourceName = null;
			String display_name = null;
			String fileBasePath = null;		// File path excluding extensions
			String folderPath = null;
			String filename = null;
			String sourceExt = null;
			int projectId = 0;

			String sql = null;
			ResultSet resultSet = null;

			/*
			 * Get the survey name (file name)
			 */
			sql = "SELECT s.display_name, s.p_id " +
					"FROM survey s " + 
					"where s.s_id = ?;";

			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, sId);
			log.info("Get survey details: " + pstmt.toString());
			resultSet = pstmt.executeQuery();

			if (resultSet.next()) {				
				display_name = resultSet.getString(1);
				projectId = resultSet.getInt(2);

				String basePath = GeneralUtilityMethods.getBasePath(request);
				String target_name = GeneralUtilityMethods.convertDisplayNameToFileName(display_name);

				fileBasePath = basePath + "/templates/" + projectId + "/" + target_name; 
				folderPath = basePath + "/templates/" + projectId;

				String ext;		
				if(type.equals("codebook")) {
					ext = "_gen.pdf";		// Codebooks are written as PDF files 
					sourceExt = "_gen.xml";		// input name is xml for a codebook file
				} else if(type.equals("xls")) {
					ext = ".xls";
					sourceExt = ".xls";
				} else if(type.equals("xml")) {	
					ext = "_gen.xml";		// Generate xml
					sourceExt = "_gen.xml";
				} else {
					ext = "." + type;
					sourceExt = "." + type;
				}
				sourceName = fileBasePath + sourceExt;
				
				String sourceNameXls = null;
				String sourceNameXlsX = null;
				if(type.equals("xls")) {
					sourceNameXls = fileBasePath + ".xls";
					sourceNameXlsX = fileBasePath + ".xlsx";
				}

				log.info("Source name: " + sourceName + " type: " + type);
				/*
				 * The XML file for a code book or an XML download needs to be generated so that it contains the latest changes
				 */
				if(type.equals("codebook") || type.equals("xml")) {

					try {
						SurveyTemplate template = new SurveyTemplate(localisation);
						template.readDatabase(sId, false);
						GetXForm xForm = new GetXForm(localisation, request.getRemoteUser());

						boolean useNodesets = !type.equals("codebook");		// For codebooks do not create nodesets in the XML
						String xmlForm = xForm.get(template, false, useNodesets, false, request.getRemoteUser());

						// 1. Create the project folder if it does not exist
						File folder = new File(folderPath);
						FileUtils.forceMkdir(folder);

						File f = new File(sourceName);

						// 2. Re-Create the file
						if(f.exists()) {
							f.delete();
						} 
						f.createNewFile();
						FileWriter fw = new FileWriter(f.getAbsoluteFile());
						BufferedWriter bw = new BufferedWriter(fw);
						bw.write(xmlForm);
						bw.close();

						log.info("Written xml file to: " + f.getAbsoluteFile());
					} catch (Exception e) {
						log.log(Level.SEVERE, "", e);
					}


				}

				// Check for the existence of the source file
				File outputFile = null;
				if(type.equals("xls")) {
					outputFile = new File(sourceNameXlsX);
					if(!outputFile.exists()) {
						outputFile = new File(sourceNameXls);
					}
				} else {
					String filepath = fileBasePath + ext;
					outputFile = new File(filepath);
				}

				filename = target_name + ext;
				try {  		
					int code = 0;
					if(type.equals("codebook")) {
						Process proc = Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", "/smap_bin/gettemplate.sh " + sourceName +
								" " + language +
						" >> /var/log/tomcat7/survey.log 2>&1"});
						code = proc.waitFor();
						log.info("Process exitValue: " + code);
					}

					builder = Response.ok(outputFile);
					if(type.equals("codebook")) {
						builder.header("Content-type","application/pdf; charset=UTF-8");
					} else if(type.equals("xls")) {
						builder.header("Content-type","application/vnd.ms-excel; charset=UTF-8");
					} else if(type.equals("xml")) {
						builder.header("Content-type","text/xml; charset=UTF-8");
					}
					builder.header("Content-Disposition", "attachment;Filename=" + filename);
					response = builder.build();

				} catch (Exception e) {
					log.log(Level.SEVERE, "", e);
					response = Response.serverError().entity("<h1>Error retrieving " + type + " file</h1><p>" + e.getMessage() + "</p>").build();
				}
			} else {
				response = Response.serverError().entity("Invalid survey name: " + sourceName).build();

			}


		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
			response = Response.serverError().entity("No data available").build();
		} finally {

			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}

			SDDataSource.closeConnection("surveyKPI-Survey-getSurveyDownload", connectionSD);

		}

		return response;
	}

	/*
	 * Get a public link to a webform for this survey
	 */
	@Path("/link/")
	@GET
	@Produces("application/text")
	public Response getLink(@Context HttpServletRequest request,
			@PathParam("sId") int sId) { 

		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-Survey-getLink");
		a.isAuthorised(sd, request.getRemoteUser());
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		
		String sql = "update survey set public_link = ? where s_id = ?";
		PreparedStatement pstmt = null;
		try {
			
			int oId = GeneralUtilityMethods.getOrganisationId(sd, null, sId);
			int pId = GeneralUtilityMethods.getProjectId(sd, sId);
			String sIdent = GeneralUtilityMethods.getSurveyIdent(sd, sId);
			String tempUserId = GeneralUtilityMethods.createTempUser(
					sd,
					oId,
					null, 
					"", 
					pId,
					null);
			String link = request.getScheme() + "://" + request.getServerName() + "/webForm/id/" + tempUserId + 
					"/" + sIdent;
			
			// Store the link with the survey
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, link);
			pstmt.setInt(2, sId);
			pstmt.executeUpdate();
			
			response = Response.ok(link).build();
			
		} catch (Exception e) {
			log.log(Level.SEVERE,e.getMessage(), e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			SDDataSource.closeConnection("surveyKPI-Survey-getLink", sd);
		}
		
		return response;
	}
	
	/*
	 * Delete a public link to a webform for this survey
	 */
	@Path("/deletelink/{ident}")
	@DELETE
	@Produces("application/text")
	public Response deleteLink(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("ident") String ident) { 

		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-Survey-deleteLink");
		a.isAuthorised(sd, request.getRemoteUser());
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		

		String sql = "update survey set public_link = null where s_id = ?";
		PreparedStatement pstmt = null;;
		try {
			
			/*
			 * Delete the temporary user
			 */
			int oId = GeneralUtilityMethods.getOrganisationId(sd, null, sId);
			GeneralUtilityMethods.deleteTempUser(sd, oId, ident);
			
			// Delete the link from the survey
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.executeUpdate();
			
			response = Response.ok("").build();
			
		} catch (Exception e) {
			log.log(Level.SEVERE,e.getMessage(), e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			SDDataSource.closeConnection("surveyKPI-Survey-deleteLink", sd);
		}
		
		return response;
	}
	
	/*
	 * Get the Survey Meta data
	 */
	private class DateInfo {	// Temporary storage of the array of date questions
		int qId;
		String name;
		String columnName;
		int fId;
		Date first;
		Date last;
	}

	@Path("/getMeta")
	@GET
	@Produces("application/json")
	public Response getSurveyMeta(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@QueryParam("extended") boolean extended) { 

		Response response = null;
		String topTableName = null;

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-Survey-getSurveyMeta");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation

		JSONObject jo = new JSONObject();

		Connection connectionRel = null; 
		PreparedStatement pstmt = null;
		PreparedStatement pstmt2 = null;
		PreparedStatement pstmt3 = null;
		PreparedStatement pstmtTables = null;
		PreparedStatement pstmtGeom = null;

		try {
			String sqlTables = "select "
					+ "f.table_name, f.name, f_id, f.parentform "
					+ "from form f "
					+ "where f.s_id = ? "
					+ "and f.reference = 'false' " 
					+ "order by f.table_name";		
			pstmtTables = sd.prepareStatement(sqlTables);	

			String sqlGeom = "select q.q_id "
					+ "from form f, question q "
					+ "where f.f_id = q.f_id "
					+ "and (q.qtype='geopoint' "
					+ "or q.qtype='geopolygon' "
					+ "or q.qtype='geolinestring' "
					+ "or q.qtype='geoshape' "
					+ "or q.qtype='geotrace') "
					+ "and f.f_id = ? "
					+ "and f.s_id = ?";
			pstmtGeom = sd.prepareStatement(sqlTables);	

			// Get the preloads
			ArrayList<MetaItem> preloads = GeneralUtilityMethods.getPreloads(sd, sId);
			
			// Add the sId to the response so that it is available in the survey meta object
			jo.put("sId", sId);

			connectionRel = ResultsDataSource.getConnection("surveyKPI-Survey-getSurveyMeta");

			String sql = null;
			ResultSet resultSet = null;
			ResultSet resultSetTable = null;
			ResultSet resultSetBounds = null;
			ArrayList<DateInfo> dateInfoList = new ArrayList<DateInfo> ();
			HashMap<String, SurveyLinkDetails> completeLinks = new HashMap<String, SurveyLinkDetails> ();

			JSONArray ja = new JSONArray();
			JSONArray jLinks = new JSONArray();
			JSONArray jSurveys = new JSONArray();

			float [] bbox = new float[4]; 
			bbox[0] = 180;
			bbox[1] = 90;
			bbox[2] = -180;
			bbox[3] = -90;

			/*
			 * Start with the passed in survey
			 * If extended mode is set then we will need to retrieve forms for other surveys
			 */
			HashMap<Integer, Integer> completedSurveys = new HashMap <Integer, Integer> ();
			Stack<Integer> surveys = new Stack<Integer>();
			surveys.push(new Integer(sId));
			completedSurveys.put(new Integer(sId), new Integer(sId));

			/*
			 * Get Forms and row counts the next survey
			 */
			while (!surveys.empty()) {

				int currentSurveyId = surveys.pop().intValue();

				// If extended get the surveys that link to this survey
				if(extended) {

					// Get the surveys that link to this one
					ArrayList<SurveyLinkDetails> sList = GeneralUtilityMethods.getLinkingSurveys(sd, currentSurveyId);
					if(sList.size() > 0) {
						for(SurveyLinkDetails link : sList) {
							completeLinks.put(link.getId(), link);
							int s = link.fromSurveyId;
							if(completedSurveys.get(s) != null) {
								log.info("Have already got meta data for survey " + s);
							} else {
								completedSurveys.put(new Integer(s), new Integer(s));
								surveys.push(s);
							}
						}	
					}

					// Get the surveys that this survey links to
					sList = GeneralUtilityMethods.getLinkedSurveys(sd, currentSurveyId);
					if(sList.size() > 0) {
						for(SurveyLinkDetails link : sList) {
							completeLinks.put(link.getId(), link);
							int s = link.toSurveyId;
							if(completedSurveys.get(s) != null) {
								log.info("Have already got meta data for survey " + s);
							} else {
								completedSurveys.put(new Integer(s), new Integer(s));
								surveys.push(s);
							}
						}	
					}	
				}

				pstmtTables.setInt(1, currentSurveyId);
				log.info("Get tables :" + pstmtTables.toString());
				resultSet = pstmtTables.executeQuery();

				while (resultSet.next()) {							

					String tableName = resultSet.getString(1);
					String formName = resultSet.getString(2);
					int fId = resultSet.getInt(3);
					String p_id = resultSet.getString(4);
					int rowCount = 0;
					boolean has_geom = false;
					String geom_id = null;
					String bounds = null;

					try {
						sql = "select count(*) from " + tableName;
						try {if (pstmt2 != null) {pstmt2.close();}} catch (SQLException e) {}
						pstmt2 = connectionRel.prepareStatement(sql);
						resultSetTable = pstmt2.executeQuery();
						if(resultSetTable.next()) {
							rowCount = resultSetTable.getInt(1);
						}

					} catch (Exception e) {
						// If the table has not been created yet set row count to the default=0
					}

					// Get any geometry questions for this table
					pstmtGeom = sd.prepareStatement(sqlGeom);
					pstmtGeom.setInt(1, fId);
					pstmtGeom.setInt(2, sId);
					resultSetTable = pstmtGeom.executeQuery();
					if(resultSetTable.next()) {
						geom_id = resultSetTable.getString(1);
						has_geom = true;
					}

					// Get the table bounding box
					try {
						if(has_geom) {
							sql = "select ST_Extent(the_geom) as table_extent "
									+ "from " + tableName;
							try {if (pstmt3 != null) {pstmt3.close();}} catch (SQLException e) {}
							pstmt3 = connectionRel.prepareStatement(sql);
							resultSetBounds = pstmt3.executeQuery();
							if(resultSetBounds.next()) {
								bounds = resultSetBounds.getString(1);
								if(bounds != null) {
									addToSurveyBounds(bbox, bounds);
								}
							}
						}
					} catch (Exception e) {
						// If the table has not been created don't set the table bounds
					}

					/*
					 * Get first last record of any date fields
					 */
					for(int i = 0; i < dateInfoList.size(); i++) {
						DateInfo di = dateInfoList.get(i);
						if(fId == di.fId) {
							try {
								String name = di.columnName;
								sql = "select min(" + name + "), max(" + name + ") FROM " + tableName + ";";

								try {if (pstmt2 != null) {pstmt2.close();}} catch (SQLException e) {}
								pstmt2 = connectionRel.prepareStatement(sql);
								resultSetTable = pstmt2.executeQuery();
								if(resultSetTable.next()) {
									di.first = resultSetTable.getDate(1);
									di.last = resultSetTable.getDate(2);
								}

							} catch (Exception e) {
								// Ignore errors, for example table not created
							}
						}
					}


					JSONObject jp = new JSONObject();
					jp.put("name", tableName);
					jp.put("form", formName);
					jp.put("rows", rowCount);
					jp.put("geom", has_geom);
					jp.put("s_id", currentSurveyId);
					jp.put("f_id", fId);
					jp.put("p_id", p_id);
					if(p_id == null || p_id.equals("0")) {
						topTableName = tableName;
						jo.put("top_table", tableName);
					}
					jp.put("geom_id", geom_id);
					ja.put(jp);

				} 	
			}
			jo.put("forms", ja);

			if(extended) {
				for(String linkId : completeLinks.keySet()) {
					SurveyLinkDetails link = completeLinks.get(linkId);
					JSONObject jl = new JSONObject();
					jl.put("fromSurveyId", link.fromSurveyId);
					jl.put("fromFormId", link.fromFormId);
					jl.put("fromQuestionId", link.fromQuestionId);
					jl.put("toQuestionId", link.toQuestionId);

					jl.put("toSurveyId", link.toSurveyId);

					jLinks.put(jl);
				}
				jo.put("links", jLinks);

				for(Integer surveyId : completedSurveys.keySet()) {
					JSONObject js = new JSONObject();
					String sName = GeneralUtilityMethods.getSurveyName(sd, surveyId);
					js.put("sId", surveyId);
					js.put("name", sName);
					jSurveys.put(js);
				}
				jo.put("surveys", jSurveys);
			}

			/*
			 * Add the date information
			 */
			/*
			 * Get Date columns available in this survey
			 * The maximum and minimum value for these dates will be added when 
			 * the results data for each table is checked
			 */
			sql = "select q.q_id, q.qname, f.f_id, q.column_name "
					+ "from form f, question q "
					+ "where f.f_id = q.f_id "
					+ "and (q.qtype='date' "
					+ "or q.qtype='dateTime') "
					+ "and f.s_id = ?"; 	


			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			resultSet = pstmt.executeQuery();

			while (resultSet.next()) {	
				DateInfo di = new DateInfo();
				di.qId = resultSet.getInt(1);
				di.name = resultSet.getString(2);
				di.fId = resultSet.getInt(3);
				di.columnName = resultSet.getString(4);
				dateInfoList.add(di);
			}	

			// Add upload time
			if(GeneralUtilityMethods.columnType(connectionRel, topTableName, "_upload_time") != null) {
				DateInfo di = new DateInfo();

				di.columnName = "_upload_time";
				di.name = "Upload Time";
				di.qId = SurveyManager.UPLOAD_TIME_ID;
				dateInfoList.add(di);
			}
			
			// Add preloads
			int metaId = -1000;		// Backward compatability to when meta items did not have an id
			for(MetaItem mi : preloads) {
				if(mi.type.equals("dateTime") || mi.type.equals("date")) {
					DateInfo di = new DateInfo();

					int id = (mi.id <= -1000) ? mi.id : metaId--;
					di.columnName = mi.columnName;
					if(mi.display_name != null) {
						di.name = mi.display_name;
					} else {
						di.name = mi.name;
					}
					
					di.qId = id;
					dateInfoList.add(di);
				}
			}

			ja = new JSONArray();
			for (int i = 0; i < dateInfoList.size(); i++) {
				DateInfo di = dateInfoList.get(i);
				JSONObject jp = new JSONObject();
				jp.put("id", di.qId);
				jp.put("name", di.name);
				jp.put("first", di.first);
				jp.put("last", di.last);
				ja.put(jp);
			}			
			jo.put("dates", ja);

			/*
			 * Add the bounding box
			 *  Don't set the bbox if there is no location data, that is the left is greater than right
			 *  If there was only one point then add a buffer around that point
			 */		
			if(bbox[0] <= bbox[2] && bbox[1] <= bbox[3]) {
				if(bbox[0] == bbox[2]) {	// Zero width
					bbox[0] -= 0.05;		// Size in degrees 
					bbox[2] += 0.05;
				}
				if(bbox[1] == bbox[3]) {	// Zero height
					bbox[1] -= 0.05;		// Size in degrees 
					bbox[3] += 0.05;
				}
				JSONArray bb = new JSONArray();
				for(int i = 0; i < bbox.length; i++) {
					bb.put(bbox[i]);
				}
				jo.put("bbox", bb);
			} 

			/*
			 * Get other survey details
			 */
			sql = "select "
					+ "s.display_name, s.deleted, s.p_id, s.ident, s.model, s.task_file "
					+ "from survey s "
					+ "where s.s_id = ?";

			if(pstmt != null) try {pstmt.close();}catch(Exception e) {}
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			resultSet = pstmt.executeQuery();

			if (resultSet.next()) {				
				jo.put("name", resultSet.getString(1));
				jo.put("deleted", resultSet.getBoolean(2));
				jo.put("project", resultSet.getInt(3));
				jo.put("survey_ident", resultSet.getString(4));
				jo.put("model", resultSet.getString(5));
				jo.put("task_file", resultSet.getBoolean(6));
			}

			String resp = jo.toString();
			response = Response.ok(resp).build();


		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
			response = Response.serverError().entity("No data available").build();
		} catch (JSONException e) {
			log.log(Level.SEVERE,"", e);
		} finally {

			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmt2 != null) {pstmt2.close();}} catch (SQLException e) {}
			try {if (pstmt3 != null) {pstmt3.close();}} catch (SQLException e) {}
			try {if (pstmtTables != null) {pstmtTables.close();}} catch (SQLException e) {}
			try {if (pstmtGeom != null) {pstmtGeom.close();}} catch (SQLException e) {}

			SDDataSource.closeConnection("surveyKPI-Survey-getSurveyMeta", sd);
			ResultsDataSource.closeConnection("surveyKPI-Survey-getSurveyMeta", connectionRel);
		}

		return response;
	}

	/*
	 * Prevent any more submissions of the survey
	 */
	@Path("/block")
	@POST
	@Consumes("application/json")
	public Response block(
			@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@QueryParam("set") boolean set) { 

		Response response = null;

		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);	// Validate that the user can access this survey
		// End Authorisation

		PreparedStatement pstmt = null;
		try {

			/*
			 * Get Forms and row counts in this survey
			 */
			String sql = "update survey set blocked = ? where s_id = ?;";		

			log.info(sql + " : " + set + " : " + sId);
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setBoolean(1, set);
			pstmt.setInt(2, sId);
			int count = pstmt.executeUpdate();

			if(count == 0) {
				log.info("Error: Failed to update blocked status");
			} else {
				lm.writeLog(connectionSD, sId, request.getRemoteUser(), "block", set ? " : block survey : " : " : unblock survey : ");
				log.info("userevent: " + request.getRemoteUser() + (set ? " : block survey : " : " : unblock survey : ") + sId);
			}

			// Record the message so that devices can be notified
			MessagingManager mm = new MessagingManager();
			mm.surveyChange(connectionSD, sId, 0);

			response = Response.ok().build();



		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
			response = Response.serverError().entity("No data available").build();
		} finally {

			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}

			SDDataSource.closeConnection("surveyKPI-Survey", connectionSD);
		}

		return response;
	}

	/*
	 * Save the survey things@ model
	 */
	@Path("/model")
	@POST
	@Consumes("application/json")
	public Response save_model(
			@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@FormParam("model") String model
			) { 

		Response response = null;

		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);	// Validate that the user can access this survey
		// End Authorisation

		PreparedStatement pstmt = null;
		if(model == null) {
			response = Response.serverError().entity("Empty model").build();
		} else {
			try {

				/*
				 * Get Forms and row counts in this survey
				 */
				String sql = "update survey set model = ? where s_id = ?;";		


				pstmt = connectionSD.prepareStatement(sql);	
				pstmt.setString(1, model);
				pstmt.setInt(2, sId);
				int count = pstmt.executeUpdate();

				if(count == 0) {
					response = Response.serverError().entity("Failed to update model").build();
				} else {
					response = Response.ok().build();
				}



			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to update model", e);
				response = Response.serverError().entity("Failed to update model").build();
			} finally {

				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}

				SDDataSource.closeConnection("surveyKPI-Survey", connectionSD);

			}
		}

		return response;
	}

	/*
	 * Remove media attachments
	 */
	@Path("/remove_media")
	@POST
	@Consumes("application/json")
	public Response remove_media(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@FormParam("qId") int qId,
			@FormParam("oId") int oId,
			@FormParam("text_id") String text_id) { 

		Response response = null;

		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);	// Validate that the user can access this survey
		// End Authorisation

		PreparedStatement pstmt = null;
		try {
			String sql = null;
			if(text_id != null) {
				// Survey level media
				sql = "delete FROM translation t " +
						" where t.s_id = ? " +
						" and t.text_id = ? "; 
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setInt(1, sId);
				pstmt.setString(2, text_id);
			} else 	if(oId == -1) {
				// Question level media
				sql = "delete FROM translation t " +
						" where t.s_id = ? " +
						" and (t.type = 'image' or t.type = 'video' or t.type = 'audio') " +
						" and t.text_id in (select q.qtext_id from question q " + 
						" where q.q_id = ?); "; 
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setInt(1, sId);
				pstmt.setInt(2, qId);

			} else {
				// Option level media
				sql = "delete FROM translation t " +
						" where t.s_id = ? " +
						" and (t.type = 'image' or t.type = 'video' or t.type = 'audio') " +
						" and t.text_id in (select o.label_id from option o " + 
						" where o.o_id = ?); "; 
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setInt(1, sId);
				pstmt.setInt(2, oId);
			}


			int count = pstmt.executeUpdate();

			if(count == 0) {
				log.info("Error: Failed to remove any media");
			} else {
				log.info("Info: Media removed");
			}

			// Record the message so that devices can be notified
			MessagingManager mm = new MessagingManager();
			mm.surveyChange(connectionSD, sId, 0);

			response = Response.ok().build();

		} catch (SQLException e) {
			log.log(Level.SEVERE,"", e);
			response = Response.serverError().entity("Error").build();
		} finally {

			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}

			SDDataSource.closeConnection("surveyKPI-Survey", connectionSD);

		}

		return response;
	}

	/*
	 * Deletes a survey template
	 *  @param tables if set to yes, data tables will also be deleted
	 *  @param hard if set to true then data and tables will be physically deleted otherwise they will only
	 *    be marked as deleted in the meta data tables but will remain in the database
	 *  @param delData if set to yes then the results tables will be deleted even if they have data
	 */
	// JSON
	@DELETE
	public String deleteSurvey(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@QueryParam("tables") String tables,
			@QueryParam("hard") boolean hard,
			@QueryParam("undelete") boolean undelete,
			@QueryParam("delData") boolean delData) { 

		log.info("Deleting template:" + sId);

		Connection cResults = null;
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-Survey");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		boolean surveyMustBeDeleted = undelete || hard;
		a.isValidSurvey(sd, request.getRemoteUser(), sId, surveyMustBeDeleted, superUser);  // Note if hard delete is set to true the survey should have already been soft deleted
		// End Authorisation

		if(sId != 0) {
			
			try {
				// Get the users locale
				Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
				ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
				
				SurveyManager mgr = new SurveyManager(localisation);
				
				if(undelete) {				 
					mgr.restore(sd, sId, request.getRemoteUser());	// Restore the survey
				} else {
					cResults = ResultsDataSource.getConnection("surveyKPI-Survey");
					String basePath = GeneralUtilityMethods.getBasePath(request);
					
					mgr.delete(sd, 
							cResults, 
							sId, 
							hard, 
							delData, 
							request.getRemoteUser(), 
							basePath,
							tables,
							0);
				}
				
				// Record the message so that devices can be notified
				MessagingManager mm = new MessagingManager();
				mm.surveyChange(sd, sId, 0);

			} catch (SQLException e) {
				log.log(Level.SEVERE, "SQL Error", e);
				return "Error: Failed to delete";

			} catch (Exception e) {
				log.log(Level.SEVERE, "Error", e);
				return "Error: Failed to delete";

			} finally {

				SDDataSource.closeConnection("surveyKPI-Survey", sd);
				ResultsDataSource.closeConnection("surveyKPI-Survey", cResults);
			}
		}

		return null; 
	}

	private void addToSurveyBounds(float[] bbox, String bounds) {
		int idx = bounds.indexOf('(');
		if(idx > 0) {
			String b2 = bounds.substring(idx + 1, bounds.length() - 1);
			String [] coords = b2.split(",");
			if(coords.length > 1) {
				String [] c1 = coords[0].split(" ");
				String [] c2 = coords[1].split(" ");

				float [] newBounds = new float[4];
				newBounds[0] = Float.parseFloat(c1[0]);
				newBounds[1] = Float.parseFloat(c1[1]);
				newBounds[2] = Float.parseFloat(c2[0]);
				newBounds[3] = Float.parseFloat(c2[1]);

				if(newBounds[0] < bbox[0]) {
					bbox[0] = newBounds[0];
				}
				if(newBounds[1] < bbox[1]) {
					bbox[1] = newBounds[1];
				}
				if(newBounds[2] > bbox[2]) {
					bbox[2] = newBounds[2];
				}
				if(newBounds[3] > bbox[3]) {
					bbox[3] = newBounds[3];
				}
			}
		}
	}
}

