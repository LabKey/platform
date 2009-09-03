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

import org.labkey.api.pipeline.*;
import org.labkey.api.util.FileType;
import org.labkey.api.study.ExternalStudyImporter;
import org.labkey.study.writer.StudySerializationRegistryImpl;

import java.util.*;

/*
* User: adam
* Date: Aug 31, 2009
* Time: 9:12:22 AM
*/
public class StudyImportFinalizerTask extends PipelineJob.Task<StudyImportFinalizerTask.Factory>
{
    public StudyImportFinalizerTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        boolean success = false;
        PipelineJob job = getJob();
        StudyJobSupport support = job.getJobSupport(StudyJobSupport.class);

        try
        {
            // TODO: Pull these from the study serialization registry?
            Collection<InternalStudyImporter> internalImporters = new LinkedList<InternalStudyImporter>();

            // Dataset and Specimen upload jobs delete "unused" participants, so we need to defer setting participant
            // cohorts until the end of upload.
            internalImporters.add(new CohortImporter());

            // Can't assign visits to cohorts until the cohorts are created
            internalImporters.add(new VisitCohortAssigner());

            // Can't assign datasets to cohorts until the cohorts are created
            internalImporters.add(new DatasetCohortAssigner());

            for (InternalStudyImporter importer : internalImporters)
            {
                job.info("Importing " + importer.getDescription());
                job.setStatus("IMPORT " + importer.getDescription());
                importer.process(support.getStudy(), support.getImportContext(), support.getRoot());
                job.info("Done importing " + importer.getDescription());
            }

            for (ExternalStudyImporter importer : StudySerializationRegistryImpl.get().getRegisteredStudyImporters())
            {
                job.info("Importing " + importer.getDescription());
                job.setStatus("IMPORT " + importer.getDescription());
                importer.process(support.getImportContext(), support.getRoot());
                job.info("Done importing " + importer.getDescription());
            }

            success = true;
        }
        catch (Exception e)
        {
            job.error("Exception during study import", e);
        }
        finally
        {
            job.setStatus(success ? PipelineJob.COMPLETE_STATUS : PipelineJob.ERROR_STATUS);
        }

        return new RecordedActionSet();
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(StudyImportFinalizerTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StudyImportFinalizerTask(this, job);
        }

        public FileType[] getInputTypes()
        {
            return new FileType[0];
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return "THIS IS A TEST";
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}