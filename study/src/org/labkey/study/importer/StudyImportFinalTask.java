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

package org.labkey.study.importer;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.study.importer.SimpleStudyImporter;
import org.labkey.api.study.importer.SimpleStudyImporter.Timing;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.writer.StudySerializationRegistry;
import org.springframework.validation.BindException;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/*
* User: adam
* Date: Aug 31, 2009
* Time: 9:12:22 AM
*/
public class StudyImportFinalTask
{
    private StudyImportFinalTask()
    {
    }

    public static void doImport(PipelineJob job, StudyImportContext ctx, BindException errors) throws PipelineJobException
    {
        // Construct all the SimpleStudyImporters that are designated as "Late"
        List<SimpleStudyImporter> simpleStudyImporters = StudySerializationRegistry.get().getSimpleStudyImporters().stream()
            .filter(ssi -> ssi.getTiming() == Timing.Late)
            .toList();

        try
        {
            // Initialize the SimpleStudyImporters
            for (SimpleStudyImporter ssi : simpleStudyImporters)
                ssi.preHandling(ctx);

            Collection<InternalStudyImporter> internalImporters = new LinkedList<>();

            // Dataset and Specimen upload jobs delete "unused" participants, so we need to defer setting participant
            // cohorts until the end of upload.
            internalImporters.add(new CohortImporter());

            // Can't assign visits or datasets to cohorts until the cohorts are created
            internalImporters.add(new VisitCohortAssigner());
            internalImporters.add(new DatasetCohortAssigner());

            internalImporters.add(new ParticipantCommentImporter());
            internalImporters.add(new ParticipantGroupImporter());
            internalImporters.add(new ProtocolDocumentImporter());
            internalImporters.add(new StudyViewsImporter());

            // TreatmentVisitMap needs to import after cohort info is loaded (issue 19947)
            internalImporters.add(new TreatmentVisitMapImporter());

            VirtualFile vf = ctx.getRoot();
            for (InternalStudyImporter importer : internalImporters)
            {
                if (job != null)
                    job.setStatus("IMPORT " + importer.getDescription());

                importer.process(ctx, vf, errors);
            }

            for (SimpleStudyImporter importer : StudySerializationRegistry.get().getSimpleStudyImporters())
            {
                if (importer.getTiming() == Timing.Late)
                    importer.process(ctx, vf, errors);
            }
        }
        catch (Exception e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            for (SimpleStudyImporter importer : simpleStudyImporters)
                importer.postHandling(ctx);
        }
    }
}
