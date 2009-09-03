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

import org.labkey.api.pipeline.*;
import org.labkey.api.util.DateUtil;
import org.labkey.api.writer.ZipUtil;
import org.labkey.study.importer.SpecimenImporter;

import java.util.List;
import java.util.Date;
import java.io.File;

/*
* User: adam
* Date: Aug 31, 2009
* Time: 9:12:22 AM
*/
public abstract class AbstractSpecimenTask<FactoryType extends AbstractSpecimenTaskFactory<FactoryType>> extends PipelineJob.Task<TaskFactory>
{
    public AbstractSpecimenTask(FactoryType factory, PipelineJob job)
    {
        super(factory, job);
    }

    public abstract File getSpecimenArchive() throws Exception;

    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        File specimenArchive;

        try
        {
            specimenArchive = getSpecimenArchive();
        }
        catch (Exception e)
        {
            throw new PipelineJobException("Error attempting to load specimen archive", e);
        }

        if (null == specimenArchive)
        {
            job.info("No specimen archive");
        }
        else
        {
            String status = PipelineJob.ERROR_STATUS;
            File unzipDir = null;
            try
            {
                job.info("Unzipping specimen archive " +  specimenArchive.getPath());
                String tempDirName = DateUtil.formatDateTime(new Date(), "yyMMddHHmmssSSS");
                unzipDir = new File(specimenArchive.getParentFile(), tempDirName);
                try
                {
                    job.setStatus("Unzipping");
                    List<File> files = ZipUtil.unzipToDirectory(specimenArchive, unzipDir, job.getLogger());
                    job.info("Archive unzipped to " + unzipDir.getPath());
                    job.info("Starting import...");
                    job.setStatus("Processing");

                    SpecimenImporter importer = new SpecimenImporter();
                    importer.process(job.getUser(), job.getContainer(), files, job.getLogger());
                }
                catch (Exception e)
                {
                    status = PipelineJob.ERROR_STATUS;
                    job.error("Unexpected error processing specimen archive", e);
                }
            }
            finally
            {
                delete(unzipDir);
            }
        }

        return new RecordedActionSet();
    }


    private void delete(File file)
    {
        if (file.isDirectory())
        {
            for (File child : file.listFiles())
                delete(child);
        }
        getJob().info("Deleting " + file.getPath());
        file.delete();
    }
}