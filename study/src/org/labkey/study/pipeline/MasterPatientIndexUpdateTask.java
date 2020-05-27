/*
 * Copyright (c) 2018 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.study.MasterPatientIndexService;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;

public class MasterPatientIndexUpdateTask extends PipelineJob
{
    public static final String PIPELINE_PROVIDER = "MasterPatientIndexPipelineProvider";
    private MasterPatientIndexService _svc;

    // For serialization
    protected MasterPatientIndexUpdateTask() {}

    public MasterPatientIndexUpdateTask(ViewBackgroundInfo info, @NotNull PipeRoot root, MasterPatientIndexService service) throws IOException
    {
        super(PIPELINE_PROVIDER, info, root);

        _svc = service;
        File logFile = File.createTempFile("patientIndexUpdateJob", ".log", root.getRootPath());
        setLogFile(logFile);
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Master Patient Index Update job.";
    }

    @Override
    public void run()
    {
        _svc.updateIndices(this);
    }
}
