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

package org.labkey.study.importer;

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.pipeline.DatasetBatch;
import org.labkey.study.pipeline.StudyPipeline;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/*
* User: adam
* Date: Sep 1, 2009
* Time: 12:31:11 PM
*/
public class DatasetImportUtils
{
    public static void submitStudyBatch(Study study, VirtualFile datasetsDirectory, String datasetsFileName, Container c, User user, ActionURL url, PipeRoot root) throws IOException, DatasetLockExistsException, SQLException
    {
        if (null == datasetsDirectory || null == datasetsFileName)
        {
            throw new NotFoundException();
        }

        File lockFile = StudyPipeline.lockForDataset(study, Path.parse(datasetsDirectory.getLocation()).append(datasetsFileName));
        if (lockFile.exists())
        {
            throw new DatasetLockExistsException();
        }

        DatasetBatch batch = new DatasetBatch(new ViewBackgroundInfo(c, user, url), datasetsDirectory, datasetsFileName, root);
        batch.submit();
    }

    public static class DatasetLockExistsException extends ServletException
    {
    }
}
