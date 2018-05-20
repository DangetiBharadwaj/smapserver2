package org.smap.sdal.managers;

import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletRequest;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.Notification;
import org.smap.sdal.model.NotifyDetails;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.SubmissionMessage;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.TaskMessage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/*****************************************************************************

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

 ******************************************************************************/

/*
 * Manage the table that stores details on the forwarding of data onto other systems
 */
public class NotificationManager {
	
	private static Logger log =
			 Logger.getLogger(NotificationManager.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	private ResourceBundle localisation;
	
	public NotificationManager(ResourceBundle l) {
		localisation = l;
	}
	
	/*
	 * Get all Enabled notifications
	 * Used by Subscriber to do forwarding
	 */
	public ArrayList<Notification> getEnabledNotifications(
			Connection sd, 
			String target) throws SQLException {
		
		ArrayList<Notification> forwards = new ArrayList<Notification>();	// Results of request
		
		ResultSet resultSet = null;
		String sql = "select f.id, "
				+ "f.s_id, "
				+ "f.enabled, " +
				" f.remote_s_id, "
				+ "f.remote_s_name, "
				+ "f.remote_host, "
				+ "f.remote_user, "
				+ "f.target, "
				+ "s.display_name, "
				+ "f.notify_details, "
				+ "f.filter, "
				+ "f.remote_password "
				+ "from forward f, survey s "
				+ "where f.s_id = s.s_id "
				+ "and f.enabled = 'true' ";
		PreparedStatement pstmt = null;
		
		try {
			if(target.equals("forward")) {
				sql += " and f.target = 'forward' and f.remote_host is not null";
			} else if(target.equals("message")) {
				sql += " and (f.target = 'email' or f.target = 'sms')";
			} else if(target.equals("document")) {
				sql += " and f.target = 'document'";
			}	
			
			pstmt = sd.prepareStatement(sql);	
	
			resultSet = pstmt.executeQuery();
			addToList(resultSet, forwards, true);
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		
		return forwards;
		
	}
	
	/*
	 * Add a record to the notification table
	 */
	public void addNotification(Connection sd, PreparedStatement pstmt, String user, 
			Notification n) throws Exception {
					
			String sql = "insert into forward(" +
					" s_id, enabled, " +
					" remote_s_id, remote_s_name, remote_host, remote_user, remote_password, notify_details, target, filter) " +
					" values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?); ";
	
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String notifyDetails = gson.toJson(n.notifyDetails);
			
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setInt(1, n.s_id);
			pstmt.setBoolean(2, n.enabled);
			pstmt.setString(3, n.remote_s_ident);
			pstmt.setString(4, n.remote_s_name);
			pstmt.setString(5, n.remote_host);
			pstmt.setString(6, n.remote_user);
			pstmt.setString(7, n.remote_password);
			pstmt.setString(8, notifyDetails);
			pstmt.setString(9, n.target);
			pstmt.setString(10, n.filter);
			pstmt.executeUpdate();
	}
	
	/*
	 * Update a record to the forwarding table
	 */
	public void updateNotification(Connection sd, PreparedStatement pstmt, String user, 
			Notification n) throws Exception {
			
		String sql = null;
		if(n.update_password) {
			sql = "update forward set " +
					" s_id = ?, " +
					" enabled = ?, " +
					" remote_s_id = ?, " +
					" remote_s_name = ?, " +
					" remote_host = ?, " +
					" remote_user = ?, " +
					" notify_details = ?, " +
					" target = ?, " +
					" filter = ?, " +
					" remote_password = ? " +
					" where id = ?; ";
		} else {
			sql = "update forward set " +
					" s_id = ?, " +
					" enabled = ?, " +
					" remote_s_id = ?, " +
					" remote_s_name = ?, " +
					" remote_host = ?, " +
					" remote_user = ?, " +
					" notify_details = ?, " +
					" target = ?, " +
					" filter = ? " +
					" where id = ?; ";
		}
			

		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		String notifyDetails = gson.toJson(n.notifyDetails);
		
		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);	 			
		pstmt.setInt(1, n.s_id);
		pstmt.setBoolean(2, n.enabled);
		pstmt.setString(3, n.remote_s_ident);
		pstmt.setString(4, n.remote_s_name);
		pstmt.setString(5, n.remote_host);
		pstmt.setString(6, n.remote_user);
		pstmt.setString(7, notifyDetails);
		pstmt.setString(8, n.target);
		pstmt.setString(9, n.filter);
		if(n.update_password) {
			pstmt.setString(10, n.remote_password);
			pstmt.setInt(11, n.id);
		} else {
			pstmt.setInt(10, n.id);
		}

		log.info("SQL: " + pstmt.toString());
		pstmt.executeUpdate();
	}
	
	/*
	 * Check that the server is not forwarding to the same survey on the same server
	 */
	public boolean isFeedbackLoop(Connection con, String server, Notification n) throws SQLException {
		boolean loop = false;
		
		String remote_host = null;;
		
		String [] hostParts = n.remote_host.split("//");
		remote_host = hostParts[1];
		
		log.info("Checking for forwarding feedback loop. Current server is: " + server + " : " + remote_host);
		
		// Get the ident of the local survey to compare with the remote ident
		PreparedStatement pstmt;
		String sql = "select ident from survey where s_id = ?;";
		pstmt = con.prepareStatement(sql);
		pstmt.setInt(1, n.s_id);
		ResultSet rs = pstmt.executeQuery(); 
		if(rs.next()) {
			String local_ident = rs.getString(1);
			log.info("Local ident is: " + local_ident + " : " + n.remote_s_ident);
			if(local_ident != null && local_ident.equals(n.remote_s_ident) && remote_host.equals(server)) {
				loop = true;
			}
		}
		pstmt.close();
		
		return loop;
	}
	
	/*
	 * Get all Notifications that are accessible by the requesting user and in a specific project
	 */
	public ArrayList<Notification> getProjectNotifications(Connection sd, PreparedStatement pstmt,
			String user,
			int projectId) throws SQLException {
		
		ArrayList<Notification> notifications = new ArrayList<Notification>();	// Results of request
		
		ResultSet resultSet = null;
		String sql = "select f.id, f.s_id, f.enabled, " +
				" f.remote_s_id, f.remote_s_name, f.remote_host, f.remote_user," +
				" f.target, s.display_name, f.notify_details, f.filter" +
				" from forward f, survey s, users u, user_project up, project p " +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and s.p_id = up.p_id" +
				" and s.s_id = f.s_id" +
				" and u.ident = ? " +
				" and s.p_id = ? " + 
				" and s.deleted = 'false'";
		
		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);	 			

		pstmt.setString(1, user);
		pstmt.setInt(2, projectId);
		log.info("Project Forwards: " + pstmt.toString());
		resultSet = pstmt.executeQuery();

		addToList(resultSet, notifications, false);

		return notifications;
		
	}
	
	/*
	 * Get all Notifications that are accessible by the requesting user and in a specific project
	 */
	public ArrayList<String> getNotificationTypes(Connection sd) throws SQLException {
		
		ArrayList<String> types = new ArrayList<>();
		PreparedStatement pstmt = null;
		
		String sql = "select s.sms_url, s.document_sync "
				+ "from server s";
		
		types.add("email");
		types.add("forward");
		
		try {
		
			pstmt = sd.prepareStatement(sql);	 			
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				String smsUrl = rs.getString("sms_url");
				if(smsUrl != null) {
					types.add("sms");
				}
				if(rs.getBoolean("document_sync")) {
					types.add("document");
				}
			}
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return types;
		
	}
	
	/*
	 * Delete the notification
	 */
	public void deleteNotification(Connection sd, PreparedStatement pstmt,
			String user,
			int id) throws SQLException {
		
		String sql = "delete from forward where id = ?; ";
		
		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);	 			

		pstmt.setInt(1, id);
		log.info("Delete: " + pstmt.toString());
		pstmt.executeUpdate();
		
	}

	private void addToList(ResultSet resultSet, ArrayList<Notification> notifications, boolean getPassword) throws SQLException {
		
		while (resultSet.next()) {								

			String remote_s_id = resultSet.getString(4);
			Notification n = new Notification();
			n.id = resultSet.getInt(1);
			n.s_id = resultSet.getInt(2);
			n.enabled = resultSet.getBoolean(3);
			n.remote_s_ident = remote_s_id;
			n.remote_s_name = resultSet.getString(5);
			n.remote_host = resultSet.getString(6);
			n.remote_user = resultSet.getString(7);
			n.target = resultSet.getString(8);
			n.s_name = resultSet.getString(9);
			String notifyDetailsString = resultSet.getString(10);
			n.notifyDetails = new Gson().fromJson(notifyDetailsString, NotifyDetails.class);
			n.filter = resultSet.getString(11);
			if(getPassword) {
				n.remote_password = resultSet.getString(12);
			}
			
			notifications.add(n);
			
		} 
	}
	
	/*
	 * Apply any notification for the passed in submission
	 */
	public void notifyForSubmission(
			Connection sd, 
			Connection cResults,
			int ue_id,
			String remoteUser,
			String scheme,
			String serverName,
			String basePath,
			String serverRoot,
			int sId,
			String ident,
			String instanceId,
			int pId,
			boolean excludeEmpty) throws Exception {
		/*
		 * 1. Get notifications that may apply to the passed in upload event.
		 * 		Notifications can be re-applied so the the notifications flag in upload event is ignored
		 * 2. Apply any additional filtering
		 * 3. Invoke each notification
		 *    3a) Create document
		 *    3b) Send document to target
		 *    3c) Update notification log
		 * 4. Update upload event table to show that notifications have been applied
		 */
		
		ResultSet rsNotifications = null;		
		PreparedStatement pstmtGetNotifications = null;
		PreparedStatement pstmtUpdateUploadEvent = null;
		
		try {
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			MessagingManager mm = new MessagingManager();
			int oId = GeneralUtilityMethods.getOrganisationIdForSurvey(sd, sId);
			
			log.info("notifyForSubmission:: " + ue_id);
			
			String sqlGetNotifications = "select n.target, n.notify_details, n.filter "
					+ "from forward n "
					+ "where n.s_id = ? " 
					+ "and n.target != 'forward' "
					+ "and n.target != 'document' "
					+ "and n.enabled = 'true'";
			pstmtGetNotifications = sd.prepareStatement(sqlGetNotifications);
			
			String sqlUpdateUploadEvent = "update upload_event set notifications_applied = 'true' where ue_id = ?; ";
			pstmtUpdateUploadEvent = sd.prepareStatement(sqlUpdateUploadEvent);
	
			// Localisation
			Organisation organisation = UtilityMethodsEmail.getOrganisationDefaults(sd, null, remoteUser);
			Locale locale = new Locale(organisation.locale);
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			pstmtGetNotifications.setInt(1, sId);
			log.info("Get notifications:: " + pstmtGetNotifications.toString());
			rsNotifications = pstmtGetNotifications.executeQuery();
			while(rsNotifications.next()) {
				
				String target = rsNotifications.getString(1);
				String notifyDetailsString = rsNotifications.getString(2);
				String filter = rsNotifications.getString(3);
				NotifyDetails nd = new Gson().fromJson(notifyDetailsString, NotifyDetails.class);
				
				/*
				 * Get survey details
				 */
				SurveyManager sm = new SurveyManager(localisation);
				Survey survey = sm.getById(sd, cResults, remoteUser, sId, true, basePath, 
						instanceId, true, false, true, false, true, "real", 
						false, false, false, "geojson");
				
				/*
				 * Test the filter
				 */
				boolean proceed = true;
				if(filter != null && filter.trim().length() > 0) {
					try {
						proceed = GeneralUtilityMethods.testFilter(cResults, localisation, survey, filter, instanceId);
					} catch(Exception e) {
						lm.writeLog(sd, sId, "subscriber", "notification", 
								localisation.getString("filter_error")
								.replace("%s1", filter)
								.replaceAll("%s2", e.getMessage()));
					}
				}
				
				if(!proceed) {
					lm.writeLog(sd, sId, "subscriber", "notification", 
							localisation.getString("filter_reject")
							.replace("%s1", survey.displayName)
							.replace("%s2", filter)
							.replaceAll("%s3", instanceId));
				} else {
		
					SubmissionMessage subMgr = new SubmissionMessage(
							sId,
							ident,
							pId,
							instanceId, 
							nd.from,
							nd.subject, 
							nd.content,
							nd.attach,
							nd.emailQuestion,
							nd.emailMeta,
							nd.emails,
							target,
							remoteUser,
							scheme,
							serverName,
							basePath,
							serverRoot);
					mm.createMessage(sd, oId, "submission", "", gson.toJson(subMgr));
					
				}
			}
				
			/*
			 * Update upload event to record application of notifications
			 */
			pstmtUpdateUploadEvent.setInt(1, ue_id);
			pstmtUpdateUploadEvent.executeUpdate();
		} finally {
			try {if (pstmtGetNotifications != null) {pstmtGetNotifications.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateUploadEvent != null) {pstmtUpdateUploadEvent.close();}} catch (SQLException e) {}
		}
			
	}
	
	/*
	 * Process a notification
	 */
	public void processNotification(Connection sd, 
			Connection cResults, 
			Organisation organisation,
			SubmissionMessage msg,
			int messageId,
			String user) throws Exception {
		
		String docURL = null;
		String filePath = null;
		String filename = "instance";
		String logContent = null;
		
		boolean writeToMonitor = true;
		
		HashMap<String, String> sentEndPoints = new HashMap<> ();
		boolean generateBlank =  (msg.instanceId == null) ? true : false;	// If false only show selected options
		
		PreparedStatement pstmtGetSMSUrl = null;
		
		PreparedStatement pstmtNotificationLog = null;
		String sqlNotificationLog = "insert into notification_log " +
				"(o_id, p_id, s_id, notify_details, status, status_details, event_time, message_id) " +
				"values( ?, ?,?, ?, ?, ?, now(), ?); ";
		
		// Time Zone
		int utcOffset = 0;	
		LocalDateTime dt = LocalDateTime.now();
		if(organisation.timeZone != null) {
			try {
				ZoneId zone = ZoneId.of(organisation.timeZone);
			    ZonedDateTime zdt = dt.atZone(zone);
			    ZoneOffset offset = zdt.getOffset();
			    utcOffset = offset.getTotalSeconds() / 60;
			} catch (Exception e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		
		SurveyManager sm = new SurveyManager(localisation);
		Survey survey = sm.getById(sd, cResults, msg.user, msg.sId, true, msg.basePath, 
				msg.instanceId, true, generateBlank, true, false, true, "real", 
				false, false, true, "geojson");
		
		PDFSurveyManager pm = new PDFSurveyManager(localisation, sd, survey, user);
		
		try {
			
			pstmtNotificationLog = sd.prepareStatement(sqlNotificationLog);
			
			/*
			 * Add details from the survey to the subject and email content
			 */
			msg.subject = sm.fillStringTemplate(survey, msg.subject);
			msg.content = sm.fillStringTemplate(survey, msg.content);
			TextManager tm = new TextManager(localisation);
			ArrayList<String> text = new ArrayList<> ();
			text.add(msg.subject);
			text.add(msg.content);
			tm.createTextOutput(sd,
						cResults,
						text,
						msg.basePath, 
						msg.user,
						survey,
						utcOffset,
						"none",
						organisation.id);
			msg.subject = text.get(0);
			msg.content = text.get(1);
			
			if(msg.attach != null && !msg.attach.equals("none")) {
				
				if(msg.attach.startsWith("pdf")) {
					docURL = null;
					
					// Create temporary PDF and get file name
					filePath = msg.basePath + "/temp/" + String.valueOf(UUID.randomUUID()) + ".pdf";
					FileOutputStream outputStream = null;
					try {
						outputStream = new FileOutputStream(filePath); 
					} catch (Exception e) {
						log.log(Level.SEVERE, "Error creating temporary PDF file", e);
					}
										
					// Split orientation from nd.attach
					boolean landscape = false;
					if(msg.attach != null && msg.attach.startsWith("pdf")) {
						landscape = msg.attach.equals("pdf_landscape");
						msg.attach = "pdf";
					}
	
					filename = pm.createPdf(
							outputStream,
							msg.basePath, 
							msg.serverRoot,
							msg.user,
							"none", 
							generateBlank,
							null,
							landscape,
							null,
							utcOffset);
					
					logContent = filePath;
					
				} else {
					docURL = "/webForm/" + msg.ident +
							"?datakey=instanceid&datakeyvalue=" + msg.instanceId;
					logContent = docURL;
				}
			} 
				
			/*
			 * Send document to target
			 */
			String status = "success";				// Notification log
			String notify_details = null;			// Notification log
			String error_details = null;				// Notification log
			if(msg.target.equals("email")) {
				EmailServer emailServer = UtilityMethodsEmail.getSmtpHost(sd, null, msg.user);
				if(emailServer.smtpHost != null && emailServer.smtpHost.trim().length() > 0) {
					ArrayList<String> emailList = null;
					log.info("Email question: " + msg.emailQuestion);
					if(msg.emailQuestion > 0) {
						emailList = GeneralUtilityMethods.getResponseForEmailQuestion(sd, cResults, msg.sId, msg.emailQuestion, msg.instanceId);
					} else {
						emailList = new ArrayList<String> ();
					}
					
					// Add any meta email addresses to the per question emails
					String metaEmail = GeneralUtilityMethods.getResponseMetaValue(sd, cResults, msg.sId, msg.emailMeta, msg.instanceId);
					if(metaEmail != null) {
						emailList.add(metaEmail);
					}
					
					// Add the static emails to the per question emails
					for(String email : msg.emails) {
						if(email.length() > 0) {
							log.info("Adding static email: " + email); 
							emailList.add(email);
						}
					}
							
					// Convert emails into a comma separated string
					String emails = "";
					for(String email : emailList) {	
						if(sentEndPoints.get(email) == null) {
							if(isValidEmail(email)) {
								if(emails.length() > 0) {
									emails += ",";
								}
								emails += email;
							} else {
								log.info("Email Notifications: Discarding invalid email: " + email);
							}
							sentEndPoints.put(email, email);
						} else {
							log.info("Duplicate email: " + email);
						}
					}
						
					if(emails.trim().length() > 0) {
						log.info("userevent: " + msg.user + " sending email of '" + logContent + "' to " + emails);
						
						// Set the subject
						String subject = "";
						if(msg.subject != null && msg.subject.trim().length() > 0) {
							subject = msg.subject;
						} else {
							if(msg.server != null && msg.server.contains("smap")) {
								subject = "Smap ";
							}
							subject += localisation.getString("c_notify");
						}
						
						String from = "smap";
						if(msg.from != null && msg.from.trim().length() > 0) {
							from = msg.from;
						}
						String content = null;
						if(msg.content != null && msg.content.trim().length() > 0) {
							content = msg.content;
						} else {
							content = organisation.default_email_content;
						}
						
						notify_details = "Sending email to: " + emails + " containing link " + logContent;
						
						log.info("+++ emailing to: " + emails + " docUrl: " + logContent + 
								" from: " + from + 
								" subject: " + subject +
								" smtp_host: " + emailServer.smtpHost +
								" email_domain: " + emailServer.emailDomain);
						try {
							EmailManager em = new EmailManager();
							
							em.sendEmail(
									emails, 
									null, 
									"notify", 
									subject, 
									content,
									from,		
									null, 
									null, 
									null, 
									docURL, 
									filePath,
									filename,
									organisation.getAdminEmail(), 
									emailServer,
									msg.scheme,
									msg.server,
									localisation);
						} catch(Exception e) {
							status = "error";
							error_details = e.getMessage();
						}
					} else {
						log.log(Level.INFO, "Info: List of email recipients is empty");
						lm.writeLog(sd, msg.sId, "subscriber", "email", localisation.getString("email_nr"));
						writeToMonitor = false;
					}
				} else {
					status = "error";
					error_details = "smtp_host not set";
					log.log(Level.SEVERE, "Error: Attempt to do email notification but email server not set");
				}
				
			} else if(msg.target.equals("sms")) {   // SMS URL notification - SMS message is posted to an arbitrary URL 
				
				// Get the URL to use in sending the SMS
				String sql = "select s.sms_url "
						+ "from server s";
				
				String sms_url = null;
				pstmtGetSMSUrl = sd.prepareStatement(sql);
				ResultSet rs = pstmtGetSMSUrl.executeQuery();
				if(rs.next()) {
					sms_url = rs.getString("sms_url");	
				}
				
				if(sms_url != null) {
					ArrayList<String> smsList = null;
					ArrayList<String> responseList = new ArrayList<> ();
					log.info("SMS question: " + msg.emailQuestion);
					if(msg.emailQuestion > 0) {
						smsList = GeneralUtilityMethods.getResponseForEmailQuestion(sd, cResults, msg.sId, msg.emailQuestion, msg.instanceId);
					} else {
						smsList = new ArrayList<String> ();
					}
					
					// Add the static sms numbers to the per question sms numbers
					for(String sms : msg.emails) {
						if(sms.length() > 0) {
							log.info("Adding static sms: " + sms); 
							smsList.add(sms);
						}
					}
					
					if(smsList.size() > 0) {
						SMSManager smsUrlMgr = new SMSManager();
						for(String sms : smsList) {
							
							if(sentEndPoints.get(sms) == null) {
								log.info("userevent: " + msg.user + " sending sms of '" + msg.content + "' to " + sms);
								responseList.add(smsUrlMgr.sendSMSUrl(sms_url, sms, msg.content));
								sentEndPoints.put(sms, sms);
							} else {
								log.info("Duplicate phone number: " + sms);
							}
							
						} 
					} else {
						log.info("No phone numbers to send to");
						writeToMonitor = false;
					}
					
					notify_details = "Sending sms " + smsList.toString() 
							+ ((logContent == null || logContent.equals("null")) ? "" :" containing link " + logContent)
							+ " with response " + responseList.toString();
					
				} else {
					status = "error";
					error_details = "SMS URL not set";
					log.log(Level.SEVERE, "Error: Attempt to do SMS notification but SMS URL not set");
				}
	
				
			} else {
				status = "error";
				error_details = "Invalid target: " + msg.target;
				log.log(Level.SEVERE, "Error: Invalid target" + msg.target);
			}
			
			// Write log message
			if(writeToMonitor) {
				pstmtNotificationLog.setInt(1, organisation.id);
				pstmtNotificationLog.setInt(2, msg.pId);
				pstmtNotificationLog.setInt(3, msg.sId);
				pstmtNotificationLog.setString(4, notify_details);
				pstmtNotificationLog.setString(5, status);
				pstmtNotificationLog.setString(6, error_details);
				pstmtNotificationLog.setInt(7, messageId);
				
				pstmtNotificationLog.executeUpdate();
			}
		} finally {
			try {if (pstmtNotificationLog != null) {pstmtNotificationLog.close();}} catch (SQLException e) {}
			try {if (pstmtGetSMSUrl != null) {pstmtGetSMSUrl.close();}} catch (SQLException e) {}
			
		}
	}
	
	/*
	 * Validate an email
	 */
	public boolean isValidEmail(String email) {
		boolean isValid = true;
		try {
		      InternetAddress emailAddr = new InternetAddress(email);
		      emailAddr.validate();
		   } catch (AddressException ex) {
		      isValid = false;
		   }
		return isValid;
	}
}


