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
    private final FastaEntryIterator _iterator;

    public FastaWriter(FastaEntryIterator iterator)
    {
        _iterator = iterator;
    }

    public void write(File file) throws IOException
    {
        PrintWriter pw = null;

        try
        {
            pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));

            while (_iterator.hasNext())
            {
                FastaEntry entry = _iterator.next();
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

    public interface FastaEntryIterator extends Iterator<FastaEntry>
    {
    }

    public interface FastaEntry
    {
        public String getHeader();
        public String getSequence();
    }
}
