package surveyKPI;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.BackgroundReportsManager;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.model.AR;
import org.smap.sdal.model.BackgroundReport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import utilities.XLSXAdminReportsManager;

/*
 * Export a survey in XLSX format
 * This export follows the approach of CSV exports where a single sub form can be selected
 * Get access to a form for each user
 */
@Path("/adminreport/usage")
public class AdminReportUsage extends Application {

	Authorise a = null;
	Authorise aOrg = null;

	private static Logger log =
			Logger.getLogger(AdminReportUsage.class.getName());

	LogManager lm = new LogManager();		// Application log
	boolean includeTemporaryUsers;
	
	public AdminReportUsage() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
		
		ArrayList<String> authorisationsOrg = new ArrayList<String> ();	
		authorisationsOrg.add(Authorise.ORG);
		aOrg = new Authorise(authorisationsOrg, null);
	}
	
	/*
	 * Get usage for a specific month
	 */
	@POST
	public Response exportSurveyXlsx (@Context HttpServletRequest request, 
			@FormParam("report") String sReport,
			@Context HttpServletResponse response) {

		Response responseVal = null;
		
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		
		System.out.println(sReport);
		// Authorisation - Access
		String connectionString = "surveyKPI - AdminReports - Usage";
		Connection sd = SDDataSource.getConnection(connectionString);		
		// End Authorisation		
		
		try {
		
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			BackgroundReport br = gson.fromJson(sReport, BackgroundReport.class);
			
			// Get params
			int oId = GeneralUtilityMethods.getKeyValueInt(BackgroundReportsManager.PARAM_O_ID, br.params);
			int month = GeneralUtilityMethods.getKeyValueInt(BackgroundReportsManager.PARAM_MONTH, br.params);	
			int year = GeneralUtilityMethods.getKeyValueInt(BackgroundReportsManager.PARAM_YEAR, br.params);	
			boolean bySurvey = GeneralUtilityMethods.getKeyValueBoolean(BackgroundReportsManager.PARAM_BY_SURVEY, br.params);	
			boolean byProject = GeneralUtilityMethods.getKeyValueBoolean(BackgroundReportsManager.PARAM_BY_PROJECT, br.params);
			boolean byDevice = GeneralUtilityMethods.getKeyValueBoolean(BackgroundReportsManager.PARAM_BY_DEVICE, br.params);
			includeTemporaryUsers = GeneralUtilityMethods.getKeyValueBoolean(BackgroundReportsManager.PARAM_INC_TEMP, br.params);
			
			// start validation			
			if(oId > 0) {
				aOrg.isAuthorised(sd, request.getRemoteUser());
			} else {
				a.isAuthorised(sd, request.getRemoteUser());
			}
			String orgName = "";
			if(oId <= 0) {
				oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			} else {
				orgName = GeneralUtilityMethods.getOrganisationName(sd, oId);
			}
						
			if(month < 1) {
				throw new ApplicationException(localisation.getString("ar_month_gt_0"));
			}
			// End Validation

			
			String filename = localisation.getString("ar_report_name") + "_" + (oId > 0 ? orgName + "_" : "") + year + "_" + month;
			
			ArrayList<AR> report = null;
			if(bySurvey) {
				report = getAdminReportSurvey(sd, oId, month, year);
			} else if(byProject) {
				report = getAdminReportProject(sd, oId, month, year);
			} else if(byDevice) {
				report = getAdminReportDevice(sd, oId, month, year);
			} else {
				report = getAdminReport(sd, oId, month, year);
			}
			
			ArrayList<String> header = new ArrayList<String> ();
			header.add(localisation.getString("ar_ident"));
			header.add(localisation.getString("ar_user_name"));
			header.add(localisation.getString("ar_user_created"));
			if(byProject || bySurvey) {
				header.add(localisation.getString("ar_project_id"));
				header.add(localisation.getString("ar_project"));
			}
			if(bySurvey) {
				header.add(localisation.getString("ar_survey_id"));
				header.add(localisation.getString("ar_survey"));
			}
			if(byDevice) {
				header.add(localisation.getString("a_device"));
			}
			header.add(localisation.getString("ar_usage_month"));
			header.add(localisation.getString("ar_usage_at"));
			
			// Get temp file
			String basePath = GeneralUtilityMethods.getBasePath(request);
			GeneralUtilityMethods.createDirectory(basePath + "/reports");
			String filepath = basePath + "/reports/" + filename;	// Use a random sequence to keep survey name unique
			File tempFile = new File(filepath);
			
			XLSXAdminReportsManager rm = new XLSXAdminReportsManager(localisation);
			rm.getNewReport(sd, tempFile, header, report, byProject, bySurvey, byDevice, year, month,
					GeneralUtilityMethods.getOrganisationName(sd, oId));
			
		} catch(Exception e) {
			log.log(Level.SEVERE, "Error", e);
			response.setHeader("Content-type",  "text/html; charset=UTF-8");
			responseVal = Response.status(Status.OK).entity("Error: " + e.getMessage()).build();
		} finally {
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return responseVal;

	}

	private ArrayList<AR> getAdminReport(Connection sd, int oId, int month, int year) throws SQLException {
		ArrayList<AR> rows = new ArrayList<AR> ();
		StringBuilder sql = new StringBuilder("select users.id as id,users.ident as ident, users.name as name, users.created as created, "
				+ "(select count (*) from upload_event ue, subscriber_event se "
					+ "where ue.ue_id = se.ue_id "
					+ "and se.status = 'success' "
					+ "and se.subscriber = 'results_db' "
					+ "and upload_time >=  ? "		// current month
					+ "and upload_time < ? "		// next month
					//+ "and extract(month from upload_time) = ? "
					//+ "and extract(year from upload_time) = ? "
					+ "and ue.user_name = users.ident) as month,"
				+ "(select count (*) from upload_event ue, subscriber_event se "
					+ "where ue.ue_id = se.ue_id and se.status = 'success' "
					+ "and se.subscriber = 'results_db' "
					+ "and ue.user_name = users.ident) as all_time "
				+ "from users "
				+ "where users.o_id = ? ");
		
		if(!includeTemporaryUsers) {
			sql.append("and not users.temporary ");
		}
		sql.append("order by users.ident");
		PreparedStatement pstmt = null;
		
		try {
			Timestamp t1 = GeneralUtilityMethods.getTimestampFromParts(year, month, 1);
			Timestamp t2 = GeneralUtilityMethods.getTimestampNextMonth(t1);
			
			pstmt = sd.prepareStatement(sql.toString());
			//pstmt.setInt(1, month);
			//pstmt.setInt(2, year);
			pstmt.setTimestamp(1, t1);
			pstmt.setTimestamp(2, t2);
			pstmt.setInt(3, oId);
			log.info("Admin report: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			while(rs.next()) {
				AR ar = new AR();
				ar.userIdent = rs.getString("ident");
				ar.userName = rs.getString("name");
				ar.created = rs.getDate("created");
				ar.usageInPeriod = rs.getInt("month");
				ar.allTimeUsage = rs.getInt("all_time");
				rows.add(ar);
			}
			
		} finally {
			if(pstmt != null) {try{pstmt.close();}catch(Exception e) {}}
		}
		return rows;
	}

	private ArrayList<AR> getAdminReportProject(Connection sd, int oId, int month, int year) throws SQLException {
		
		ArrayList<AR> rows = new ArrayList<AR> ();
		HashMap<String, AR> monthMap = new HashMap<> ();
		
		StringBuilder sqlMonth = new StringBuilder("select count(*) as month, "
				+ "ue.user_name as ident, "
				+ "users.name as name, "
				+ "ue.p_id as p_id, "
				+ "project.name as project_name, "
				+ "users.created as created "
				+ "from users, subscriber_event se, upload_event ue "
				+ "left outer join project on project.id = ue.p_id "
				+ "where ue.ue_id = se.ue_id "
				+ "and se.status = 'success' "
				+ "and se.subscriber = 'results_db' "
				+ "and upload_time >=  ? "		// current month
				+ "and upload_time < ? "		// next month
				//+ "and extract(month from upload_time) = ? "
				//+ "and extract(year from upload_time) = ? "
				+ "and users.o_id = ? "
				+ "and users.ident = ue.user_name ");
		
		if(!includeTemporaryUsers) {
			sqlMonth.append("and not users.temporary ");
		}
		sqlMonth.append("group by ue.user_name, users.name, ue.p_id, project.name, users.created "
				+ "order by ue.user_name, ue.p_id;");
		PreparedStatement pstmtMonth = null;
		
		StringBuilder sqlAllTime = new StringBuilder("select count(*) as year, "
				+ "ue.user_name as ident, "
				+ "users.name as name, "
				+ "ue.p_id as p_id, "
				+ "project.name as project_name, "
				+ "users.created as created "
				+ "from users, subscriber_event se, upload_event ue "
				+ "left outer join project on project.id = ue.p_id "
				+ "where ue.ue_id = se.ue_id "
				+ "and se.status = 'success' "
				+ "and se.subscriber = 'results_db' "
				+ "and users.o_id = ? "
				+ "and users.ident = ue.user_name ");
		if(!includeTemporaryUsers) {
			sqlAllTime.append("and not users.temporary ");
		}
		sqlAllTime.append("group by ue.user_name, users.name, ue.p_id, project.name, users.created "
				+ "order by ue.user_name, ue.p_id");
		PreparedStatement pstmtAllTime = null;
		
		try {
			Timestamp t1 = GeneralUtilityMethods.getTimestampFromParts(year, month, 1);
			Timestamp t2 = GeneralUtilityMethods.getTimestampNextMonth(t1);
			
			pstmtMonth = sd.prepareStatement(sqlMonth.toString());
			//pstmtMonth.setInt(1, month);
			//pstmtMonth.setInt(2, year);
			pstmtMonth.setTimestamp(1, t1);
			pstmtMonth.setTimestamp(2, t2);
			pstmtMonth.setInt(3, oId);
			log.info("Monthly Admin report by project: " + pstmtMonth.toString());
			ResultSet rs = pstmtMonth.executeQuery();
			
			while(rs.next()) {
				AR ar = new AR();
				ar.userIdent = rs.getString("ident");
				ar.userName = rs.getString("name");
				ar.created = rs.getDate("created");
				ar.p_id = rs.getInt("p_id");
				ar.project = rs.getString("project_name");
				ar.usageInPeriod = rs.getInt("month");
				rows.add(ar);
				monthMap.put(ar.userIdent + "::::" + ar.p_id, ar);	// Save map so we can add all time values
			}
			
			// Get the all time
			pstmtAllTime = sd.prepareStatement(sqlAllTime.toString());
			pstmtAllTime.setInt(1, oId);
			log.info("All Time Admin report by project: " + pstmtAllTime.toString());
			rs = pstmtAllTime.executeQuery();
			while(rs.next()) {
						
				String user = rs.getString("ident");
				int p_id = rs.getInt("p_id");
				int allTime = rs.getInt("year");
				AR ar = monthMap.get(user + "::::" + p_id);
				if(ar == null) {
					ar = new AR();
					ar.userIdent = rs.getString("ident");
					ar.userName = rs.getString("name");
					ar.created = rs.getDate("created");
					ar.p_id = rs.getInt("p_id");
					ar.project = rs.getString("project_name");
					ar.usageInPeriod = 0;
					rows.add(ar);
				}
				ar.allTimeUsage = allTime;
				
			}
			
		} finally {
			if(pstmtMonth != null) {try{pstmtMonth.close();}catch(Exception e) {}}
			if(pstmtAllTime != null) {try{pstmtAllTime.close();}catch(Exception e) {}}
		}
		return rows;
	}

	private ArrayList<AR> getAdminReportSurvey(Connection sd, int oId, int month, int year) throws SQLException {
		
		ArrayList<AR> rows = new ArrayList<AR> ();
		HashMap<String, AR> monthMap = new HashMap<> ();
		
		StringBuilder sqlMonth = new StringBuilder("select count(*) as month, "
				+ "ue.user_name as ident, "
				+ "users.name as name, "
				+ "ue.p_id as p_id, "
				+ "ue.s_id as s_id, "
				+ "project.name as project_name, "
				+ "survey.display_name as survey_name, "
				+ "users.created as created "
				+ "from users, subscriber_event se, upload_event ue "
				+ "left outer join project on project.id = ue.p_id "
				+ "left outer join survey on survey.s_id = ue.s_id "
				+ "where ue.ue_id = se.ue_id "
				+ "and se.status = 'success' "
				+ "and se.subscriber = 'results_db' "
				+ "and upload_time >=  ? "		// current month
				+ "and upload_time < ? "		// next month
				//+ "and extract(month from upload_time) = ? "
				//+ "and extract(year from upload_time) = ? "
				+ "and users.o_id = ? "
				+ "and users.ident = ue.user_name ");
		if(!includeTemporaryUsers) {
			sqlMonth.append("and not users.temporary ");
		}
		sqlMonth.append("group by ue.user_name, users.name, ue.p_id, project.name, ue.s_id, survey.display_name, users.created "
				+ "order by ue.user_name, ue.p_id, ue.s_id");		
		PreparedStatement pstmtMonth = null;
		
		StringBuilder sqlAllTime = new StringBuilder("select count(*) as year, "
				+ "ue.user_name as ident, "
				+ "users.name as name, "
				+ "ue.p_id as p_id, "
				+ "ue.s_id as s_id, "
				+ "project.name as project_name, "
				+ "survey.display_name as survey_name, "
				+ "users.created as created "
				+ "from users, subscriber_event se, upload_event ue "
				+ "left outer join project on project.id = ue.p_id "
				+ "left outer join survey on survey.s_id = ue.s_id "
				+ "where ue.ue_id = se.ue_id "
				+ "and se.status = 'success' "
				+ "and se.subscriber = 'results_db' "
				+ "and users.o_id = ? "
				+ "and users.ident = ue.user_name ");
		if(!includeTemporaryUsers) {
			sqlAllTime.append("and not users.temporary ");
		}
		sqlAllTime.append("group by ue.user_name, users.name, ue.p_id, project.name, ue.s_id, survey.display_name, users.created "
				+ "order by ue.user_name, ue.p_id, ue.s_id");
		PreparedStatement pstmtAllTime = null;
		
		try {
			Timestamp t1 = GeneralUtilityMethods.getTimestampFromParts(year, month, 1);
			Timestamp t2 = GeneralUtilityMethods.getTimestampNextMonth(t1);
			
			pstmtMonth = sd.prepareStatement(sqlMonth.toString());
			//pstmtMonth.setInt(1, month);
			//pstmtMonth.setInt(2, year);
			pstmtMonth.setTimestamp(1, t1);
			pstmtMonth.setTimestamp(2, t2);
			pstmtMonth.setInt(3, oId);
			log.info("Monthly Admin report by survey: " + pstmtMonth.toString());
			ResultSet rs = pstmtMonth.executeQuery();
			
			while(rs.next()) {
				AR ar = new AR();
				ar.userIdent = rs.getString("ident");
				ar.userName = rs.getString("name");
				ar.created = rs.getDate("created");
				ar.p_id = rs.getInt("p_id");
				ar.project = rs.getString("project_name");
				ar.s_id = rs.getInt("s_id");
				ar.survey = rs.getString("survey_name");
				ar.usageInPeriod = rs.getInt("month");
				rows.add(ar);
				monthMap.put(ar.userIdent + "::::" + ar.p_id + "::::" + ar.s_id, ar);	// Save map so we can add all time values
			}
			
			// Get the all time
			pstmtAllTime = sd.prepareStatement(sqlAllTime.toString());
			pstmtAllTime.setInt(1, oId);
			log.info("All Time Admin report by project: " + pstmtAllTime.toString());
			rs = pstmtAllTime.executeQuery();
			while(rs.next()) {
						
				String user = rs.getString("ident");
				int p_id = rs.getInt("p_id");
				int s_id = rs.getInt("s_id");
				int allTime = rs.getInt("year");
				AR ar = monthMap.get(user + "::::" + p_id + "::::" + s_id);
				if(ar == null) {
					ar = new AR();
					ar.userIdent = rs.getString("ident");
					ar.userName = rs.getString("name");
					ar.created = rs.getDate("created");
					ar.p_id = rs.getInt("p_id");
					ar.project = rs.getString("project_name");
					ar.s_id = rs.getInt("s_id");
					ar.survey = rs.getString("survey_name");
					ar.usageInPeriod = 0;
					rows.add(ar);
				}
				ar.allTimeUsage = allTime;
				
			}
			
		} finally {
			if(pstmtMonth != null) {try{pstmtMonth.close();}catch(Exception e) {}}
			if(pstmtAllTime != null) {try{pstmtAllTime.close();}catch(Exception e) {}}
		}
		return rows;
	}
	
	private ArrayList<AR> getAdminReportDevice(Connection sd, int oId, int month, int year) throws SQLException {
		
		ArrayList<AR> rows = new ArrayList<AR> ();
		HashMap<String, AR> monthMap = new HashMap<> ();
		
		StringBuilder sqlMonth = new StringBuilder("select count(*) as month, "
				+ "ue.user_name as ident, "
				+ "users.name as name, "
				+ "ue.imei as imei, "
				+ "users.created as created "
				+ "from users, subscriber_event se, upload_event ue "
				+ "where ue.ue_id = se.ue_id "
				+ "and se.status = 'success' "
				+ "and se.subscriber = 'results_db' "
				//+ "and extract(month from upload_time) = ? "
				//+ "and extract(year from upload_time) = ? "
				+ "and upload_time >=  ? "		// current month
				+ "and upload_time < ? "		// next month
				+ "and users.o_id = ? "
				+ "and users.ident = ue.user_name ");
		if(!includeTemporaryUsers) {
			sqlMonth.append("and not users.temporary ");
		}
		sqlMonth.append("group by ue.user_name, users.name, ue.imei, users.created "
				+ "order by ue.user_name, ue.imei");		
		PreparedStatement pstmtMonth = null;
		
		StringBuilder sqlAllTime = new StringBuilder("select count(*) as year, "
				+ "ue.user_name as ident, "
				+ "users.name as name, "
				+ "ue.imei as imei, "
				+ "users.created as created "
				+ "from users, subscriber_event se, upload_event ue "
				+ "left outer join project on project.id = ue.p_id "
				+ "left outer join survey on survey.s_id = ue.s_id "
				+ "where ue.ue_id = se.ue_id "
				+ "and se.status = 'success' "
				+ "and se.subscriber = 'results_db' "
				+ "and users.o_id = ? "
				+ "and users.ident = ue.user_name ");
		if(!includeTemporaryUsers) {
			sqlAllTime.append("and not users.temporary ");
		}
		sqlAllTime.append("group by ue.user_name, users.name, ue.imei, users.created "
				+ "order by ue.user_name, ue.imei");	
		PreparedStatement pstmtAllTime = null;
		
		try {
			Timestamp t1 = GeneralUtilityMethods.getTimestampFromParts(year, month, 1);
			Timestamp t2 = GeneralUtilityMethods.getTimestampNextMonth(t1);
			
			pstmtMonth = sd.prepareStatement(sqlMonth.toString());
			//pstmtMonth.setInt(1, month);
			//pstmtMonth.setInt(2, year);
			pstmtMonth.setTimestamp(1, t1);
			pstmtMonth.setTimestamp(2, t2);
			pstmtMonth.setInt(3, oId);
			log.info("Monthly Admin report by device: " + pstmtMonth.toString());
			ResultSet rs = pstmtMonth.executeQuery();
			
			while(rs.next()) {
				AR ar = new AR();
				ar.userIdent = rs.getString("ident");
				ar.userName = rs.getString("name");
				ar.created = rs.getDate("created");
				ar.device = rs.getString("imei");
				ar.usageInPeriod = rs.getInt("month");
				rows.add(ar);
				monthMap.put(ar.userIdent + "::::" + ar.device, ar);	// Save map so we can add all time values
			}
			
			// Get the all time
			pstmtAllTime = sd.prepareStatement(sqlAllTime.toString());
			pstmtAllTime.setInt(1, oId);
			log.info("All Time Admin report by project: " + pstmtAllTime.toString());
			rs = pstmtAllTime.executeQuery();
			while(rs.next()) {
						
				String user = rs.getString("ident");
				String device = rs.getString("imei");
				int allTime = rs.getInt("year");
				AR ar = monthMap.get(user + "::::" + device);
				if(ar == null) {
					ar = new AR();
					ar.userIdent = rs.getString("ident");
					ar.userName = rs.getString("name");
					ar.created = rs.getDate("created");
					ar.device = rs.getString("imei");
					ar.usageInPeriod = 0;
					rows.add(ar);
				}
				ar.allTimeUsage = allTime;
				
			}
			
		} finally {
			if(pstmtMonth != null) {try{pstmtMonth.close();}catch(Exception e) {}}
			if(pstmtAllTime != null) {try{pstmtAllTime.close();}catch(Exception e) {}}
		}
		return rows;
	}

}
