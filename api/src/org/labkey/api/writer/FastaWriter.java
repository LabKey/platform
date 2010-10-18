/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.writer;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * User: adam
 * Date: Aug 24, 2010
 * Time: 12:08:36 AM
 */
public class FastaWriter<E extends FastaEntry>
{
    private final FastaGenerator<E> _generator;

    public FastaWriter(FastaGenerator<E> generator)
    {
        _generator = generator;
    }

    // TODO: Create base class TextWriter that implements write() methods (TSVWriter should inherit as well)
    public void write(File file) throws IOException
    {
        write(new PrintWriter(new BufferedWriter(new FileWriter(file))));
    }

    public void write(HttpServletResponse response, String filename, boolean exportAsWebPage) throws IOException
    {
        // Flush any extraneous output (e.g., <CR><LF> from JSPs)
        response.reset();

        // Specify attachment and foil caching
        if (exportAsWebPage)
        {
           response.setHeader("Content-disposition", "inline; filename=\"" + filename + "\"");
        }
        else
        {
           // Set the content-type so the browser knows which application to launch
           response.setContentType(getContentType());
           response.setHeader("Content-disposition", "attachment; filename=\"" + filename + "\"");
        }

        // Write to the outputstream of the servlet (BTW, always get the outputstream AFTER you've
        // set the content-disposition and content-type)
        write(new PrintWriter(response.getOutputStream()));
    }

    // Always closes the PrintWriter
    private void write(PrintWriter pw)
    {
        try
        {
            while (_generator.hasNext())
            {
                E entry = _generator.next();
                writeEntry(pw, entry);
            }
        }
        finally
        {
            if (null != pw)
                pw.close();
        }
    }

    private String getContentType()
    {
        return "text/plain";
    }

    protected void writeEntry(PrintWriter pw, E entry)
    {
        pw.print(">");
        pw.println(entry.getHeader());
        pw.println(entry.getSequence());
    }
}
