/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
import org.apache.log4j.Priority;

import java.io.PrintWriter;
import java.io.Writer;
import java.io.IOException;

/**
 * User: Matthew
 * Date: Feb 9, 2006
 * Time: 4:16:04 PM
 */
public class LogPrintWriter extends PrintWriter
{
    public LogPrintWriter(Logger log, Priority level)
    {
        super(new LogWriter(log, level));
    }

    private static class LogWriter extends Writer
    {
        Logger log;
        Priority level;
        String message = "";

        LogWriter(Logger log, Priority level)
        {
            this.log = log;
            this.level = level;
        }

        public void write(char cbuf[], int off, int len) throws IOException
        {
            message += new String(cbuf, off, len);
        }

        public void flush() throws IOException
        {
            if (message.length() > 0)
                log.log(level, message);
            message = "";
        }

        public void close() throws IOException
        {
            flush();
        }
    }
}
