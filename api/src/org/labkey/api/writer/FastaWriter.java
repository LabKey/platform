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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

/**
 * User: adam
 * Date: Aug 24, 2010
 * Time: 12:08:36 AM
 */
public class FastaWriter
{
    private final FastaGenerator _generator;

    public FastaWriter(FastaGenerator generator)
    {
        _generator = generator;
    }

    public void write(File file) throws IOException
    {
        PrintWriter pw = null;

        try
        {
            pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));

            while (_generator.hasNext())
            {
                FastaEntry entry = _generator.next();
                pw.print(">");
                pw.println(entry.getHeader());
                pw.println(entry.getSequence());
            }
        }
        finally
        {
            if (null != pw)
                pw.close();
        }
    }

    public interface FastaGenerator extends Iterator<FastaEntry>
    {
    }

    public interface FastaEntry
    {
        public String getHeader();
        public String getSequence();
    }
}
