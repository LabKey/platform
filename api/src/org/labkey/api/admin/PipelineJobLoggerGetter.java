/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
package org.labkey.api.admin;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.pipeline.PipelineJob;

/**
 * Implementation used within a pipeline job. The job knows how to create a Logger that writes to the job's log file.
 * User: jeckels
 * Date: 10/30/12
 */
public class PipelineJobLoggerGetter implements LoggerGetter       // Should not be serialized to avoid circular reference
{
    private final PipelineJob _job;

    public PipelineJobLoggerGetter(PipelineJob job)
    {
        _job = job;
    }

    @Override
    public Logger getLogger()
    {
        return _job.getLogger();
    }
}
