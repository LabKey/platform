/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.mbean;

import org.apache.log4j.spi.LoggingEvent;

import java.util.Date;

/**
 * User: matthewb
 * Date: 2012-02-28
 * Time: 4:06 PM
 */
public interface ErrorsMXBean
{
    Date getTime();
    String getMessage();
    String getLevel();
    Error[] getErrors();
    void clear();

    public interface Error
    {
        Date getTime();
        String getMessage();
        String getThreadName();
        String getLevel();
        String getLoggerName();
    }
}