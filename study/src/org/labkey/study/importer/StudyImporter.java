/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.study.importer;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.StudyImportException;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Apr 11, 2009
 * Time: 2:48:31 PM
 */
public class StudyImporter
{
    private static final Logger _log = Logger.getLogger(StudyImporter.class);

    private Container _c;
    private User _user;
    private ActionURL _url;
    private File _root;
    private BindException _errors;

    public StudyImporter(Container c, User user, ActionURL url, File root, BindException errors)
    {
        _c = c;
        _user = user;
        _url = url;
        _root = root;
        _errors = errors;
    }

    private StudyImpl getStudy()
    {
        return getStudy(false);
    }

    private StudyImpl getStudy(boolean allowNullStudy)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(_c);
        if (!allowNullStudy && study == null)
        {
            throw new IllegalStateException("Study does not exist.");
        }
        return study;
    }

    public boolean process() throws SQLException, ServletException, IOException, SAXException, ParserConfigurationException, XmlException, StudyImportException
    {
        _log.info("Loading study to folder " + _c.getPath());

        ImportContext ctx = new ImportContext(_user, _c, _root, _url);
        StudyDocument.Study studyXml = ctx.getStudyXml();
        StudyImpl study = getStudy(true);

        // Create the study if it doesn't exist... otherwise, modify the existing properties
        if (null == study)
        {
            // Create study
            StudyController.StudyPropertiesForm studyForm = new StudyController.StudyPropertiesForm();

            if (studyXml.isSetLabel())
                studyForm.setLabel(studyXml.getLabel());

            if (studyXml.isSetDateBased())
                studyForm.setDateBased(studyXml.getDateBased());

            if (studyXml.isSetStartDate())
                studyForm.setStartDate(studyXml.getStartDate().getTime());

            if (studyXml.isSetSecurityType())
                studyForm.setSecurityType(SecurityType.valueOf(studyXml.getSecurityType().toString()));

            StudyController.createStudy(getStudy(true), _c, _user, studyForm);
        }
        else
        {
            // TODO: Change these props and save only if values have changed
            study = study.createMutable();

            if (studyXml.isSetLabel())
                study.setLabel(studyXml.getLabel());

            if (studyXml.isSetDateBased())
                study.setDateBased(studyXml.getDateBased());

            if (studyXml.isSetStartDate())
                study.setStartDate(studyXml.getStartDate().getTime());

            if (studyXml.isSetSecurityType())
                study.setSecurityType(SecurityType.valueOf(studyXml.getSecurityType().toString()));

            StudyManager.getInstance().updateStudy(_user, study);            
        }

        new MissingValueImporter().process(ctx);
        new QcStatesImporter().process(getStudy(), ctx);

        if (!new VisitImporter().process(getStudy(), ctx, _root, _errors))
            return false;

        if (!new DatasetImporter().process(getStudy(), ctx, _root, _errors))
            return false;

        new SpecimenArchiveImporter().process(ctx, _root);

        // Queue up a pipeline job to handle all the tasks that must happen after dataset and specimen upload
        PipelineService.get().queueJob(new StudyImportJob(getStudy(), ctx, _root));

        _log.info("Pipeline jobs initialized for study " + getStudy().getLabel() + " in folder " + _c.getPath());

        return true;
    }

    public static class DatasetLockExistsException extends ServletException {}

    public static class InvalidFileException extends StudyImportException
    {
        public InvalidFileException(File root, File file, Throwable t)
        {
            super(getErrorString(root, file, t.getMessage()));
        }

        public InvalidFileException(File root, File file, XmlException e)
        {
            super(getErrorString(root, file, e));
        }

        // Special handling for XmlException: e.getMessage() includes absolute path to file, which don't want to display
        private static String getErrorString(File root, File file, XmlException e)
        {
            XmlError error = e.getError();
            return getErrorString(root, file, error.getLine() + ":" + error.getColumn() + ": " + error.getMessage());
        }

        private static String getErrorString(File root, File file, String message)
        {
            return StudyImporter.getRelativePath(root, file) + " is not valid: " + message;
        }
    }

    public static File getStudyDir(File root, String dirName, String source) throws StudyImportException
    {
        File dir = null != dirName ? new File(root, dirName) : root;

        if (!dir.exists())
            throw new StudyImportException(source + " refers to a directory that does not exist: " + getRelativePath(root, dir));

        if (!dir.isDirectory())
            throw new StudyImportException(source + " refers to " + getRelativePath(root, dir) + ": expected a directory but found a file");

        return dir;
    }

    public static File getStudyFile(File root, File dir, String name, String source) throws StudyImportException
    {
        File file = new File(dir, name);

        if (!file.exists())
            throw new StudyImportException(source + " refers to a file that does not exist: " + getRelativePath(root, file));

        if (!file.isFile())
            throw new StudyImportException(source + " refers to " + getRelativePath(root, file) + ": expected a file but found a directory");

        return file;
    }

    // Returns a filepath relative to root... this provides path information but hides the pipeline root path.
    public static String getRelativePath(File root, File file)
    {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();

        if (filePath.startsWith(rootPath))
            return filePath.substring(rootPath.length());
        else
            return file.getName();
    }
}
