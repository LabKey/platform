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

import org.labkey.study.xml.StudyDocument;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Study;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.pipeline.DatasetBatch;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:42:02 PM
 */
public class DatasetImporter
{
    boolean process(Study study, ImportContext ctx, File root, BindException errors) throws IOException, SQLException, StudyImporter.DatasetLockExistsException
    {
        StudyDocument.Study.Datasets datasetsXml = ctx.getStudyXml().getDatasets();

        if (null != datasetsXml)
        {
            File datasetDir = (null == datasetsXml.getDir() ? root : new File(root, datasetsXml.getDir()));

            StudyDocument.Study.Datasets.Schema schema = datasetsXml.getSchema();
            String schemaSource = schema.getFile();
            String labelColumn = schema.getLabelColumn();
            String typeNameColumn = schema.getTypeNameColumn();
            String typeIdColumn = schema.getTypeIdColumn();

            String datasetFilename = ctx.getStudyXml().getDatasets().getDefinition().getFile();

            File schemaFile = new File(datasetDir, schemaSource);

            if (schemaFile.exists())
            {
                if (!StudyManager.getInstance().bulkImportTypes(study, schemaFile, ctx.getUser(), labelColumn, typeNameColumn, typeIdColumn, errors))
                    return false;

                File datasetFile = new File(datasetDir, datasetFilename);

                if (datasetFile.exists())
                {
                    submitStudyBatch(study, datasetFile, ctx.getContainer(), ctx.getUser(), ctx.getUrl());
                }
            }
        }

        return true;
    }

    public static void submitStudyBatch(Study study, File datasetFile, Container c, User user, ActionURL url) throws IOException, StudyImporter.DatasetLockExistsException, SQLException
    {
        if (null == datasetFile || !datasetFile.exists() || !datasetFile.isFile())
        {
            HttpView.throwNotFound();
            return;
        }

        File lockFile = StudyPipeline.lockForDataset(study, datasetFile);
        if (!datasetFile.canRead() || lockFile.exists())
        {
            throw new StudyImporter.DatasetLockExistsException();
        }

        DatasetBatch batch = new DatasetBatch(new ViewBackgroundInfo(c, user, url), datasetFile);
        batch.submit();
    }
}
