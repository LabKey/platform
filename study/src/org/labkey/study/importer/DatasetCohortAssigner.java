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

import org.apache.xmlbeans.XmlException;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Cohort;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.DatasetsDocument;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: adam
 * Date: May 21, 2009
 * Time: 3:22:31 PM
 */
public class DatasetCohortAssigner
{
    // Parses the dataset manifest again to retrieve the cohort assigments; should cache info from the first parsing
    // somewhere in the ImportContext
    void process(StudyImpl study, ImportContext ctx, File root) throws SQLException, XmlException, IOException, StudyImporter.StudyImportException
    {
        DatasetsDocument.Datasets datasets = DatasetImporter.getDatasetsManifest(ctx, root);

        if (null != datasets)
        {
            Container c = ctx.getContainer();
            User user = ctx.getUser();

            StudyManager studyManager = StudyManager.getInstance();
            Map<String, DatasetImporter.DatasetImportProperties> datasetProps = DatasetImporter.getDatasetImportProperties(datasets);
            DataSetDefinition[] defs = study.getDataSets();

            for (DataSetDefinition def : defs)
            {
                DatasetImporter.DatasetImportProperties props = datasetProps.get(def.getName());

                Cohort cohort = def.getCohort();
                String oldCohortLabel = null != cohort ? cohort.getLabel() : null;

                if (!PageFlowUtil.nullSafeEquals(oldCohortLabel, props.getCohort()))
                {
                    CohortImpl newCohort = studyManager.getCohortByLabel(c, user, props.getCohort());
                    def = def.createMutable();
                    def.setCohortId(newCohort.getRowId());
                    StudyManager.getInstance().updateDataSetDefinition(user, def);
                }
            }
        }
    }
}