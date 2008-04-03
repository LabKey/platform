package org.labkey.api.util;

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

import java.io.PrintWriter;
import java.io.Writer;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Feb 9, 2006
 * Time: 4:16:04 PM
 * To change this template use File | Settings | File Templates.
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
