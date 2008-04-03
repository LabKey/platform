/*
 * Copyright (c) 2007 LabKey Software Foundation
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
package org.labkey.pipeline.mule;

import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.pipeline.api.PipelineStatusFileImpl;

import java.io.Serializable;

/**
 * <code>EPipelineStatus</code> is the transfer object for setting status from
 * a <code>PipelineJob</code> in the Enterprise Pipeline, allowing status to
 * be set from remote machines running jobs.
 *
 * @author brendanx
 */
public class EPipelineStatus implements Serializable
{
    private ViewBackgroundInfo _info;
    private PipelineStatusFile _statusFile;

    public EPipelineStatus(ViewBackgroundInfo info, PipelineStatusFile statusFile)
    {
        _info = info;
        _statusFile = statusFile;
    }

    public ViewBackgroundInfo getInfo()
    {
        return _info;
    }

    public PipelineStatusFile getStatusFile()
    {
        return _statusFile;
    }
}
