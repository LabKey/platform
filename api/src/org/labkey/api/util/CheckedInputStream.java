/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * Verifies that close() was called at some point before finalization; logs an error and creation stack trace if not.
 * User: adam
 * Date: 7/2/12
 */

public class CheckedInputStream extends InputStreamWrapper
{
    private final StackTraceElement[] _creationStackTrace;
    private boolean _closed = false;

    public CheckedInputStream(InputStream is)
    {
        super(is);
        _creationStackTrace = Thread.currentThread().getStackTrace();
    }

    @Override
    public void close() throws IOException
    {
        _closed = true;
        super.close();
    }

    @Override
    protected void finalize() throws Throwable
    {
        if (!_closed)
        {
            Logger.getLogger(CheckedInputStream.class).error("InputStream was not closed. Creation stacktrace:" + ExceptionUtil.renderStackTrace(_creationStackTrace));
            super.close();
        }

        super.finalize();
    }
}
