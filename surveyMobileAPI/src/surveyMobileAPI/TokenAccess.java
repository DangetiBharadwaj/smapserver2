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

package surveyMobileAPI;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.AssignmentsManager;
import org.smap.sdal.managers.SharedResourceManager;

import surveyMobileAPI.managers.FormListManager;
import surveyMobileAPI.managers.ManifestManager;
import surveyMobileAPI.managers.UploadManager;
import surveyMobileAPI.managers.WebFormManager;

/*
 * Entry point for fieldTask requests using a token
 */

@Path("/token")

public class TokenAccess extends Application {
	
	private static Logger log = Logger.getLogger(TokenAccess.class.getName());
	
	// Respond with XML 
	@GET
	@Path("/formList")
	@Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML})   
	public Response getFormListToken(@Context HttpServletRequest request) throws IOException, ApplicationException {
		
		FormListManager flm = new FormListManager();
		return flm.getFormList(request);
	}
	
	@GET
	@Path("/formXML")
	@Produces(MediaType.TEXT_XML)  
	public String getFormToken(@Context HttpServletRequest request,
			@QueryParam("key") String templateName,
			@QueryParam("deviceID") String deviceId) {
		
		FormListManager flm = new FormListManager();
		return flm.getForm(request, templateName, deviceId);
	}
 
	@GET
	@Path("/refresh")
	@Produces("application/json")
	public Response getTasksCredentials(@Context HttpServletRequest request,
			@QueryParam("noprojects") boolean noProjects, 
			@QueryParam("orgs") boolean getOrgs,
			@QueryParam("linked") boolean getLinkedRefDefns, 
			@QueryParam("manifests") boolean getManifests)
			throws SQLException, ApplicationException {
		
		AssignmentsManager am = new AssignmentsManager();
		return am.getTasks(request, request.getRemoteUser(), noProjects, getOrgs, 
				getLinkedRefDefns, getManifests, true);
	}
	
	@GET
	@Path("/webForm/instance/{ident}/{updateid}")
	@Produces("application/json")
	public Response getInstanceDataEndPoint(@Context HttpServletRequest request,
			@PathParam("ident") String formIdent,
			@PathParam("updateid") String updateid // Unique id of instance data
			) throws SQLException, ApplicationException {
		
		Response resp = null;
		String requester = "surveyMobileAPI-Webform";
		Connection sd = SDDataSource.getConnection(requester);

		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);

			String user = request.getRemoteUser();
			if(user == null) {
				user = GeneralUtilityMethods.getUserFromRequestKey(sd, request, "app");
			}
			if(user == null) {
				throw new AuthorisationException("Unknown User");
			}
			log.info("Requesting instance as: " + user);
			WebFormManager wfm = new WebFormManager(localisation, "UTC");
			resp = wfm.getInstanceData(sd, request, formIdent, updateid, 0, user, true);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SDDataSource.closeConnection(requester, sd);
		}

		return resp;
	}

	/*
	 * Head request to return the actual URL to submit data to
	 * This is required by the Java Rosa protocol
	 */
	@HEAD
	@Path("/submission")
	@Produces(MediaType.TEXT_XML)
	public void getHead(@Context HttpServletRequest request,  @Context HttpServletResponse resp) {

		UploadManager ulm = new UploadManager();
		ulm.setHeaderResponse(request, resp);

	}
	
	/*
	 * Update
	 */
	@POST
	@Path("/submission")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response postUpdateInstance(
			@Context HttpServletRequest request,
			@QueryParam("deviceID") String deviceId
	       ) throws IOException {
		
		UploadManager ulm = new UploadManager();
		return ulm.submission(request, null, null, deviceId);
	}
	
	/*
	 * Manifest
	 */
	@GET
	@Path("/xformsManifest")
	@Produces(MediaType.TEXT_XML)
	public String getManifest(
			@QueryParam("key") String key, 
			@Context HttpServletRequest request, 
			@Context HttpServletResponse resp
	       ) throws IOException {
		
		ManifestManager mm = new ManifestManager();
		return mm.getManifest(request, resp, key);
	}
	
	/*
	 * Survey Resource
	 */
	@GET
	@Path("/resource/{filename}/survey/{sId}")
	@Produces("application/x-download")
	public Response getSurveyFile(
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("filename") String filename,
			@PathParam("sId") int sId,
			@QueryParam("thumbs") boolean thumbs,
			@QueryParam("linked") boolean linked
	       ) throws IOException {
		
		log.info("Get Resource: " + filename + " for survey: " + sId);
		SharedResourceManager srm = new SharedResourceManager(null, null);
		return srm.getSurveyFile(request, response,filename, sId, thumbs, linked);	
	}
	
	/*
	 * Organisation Resource
	 */
	@GET
	@Path("/resource/{filename}/organisation")
	@Produces("application/x-download")
	public Response getOrganisationFile(
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("filename") String filename,
			@QueryParam("settings") boolean settings,
			@QueryParam("thumbs") boolean thumbs,
			@QueryParam("org") int requestedOrgId
	       ) throws IOException {
		
		log.info("------- " + filename);
		SharedResourceManager srm = new SharedResourceManager(null, null);
		return srm.getOrganisationFile(request, response, null, requestedOrgId, filename, settings, false, thumbs);
	}
}

