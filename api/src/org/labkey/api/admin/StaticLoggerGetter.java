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
package org.labkey.api.admin;

import org.apache.log4j.Logger;

/**
 * Implementation used when an import/export context is used within an action or otherwise outside of a pipeline job.
 * In these cases, we can hold on the Logger directly because we don't need to be serialized.
 * User: jeckels
 * Date: 10/31/12
 */
public class StaticLoggerGetter implements LoggerGetter
{
    private final Logger _logger;

    public StaticLoggerGetter(Logger logger)
    {
        _logger = logger;
    }

    @Override
    public Logger getLogger()
    {
        return _logger;
    }
}
