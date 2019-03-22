/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.api.pipeline.cmd;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.WorkDirectory;

import java.io.IOException;

/**
 * <code>CommandTask</code>
 */
public interface CommandTask
{
    PipelineJob getJob();

    String getInstallPath();

    String[] getProcessPaths(WorkDirectory.Function f, String key) throws IOException;
}
