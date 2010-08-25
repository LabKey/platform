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
