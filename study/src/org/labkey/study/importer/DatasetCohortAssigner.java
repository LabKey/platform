/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.labkey.api.admin.ImportException;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Cohort;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.xml.DatasetsDocument;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * User: adam
 * Date: May 21, 2009
 * Time: 3:22:31 PM
 */
public class DatasetCohortAssigner implements InternalStudyImporter
{
    public String getDescription()
    {
        return "dataset cohort assignments";
    }

    public String getDataType() { return StudyArchiveDataTypes.DATASET_DEFINITIONS; }

    // Parses the dataset manifest again to retrieve the cohort assignments; should cache info from the first parsing
    // somewhere in the StudyImportContext
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws IOException, ImportException
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (isValidForImportArchive(ctx, root))
        {
            StudyImpl study = ctx.getStudy();
            DatasetsDocument.Datasets datasets = DatasetDefinitionImporter.getDatasetsManifest(ctx, root, false);

            ctx.getLogger().info("Loading " + getDescription());

            Container c = ctx.getContainer();
            User user = ctx.getUser();

            StudyManager studyManager = StudyManager.getInstance();
            Map<String, DatasetDefinitionImporter.DatasetImportProperties> datasetProps = DatasetDefinitionImporter.getDatasetImportProperties(datasets);

            for (DatasetDefinition def : study.getDatasets())
            {
                DatasetDefinitionImporter.DatasetImportProperties props = datasetProps.get(def.getName());

                // Props will be null if a dataset is referenced in the visit map but not in the datasets_manifest
                if (null == props)
                {
                    ctx.getLogger().info("INFORMATION: Dataset \"" + def.getName() + "\" found in the study that is not defined in datasets_manifest.xml");
                }
                else
                {
                    Cohort cohort = def.getCohort();
                    String oldCohortLabel = null != cohort ? cohort.getLabel() : null;

                    if (!Objects.equals(oldCohortLabel, props.getCohort()))
                    {
                        CohortImpl newCohort = studyManager.getCohortByLabel(c, user, props.getCohort());
                        def = def.createMutable();
                        def.setCohortId(newCohort.getRowId());
                        StudyManager.getInstance().updateDatasetDefinition(user, def);
                    }
                }
            }

            ctx.getLogger().info("Done importing " + getDescription());
        }
    }

    @Override
    public boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        try
        {
            return DatasetDefinitionImporter.getDatasetsManifest(ctx, root, false) != null;
        }
        catch (IOException e)
        {
            return false;
        }
    }
}
