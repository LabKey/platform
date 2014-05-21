/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.study.Study;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.CohortManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.CohortMode;
import org.labkey.study.xml.CohortType;
import org.labkey.study.xml.CohortsDocument;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:26:29 PM
 */
public class CohortImporter implements InternalStudyImporter
{
    public String getDescription()
    {
        return "cohort settings";
    }

    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws IOException, SQLException, ServletException, ImportException
    {
        StudyImpl study = ctx.getStudy();
        StudyDocument.Study.Cohorts cohortsXml = ctx.getXml().getCohorts();

        if (null != cohortsXml)
        {
            CohortType.Enum cohortType = cohortsXml.getType();
            CohortMode.Enum cohortMode = cohortsXml.getMode();
            String cohortFileName = cohortsXml.getFile();
            Map<String, Integer> p2c = null;

            //
            // In ITN12.2 or > the export format changed to always persist a cohort.xml file
            // regardless of cohort type to include 'enrolled' information for each cohort.
            // The importer must support this new format as well as the older format
            // for automatic cohort types.  If we are loading an older folder with automatic cohorts
            // then each cohort is enrolled by default.
            //
            if (!StringUtils.isEmpty(cohortFileName))
            {
                p2c = buildParticipantCohortMap(ctx, root, study, cohortFileName);
            }

            if (cohortType == CohortType.AUTOMATIC)
            {
                Integer datasetId = getValidCohortDatasetId(cohortsXml, study);
                String datasetProperty = null != datasetId ? cohortsXml.getDatasetProperty() : null;

                if (p2c == null)
                {
                    //
                    // older folder format, no cohorts.xml file
                    //
                    ctx.getLogger().info("Loading automatic cohort settings");
                    CohortManager.getInstance().setAutomaticCohortAssignment(study, ctx.getUser(), datasetId, datasetProperty,
                            cohortMode == CohortMode.ADVANCED, true);
                }
                else
                {
                    //
                    // newer format - still automatic but we want to ensure we pick up enrolled state for each
                    // cohort
                    //
                    CohortManager.getInstance().setAutomaticCohortAssignment(study, ctx.getUser(), datasetId, datasetProperty,
                            cohortMode == CohortMode.ADVANCED, p2c);
                }
            }
            else
            {
                //
                // For manual cohort assigments, the format stays the same except for the added
                // enrolled state for each cohort.
                //
                assert (p2c != null);
                CohortManager.getInstance().setManualCohortAssignment(study, ctx.getUser(), p2c);
            }
        }
    }

    // Return the cohort dataset ID iff the <cohorts> element includes it and the corresponding dataset exists.
    // LabKey export, publish, and ancillary study creation always include the cohort dataset, but a manually
    // created archive might not. See #19944 and #20397.
    private @Nullable Integer getValidCohortDatasetId(StudyDocument.Study.Cohorts cohortsXml, Study study)
    {
        if (cohortsXml.isSetDatasetId())
        {
            int id = cohortsXml.getDatasetId();

            if (null != study.getDataSet(id))
                return id;
        }

        return null;
    }

    private Map<String, Integer> buildParticipantCohortMap(StudyImportContext ctx, VirtualFile root, StudyImpl study, String cohortFileName) throws IOException, SQLException, ImportException, ServletException
    {
        //
        // ITN12.2 branch and newer releases will always export a separate cohorts.xml table to
        // round-trip the 'enrolled' bit.
        //
        ctx.getLogger().info("Loading cohort assignments from " + root.getRelativePath(cohortFileName));
        CohortsDocument cohortAssignmentXml;

        try
        {
            XmlObject xml = root.getXmlBean(cohortFileName);
            if (xml instanceof CohortsDocument)
            {
                cohortAssignmentXml = (CohortsDocument)xml;
                XmlBeansUtil.validateXmlDocument(cohortAssignmentXml);
            }
            else
                throw new ImportException("Unable to get an instance of CohortsDocument");
        }
        catch (XmlValidationException e)
        {
            throw new InvalidFileException(root.getRelativePath(cohortFileName), e);
        }

        Map<String, Integer> p2c = new HashMap<>();
        CohortsDocument.Cohorts.Cohort[] cohortXmls = cohortAssignmentXml.getCohorts().getCohortArray();

        for (CohortsDocument.Cohorts.Cohort cohortXml : cohortXmls)
        {
            String label = cohortXml.getLabel();
            boolean enrolled = cohortXml.isSetEnrolled() ? cohortXml.getEnrolled() : true;
            Integer subjectCount = cohortXml.isSetSubjectCount() ? cohortXml.getSubjectCount() : null;
            String description = cohortXml.isSetDescription() ? cohortXml.getDescription() : null;
            CohortImpl cohort = CohortManager.getInstance().ensureCohort(study, ctx.getUser(), label, enrolled, subjectCount, description);

            for (String ptid : cohortXml.getIdArray())
                p2c.put(ptid, cohort.getRowId());
        }

        return p2c;
    }
}
