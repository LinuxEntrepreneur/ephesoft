/********************************************************************************* 
* Ephesoft is a Intelligent Document Capture and Mailroom Automation program 
* developed by Ephesoft, Inc. Copyright (C) 2010-2012 Ephesoft Inc. 
* 
* This program is free software; you can redistribute it and/or modify it under 
* the terms of the GNU Affero General Public License version 3 as published by the 
* Free Software Foundation with the addition of the following permission added 
* to Section 15 as permitted in Section 7(a): FOR ANY PART OF THE COVERED WORK 
* IN WHICH THE COPYRIGHT IS OWNED BY EPHESOFT, EPHESOFT DISCLAIMS THE WARRANTY 
* OF NON INFRINGEMENT OF THIRD PARTY RIGHTS. 
* 
* This program is distributed in the hope that it will be useful, but WITHOUT 
* ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS 
* FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more 
* details. 
* 
* You should have received a copy of the GNU Affero General Public License along with 
* this program; if not, see http://www.gnu.org/licenses or write to the Free 
* Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 
* 02110-1301 USA. 
* 
* You can contact Ephesoft, Inc. headquarters at 111 Academy Way, 
* Irvine, CA 92617, USA. or at email address info@ephesoft.com. 
* 
* The interactive user interfaces in modified source and object code versions 
* of this program must display Appropriate Legal Notices, as required under 
* Section 5 of the GNU Affero General Public License version 3. 
* 
* In accordance with Section 7(b) of the GNU Affero General Public License version 3, 
* these Appropriate Legal Notices must retain the display of the "Ephesoft" logo. 
* If the display of the logo is not reasonably feasible for 
* technical reasons, the Appropriate Legal Notices must display the words 
* "Powered by Ephesoft". 
********************************************************************************/ 

package com.ephesoft.dcma.gwt.admin.bm.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.SerializationUtils;

import com.ephesoft.dcma.batch.service.BatchSchemaService;
import com.ephesoft.dcma.core.common.FileType;
import com.ephesoft.dcma.da.domain.BatchClass;
import com.ephesoft.dcma.da.service.BatchClassService;
import com.ephesoft.dcma.gwt.admin.bm.client.i18n.BatchClassManagementConstants;
import com.ephesoft.dcma.gwt.core.server.BatchClassUtil;
import com.ephesoft.dcma.gwt.core.server.DCMAHttpServlet;
import com.ephesoft.dcma.util.FileUtils;

/**
 * This is class for exporting batch class download Servlet.
 * 
 * @author Ephesoft
 * @version 1.0
 * @see com.ephesoft.dcma.gwt.core.server.DCMAHttpServlet
 */
public class ExportBatchClassDownloadServlet extends DCMAHttpServlet {

	/**
	 * serialVersionUID long.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * SERIALIZATION_EXT String.
	 */
	private static final String SERIALIZATION_EXT = FileType.SER.getExtensionWithDot();

	/**
	 * ZIP_EXT String.
	 */
	private static final String ZIP_EXT = FileType.ZIP.getExtensionWithDot();

	/**
	 * Overriden doPost method.
	 * 
	 * @param request HttpServletRequest
	 * @param response HttpServletResponse
	 * @throws IOException
	 */
	@Override
	public final void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		doGet(request, response);
	}

	/**
	 * Overriden doGet method.
	 * 
	 * @param request HttpServletRequest
	 * @param response HttpServletResponse
	 * @throws IOException
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

		BatchClassService batchClassService = this.getSingleBeanOfType(BatchClassService.class);
		BatchSchemaService batchSchemaService = this.getSingleBeanOfType(BatchSchemaService.class);
		BatchClass batchClass = batchClassService.getLoadedBatchClassByIdentifier(req.getParameter("identifier"));
		if (batchClass == null) {
			LOG.error("Incorrect batch class identifier specified.");
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Incorrect batch class identifier specified.");
		} else {
			Calendar cal = Calendar.getInstance();
			String exportSerailizationFolderPath = batchSchemaService.getBatchExportFolderLocation();

			SimpleDateFormat formatter = new SimpleDateFormat("MMddyy", Locale.getDefault());
			String formattedDate = formatter.format(new Date());
			String zipFileName = batchClass.getIdentifier() + BatchClassManagementConstants.UNDERSCORE + formattedDate
					+ BatchClassManagementConstants.UNDERSCORE + cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.SECOND);

			String tempFolderLocation = exportSerailizationFolderPath + File.separator + zipFileName;
			File copiedFolder = new File(tempFolderLocation);

			if (copiedFolder.exists()) {
				copiedFolder.delete();
			}

			copiedFolder.mkdirs();

			BatchClassUtil.copyModules(batchClass);
			BatchClassUtil.copyDocumentTypes(batchClass);
			BatchClassUtil.copyScannerConfig(batchClass);
			BatchClassUtil.exportEmailConfiguration(batchClass);
			BatchClassUtil.exportUserGroups(batchClass);
			BatchClassUtil.exportBatchClassField(batchClass);

			File serializedExportFile = new File(tempFolderLocation + File.separator + batchClass.getIdentifier() + SERIALIZATION_EXT);

			try {
				SerializationUtils.serialize(batchClass, new FileOutputStream(serializedExportFile));
				boolean isImagemagickBaseFolder = false;
				String imageMagickBaseFolderParam = req.getParameter(batchSchemaService.getImagemagickBaseFolderName());
				if (imageMagickBaseFolderParam != null
						&& (imageMagickBaseFolderParam.equalsIgnoreCase(batchSchemaService.getImagemagickBaseFolderName()) || Boolean
								.parseBoolean(imageMagickBaseFolderParam))) {
					isImagemagickBaseFolder = true;
				}

				boolean isSearchSampleName = false;
				String isSearchSampleNameParam = req.getParameter(batchSchemaService.getSearchSampleName());
				if (isSearchSampleNameParam != null
						&& (isSearchSampleNameParam.equalsIgnoreCase(batchSchemaService.getSearchSampleName()) || Boolean
								.parseBoolean(isSearchSampleNameParam))) {
					isSearchSampleName = true;
				}

				File originalFolder = new File(batchSchemaService.getBaseSampleFDLock() + File.separator + batchClass.getIdentifier());

				if (originalFolder.isDirectory()) {

					validateFolderAndFile(batchSchemaService, copiedFolder, isImagemagickBaseFolder, isSearchSampleName,
							originalFolder);
				}

			} catch (FileNotFoundException e) {
				// Unable to read serializable file
				LOG.error("Error occurred while creating the serializable file." + e, e);
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error occurred while creating the serializable file.");

			} catch (IOException e) {
				// Unable to create the temporary export file(s)/folder(s)
				LOG.error("Error occurred while creating the serializable file." + e, e);
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						"Error occurred while creating the serializable file.Please try again");
			}
			resp.setContentType("application/x-zip\r\n");
			resp.setHeader("Content-Disposition", "attachment; filename=\"" + zipFileName + ZIP_EXT + "\"\r\n");
			ServletOutputStream out = null;
			ZipOutputStream zout = null;
			try {
				out = resp.getOutputStream();
				zout = new ZipOutputStream(out);
				FileUtils.zipDirectory(tempFolderLocation, zout, zipFileName);
				resp.setStatus(HttpServletResponse.SC_OK);
			} catch (IOException e) {
				// Unable to create the temporary export file(s)/folder(s)
				LOG.error("Error occurred while creating the zip file." + e, e);
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to export.Please try again.");
			} finally {
				// clean up code
				if (zout != null) {
					zout.close();
				}
				if (out != null) {
					out.flush();
				}
				FileUtils.deleteDirectoryAndContentsRecursive(copiedFolder);
			}
		}
	}

	private void validateFolderAndFile(BatchSchemaService batchSchemaService, File copiedFolder, boolean isImagemagickBaseFolder,
			boolean isSearchSampleName, File originalFolder) throws IOException {
		String[] folderList = originalFolder.list();
		Arrays.sort(folderList);

		for (int i = 0; i < folderList.length; i++) {
			if (FilenameUtils.getName(folderList[i]).equalsIgnoreCase(batchSchemaService.getTestKVExtractionFolderName())
					|| FilenameUtils.getName(folderList[i]).equalsIgnoreCase(batchSchemaService.getTestTableFolderName())
					|| FilenameUtils.getName(folderList[i]).equalsIgnoreCase(batchSchemaService.getFileboundPluginMappingFolderName())) {
				// Skip this folder
				continue;
			} else if (FilenameUtils.getName(folderList[i]).equalsIgnoreCase(batchSchemaService.getImagemagickBaseFolderName())
					&& isImagemagickBaseFolder) {
				FileUtils.copyDirectoryWithContents(new File(originalFolder, folderList[i]), new File(copiedFolder, folderList[i]));
			} else if (FilenameUtils.getName(folderList[i]).equalsIgnoreCase(batchSchemaService.getSearchSampleName())
					&& isSearchSampleName) {
				FileUtils.copyDirectoryWithContents(new File(originalFolder, folderList[i]), new File(copiedFolder, folderList[i]));
			} else if (!(FilenameUtils.getName(folderList[i]).equalsIgnoreCase(batchSchemaService.getImagemagickBaseFolderName()) || FilenameUtils
					.getName(folderList[i]).equalsIgnoreCase(batchSchemaService.getSearchSampleName()))) {
				FileUtils.copyDirectoryWithContents(new File(originalFolder, folderList[i]), new File(copiedFolder, folderList[i]));
			}
		}
	}
}
