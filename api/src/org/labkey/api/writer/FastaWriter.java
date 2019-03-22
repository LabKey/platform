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

import org.labkey.api.data.TextWriter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * User: adam
 * Date: Aug 24, 2010
 * Time: 12:08:36 AM
 */
public class FastaWriter<E extends FastaEntry> extends TextWriter
{
    private final FastaGenerator<E> _generator;
    private String _filename;

    public FastaWriter(FastaGenerator<E> generator)
    {
        _generator = generator;
    }

    @Override
    protected String getFilename()
    {
        return _filename;
    }

    public void write(HttpServletResponse response, String filename) throws IOException
    {
        _filename = filename;
        write(response);
    }

    // Always closes the PrintWriter
    public void write()
    {
        while (_generator.hasNext())
        {
            E entry = _generator.next();
            writeEntry(_pw, entry);
        }
    }

    protected void writeEntry(PrintWriter pw, E entry)
    {
        pw.print(">");
        pw.println(entry.getHeader());
        pw.println(entry.getSequence());
    }
}
