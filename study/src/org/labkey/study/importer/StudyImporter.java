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
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Visit;
import org.labkey.study.pipeline.DatasetBatch;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.visitmanager.VisitManager;
import org.labkey.study.xml.CohortType;
import org.labkey.study.xml.RepositoryType;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

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

    private Study getStudy()
    {
        return getStudy(false);
    }

    private Study getStudy(boolean allowNullStudy)
    {
        Study study = StudyManager.getInstance().getStudy(_c);
        if (!allowNullStudy && study == null)
        {
            throw new IllegalStateException("Study does not exist.");
        }
        return study;
    }

    public boolean process() throws SQLException, ServletException, IOException, SAXException, ParserConfigurationException, XmlException
    {
        File file = new File(_root, "study.xml");

        _log.info("Loading study: " + file.getAbsolutePath());

        StudyDocument doc = StudyDocument.Factory.parse(file);
        org.labkey.study.xml.StudyDocument.Study studyXml = doc.getStudy();

        // Create study
        StudyController.StudyPropertiesForm studyForm = new StudyController.StudyPropertiesForm();
        studyForm.setLabel(studyXml.getLabel());
        studyForm.setDateBased(studyXml.getDateBased());
        studyForm.setStartDate(studyXml.getStartDate().getTime());
        studyForm.setSecurityType(SecurityType.valueOf(studyXml.getSecurityType().toString()));
        StudyController.createStudy(getStudy(true), _c, _user, studyForm);

        // Visit map
        StudyDocument.Study.Visits visits = studyXml.getVisits();
        File visitMap = new File(_root, visits.getSource());

        if (visitMap.exists())
        {
            String content = PageFlowUtil.getFileContentsAsString(visitMap);

            VisitMapImporter importer = new VisitMapImporter();
            List<String> errorMsg = new LinkedList<String>();

            if (!importer.process(_user, getStudy(), content, VisitMapImporter.Format.DataFax, errorMsg))
            {
                for (String error : errorMsg)
                    _errors.reject("uploadVisitMap", error);

                return false;
            }
        }

        VisitManager visitManager = StudyManager.getInstance().getVisitManager(getStudy());

        for (StudyDocument.Study.Visits.Visit visitXml : visits.getVisitArray())
        {
            // Just a proof of concept -- only works for "show by default".  TODO: Move to alternative, xml-based visit map
            double sequenceNum = visitXml.getSequenceNum();
            Visit visit = visitManager.findVisitBySequence(sequenceNum);
            Visit mutable = visit.createMutable();
            mutable.setShowByDefault(visitXml.getShowByDefault());
            StudyManager.getInstance().updateVisit(_user, mutable);
        }

        // Cohorts
        StudyDocument.Study.Cohorts cohorts = studyXml.getCohorts();
        CohortType.Enum cohortType = cohorts.getType();

        if (cohortType == CohortType.AUTOMATIC)
        {
            Integer dataSetId = cohorts.getDataSetId();
            String dataSetProperty = cohorts.getDataSetProperty();
            CohortController.updateAutomaticCohort(getStudy(), _user, dataSetId, dataSetProperty);
        }
        else
            assert false : "Unsupported cohort type";

        // QC States
        // TODO: Generalize to all qc state properties
        StudyController.ManageQCStatesForm qcForm = new StudyController.ManageQCStatesForm();
        qcForm.setShowPrivateDataByDefault(studyXml.getQcStates().getShowPrivateDataByDefault());
        StudyController.updateQcState(getStudy(), _user, qcForm);

        // Datasets
        StudyDocument.Study.Datasets.Schema schema = studyXml.getDatasets().getSchema();
        String schemaSource = schema.getSource();
        String labelColumn = schema.getLabelColumn();
        String typeNameColumn = schema.getTypeNameColumn();
        String typeIdColumn = schema.getTypeIdColumn();

        String datasetSource = studyXml.getDatasets().getDefinition().getSource();

        File schemaFile = new File(_root, schemaSource);

        if (schemaFile.exists())
        {
            if (!StudyManager.getInstance().bulkImportTypes(getStudy(), schemaFile, _user, labelColumn, typeNameColumn, typeIdColumn, _errors))
                return false;

            File datasetFile = new File(_root, datasetSource);

            if (datasetFile.exists())
            {
                submitStudyBatch(getStudy(), datasetFile, _c, _user, _url);
            }
        }

        // Specimens
        StudyDocument.Study.Specimens specimens = studyXml.getSpecimens();

        if (null != specimens)
        {
            RepositoryType.Enum repositoryType = specimens.getRepositoryType();
            StudyController.updateRepositorySettings(_c, RepositoryType.STANDARD == repositoryType);
            String source = specimens.getSource();
            File specimenFile = new File(_root, source);
            SpringSpecimenController.submitSpecimenBatch(_c, _user, _url, specimenFile);
        }

        _log.info("Finished loading study: " + file.getAbsolutePath());

        return true;
    }

    public static void submitStudyBatch(Study study, File datasetFile, Container c, User user, ActionURL url) throws IOException, DatasetLockExistsException, SQLException
    {
        if (null == datasetFile || !datasetFile.exists() || !datasetFile.isFile())
        {
            HttpView.throwNotFound();
            return;
        }

        File lockFile = StudyPipeline.lockForDataset(study, datasetFile);
        if (!datasetFile.canRead() || lockFile.exists())
        {
            throw new DatasetLockExistsException();
        }

        DatasetBatch batch = new DatasetBatch(new ViewBackgroundInfo(c, user, url), datasetFile);
        batch.submit();
    }

    public static class DatasetLockExistsException extends ServletException {}
}
