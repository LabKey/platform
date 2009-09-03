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

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.study.StudyImportException;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.importer.ImportContext;
import org.labkey.study.importer.StudyJobSupport;
import org.labkey.study.importer.StudyImportJob;
import org.labkey.study.xml.RepositoryType;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.sql.SQLException;

/*
* User: adam
* Date: Sep 1, 2009
* Time: 3:17:44 PM
*/

// This task is used to import specimen archives as part of study import/reload.  StudyImportJob is the associcated pipeline job.
public class StudyImportSpecimenTask extends AbstractSpecimenTask<StudyImportSpecimenTask.Factory>
{
    private StudyImportSpecimenTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    protected File getSpecimenArchive() throws StudyImportException, SQLException
    {
        StudyJobSupport support = getJob().getJobSupport(StudyJobSupport.class);
        ImportContext ctx = support.getImportContext();
        File root = support.getRoot();
        StudyDocument.Study.Specimens specimens = ctx.getStudyXml().getSpecimens();

        if (null != specimens)
        {
            Container c = ctx.getContainer();

            // TODO: support specimen archives that are not zipped
            RepositoryType.Enum repositoryType = specimens.getRepositoryType();
            StudyController.updateRepositorySettings(c, RepositoryType.STANDARD == repositoryType);

            if (null != specimens.getDir())
            {
                File specimenDir = ctx.getStudyDir(root, specimens.getDir(), "Study.xml");

                if (null != specimens.getFile())
                    return StudyImportJob.getStudyFile(root, specimenDir, specimens.getFile(), "Study.xml");
            }
        }

        return null;
    }


    public static class Factory extends AbstractSpecimenTaskFactory<Factory>
    {
        public Factory()
        {
            super(StudyImportSpecimenTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StudyImportSpecimenTask(this, job);
        }
    }
}
