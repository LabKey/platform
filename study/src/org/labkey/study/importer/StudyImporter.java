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

    private final Container _c;
    private final User _user;
    private final ActionURL _url;
    private final File _root;
    private final File _studyXml;
    private final BindException _errors;

    public StudyImporter(Container c, User user, ActionURL url, File studyXml, BindException errors)
    {
        _c = c;
        _user = user;
        _url = url;
        _studyXml = studyXml;
        _root = studyXml.getParentFile();
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
        StudyImpl study = getStudy(true);
        boolean reload = (null != study);

        _log.info((reload ? "Reloading" : "Importing") + " study to folder " + _c.getPath());

        ImportContext ctx = new ImportContext(_user, _c, _studyXml, _url);
        StudyDocument.Study studyXml = ctx.getStudyXml();

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

    public static File getStudyFile(File root, File dir, String name, String source) throws StudyImportException
    {
        File file = new File(dir, name);

        if (!file.exists())
            throw new StudyImportException(source + " refers to a file that does not exist: " + StudyImportException.getRelativePath(root, file));

        if (!file.isFile())
            throw new StudyImportException(source + " refers to " + StudyImportException.getRelativePath(root, file) + ": expected a file but found a directory");

        return file;
    }
}
