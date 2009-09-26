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

package org.labkey.study.pipeline;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.study.StudyImportException;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.importer.*;
import org.labkey.study.xml.StudyDocument;

import java.io.File;

/*
* User: adam
* Date: Sep 1, 2009
* Time: 3:17:44 PM
*/

// This task is used to import datasets as part of study import/reload.  StudyImportJob is the associcated pipeline job.
public class StudyImportDatasetTask extends AbstractDatasetImportTask<StudyImportDatasetTask.Factory>
{
    private StudyImportDatasetTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public File getDatasetsFile() throws StudyImportException
    {
        StudyJobSupport support = getJob().getJobSupport(StudyJobSupport.class);
        ImportContext ctx = support.getImportContext();
        File root = support.getRoot();

        StudyDocument.Study.Datasets datasetsXml = ctx.getStudyXml().getDatasets();

        if (null != datasetsXml)
        {
            File datasetDir = DatasetImporter.getDatasetDirectory(ctx, root);
            String datasetFilename = datasetsXml.getDefinition().getFile();

            if (null != datasetFilename)
                return ctx.getStudyFile(root, datasetDir, datasetFilename);
        }

        return null;
    }

    public StudyImpl getStudy()
    {
        return getJob().getJobSupport(StudyJobSupport.class).getStudy();
    }


    public static class Factory extends AbstractDatasetImportTaskFactory<Factory>
    {
        public Factory()
        {
            super(StudyImportDatasetTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StudyImportDatasetTask(this, job);
        }
    }
}