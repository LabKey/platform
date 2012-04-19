/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.util.Collections;
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
    protected AbstractSpecimenTask(FactoryType factory, PipelineJob job)
    {
        super(factory, job);
    }

    protected abstract File getSpecimenArchive() throws Exception;

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

        doImport(specimenArchive, job, isMerge());

        return new RecordedActionSet();
    }

    public static void doImport(File specimenArchive, PipelineJob job, boolean merge) throws PipelineJobException
    {

        if (null == specimenArchive)
        {
            job.info("No specimen archive");
        }
        else
        {
            File unzipDir = null;

            try
            {
                job.info("Unzipping specimen archive " +  specimenArchive.getPath());
                String tempDirName = DateUtil.formatDateTime(new Date(), "yyMMddHHmmssSSS");
                unzipDir = new File(specimenArchive.getParentFile(), tempDirName);
                try
                {
                    job.setStatus("UNZIPPING SPECIMEN ARCHIVE");
                    List<File> files = ZipUtil.unzipToDirectory(specimenArchive, unzipDir, job.getLogger());
                    job.info("Archive unzipped to " + unzipDir.getPath());
                    job.info("Starting import...");
                    job.setStatus("PROCESSING SPECIMENS");

                    SpecimenImporter importer = new SpecimenImporter();
                    importer.process(job.getUser(), job.getContainer(), files, merge, job.getLogger());
                }
                catch (Exception e)
                {
                    throw new PipelineJobException(e);
                }
            }
            finally
            {
                delete(unzipDir, job);
                // Since changing specimens in this study will impact specimens in ancillary studies dependent on this study,
                // we need to force a participant/visit refresh in those study containers (if any):
                StudyImpl[] dependentStudies = StudyManager.getInstance().getAncillaryStudies(job.getContainer());
                for (StudyImpl dependentStudy : dependentStudies)
                    StudyManager.getInstance().getVisitManager(dependentStudy).updateParticipantVisits(job.getUser(), Collections.<DataSetDefinition>emptySet());
            }
        }
    }

    protected abstract boolean isMerge();

    private static void delete(File file, PipelineJob job)
    {
        if (file.isDirectory())
        {
            for (File child : file.listFiles())
                delete(child, job);
        }
        job.info("Deleting " + file.getPath());
        file.delete();
    }
}
