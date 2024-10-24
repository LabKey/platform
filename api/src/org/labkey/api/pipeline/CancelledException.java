/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.apache.logging.log4j.LoggingException;

/**
 * Indicates that a pipeline job has been cancelled and no further work should be done for it.
 *
 * Extend LoggingException so that Log4J lets it through. Otherwise, the exception can be suppressed in
 * AbstractLogger.handleLogMessageException() when it happens in the context of a call to PipelineJob.error() or fatal()
 */
public class CancelledException extends LoggingException
{
    public CancelledException()
    {
        super((String)null);
    }
}
