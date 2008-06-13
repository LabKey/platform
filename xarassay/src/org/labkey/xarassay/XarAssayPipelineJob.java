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

package org.labkey.xarassay;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.sql.SQLException;

/**
 * User: phussey
 * Date: Sep 19, 2007
 * Time: 11:35:02 AM
 */
public class XarAssayPipelineJob extends PipelineJob
{

    public XarAssayPipelineJob(ViewBackgroundInfo info, File logFile) throws SQLException
    {
        super(XarAssayPipelineProvider.name, info);
        setLogFile(logFile);
    }

    public ActionURL getStatusHref()
    {
        // No custom viewing for status while loading
        return null;
    }

    public String getDescription()
    {
        return "Assay load from Xar";
    }
    public void run()
    {
        setStatus(PipelineJob.COMPLETE_STATUS);
    }



}
