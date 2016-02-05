/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.model.StudyImpl;

/*
* User: adam
* Date: Sep 1, 2009
* Time: 3:17:44 PM
*/

// This task is used to import datasets directly via the pipeline ui .  DatasetBatch is the associated pipeline job.
public class StandaloneDatasetTask extends AbstractDatasetImportTask<StandaloneDatasetTask.Factory>
{
    private transient StudyImpl _study = null;

    private StandaloneDatasetTask(Factory factory, PipelineJob job, StudyImportContext ctx)
    {
        super(factory, job, ctx);
    }

    @Override
    protected String getDatasetsFileName() throws ImportException
    {
        return getJob().getJobSupport(DatasetJobSupport.class).getDatasetsFileName();
    }

    @Override
    protected VirtualFile getDatasetsDirectory() throws ImportException
    {
        return getJob().getJobSupport(DatasetJobSupport.class).getDatasetsDirectory();
    }

    public StudyImpl getStudy()
    {
        if (null == _study)
            _study = getStudyManager().getStudy(getJob().getContainer());
        return _study;
    }


    public static class Factory extends AbstractDatasetImportTaskFactory<Factory>
    {
        public Factory()
        {
            super(StandaloneDatasetTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            StudyImportContext ctx = new StudyImportContext(job.getUser(), job.getContainer(), null, new PipelineJobLoggerGetter(job));
            return new StandaloneDatasetTask(this, job, ctx);
        }
    }
}