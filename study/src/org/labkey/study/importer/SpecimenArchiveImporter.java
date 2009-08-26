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

import org.labkey.api.data.Container;
import org.labkey.api.study.StudyImportException;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.xml.RepositoryType;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:52:59 PM
 */
public class SpecimenArchiveImporter
{
    void process(ImportContext ctx, File root) throws IOException, SQLException, StudyImportException
    {
        StudyDocument.Study.Specimens specimens = ctx.getStudyXml().getSpecimens();

        if (null != specimens)
        {
            Container c = ctx.getContainer();

            // TODO: support specimen archives that are not zipped
            RepositoryType.Enum repositoryType = specimens.getRepositoryType();
            StudyController.updateRepositorySettings(c, RepositoryType.STANDARD == repositoryType);

            if (null != specimens.getDir())
            {
                File specimenDir = StudyImporter.getStudyDir(root, specimens.getDir(), "Study.xml");

                if (null != specimens.getFile())
                {
                    File specimenFile = StudyImporter.getStudyFile(root, specimenDir, specimens.getFile(), "Study.xml");
                    SpringSpecimenController.submitSpecimenBatch(c, ctx.getUser(), ctx.getUrl(), specimenFile);
                }
            }
        }
    }
}
