/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.query.reports;

import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineStatusFile;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jun 20, 2007
 */
public class ReportsPipelineProvider extends PipelineProvider
{
    static String NAME = "reports";

    public ReportsPipelineProvider()
    {
        super(NAME);
    }

    public void preDeleteStatusFile(PipelineStatusFile sf) throws StatusUpdateException
    {
        // clean up all the temp files on status file deletion
        File filePath = new File(sf.getFilePath());
        if (filePath.exists())
        {
            File dir = filePath.getParentFile();
            for (File file : dir.listFiles())
                file.delete();
            dir.delete();
        }
    }
}
