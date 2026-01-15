/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.labkey.api.miniprofiler.MiniProfiler;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Cleaner;

/**
 * Verifies that close() was called at some point before finalization; logs an error and creation stack trace if not.
 * User: adam
 * Date: 7/2/12
 */

public class CheckedInputStream extends InputStreamWrapper
{
    private static final Cleaner CLEANER = Cleaner.create();

    private static class State implements Runnable
    {
        private final InputStream _is;
        private final StackTraceElement[] _creationStackTrace;
        private boolean _closed = false;

        private State(InputStream is, StackTraceElement[] creationStackTrace)
        {
            _is = is;
            _creationStackTrace = creationStackTrace;
        }

        @Override
        public void run()
        {
            if (!_closed)
            {
                LogManager.getLogger(CheckedInputStream.class).error("InputStream was not closed. Creation stacktrace:" + ExceptionUtil.renderStackTrace(_creationStackTrace));
                try
                {
                    _is.close();
                }
                catch (IOException e)
                {
                    LogManager.getLogger(CheckedInputStream.class).error("Failed to close InputStream", e);
                }
                finally
                {
                    _closed = true;
                }
            }
        }
    }

    private final Cleaner.Cleanable _cleanable;

    public CheckedInputStream(InputStream is)
    {
        super(is);
        StackTraceElement[] creationStackTrace = MiniProfiler.getTroubleshootingStackTrace();
        State state = new State(is, creationStackTrace);
        _cleanable = CLEANER.register(this, state);
    }

    @Override
    public void close() throws IOException
    {
        _cleanable.clean();
    }
}
