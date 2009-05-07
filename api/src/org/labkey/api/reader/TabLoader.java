package org.labkey.api.reader;

import org.labkey.api.util.CloseableIterator;

import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.io.Reader;

/**
 * User: adam
 * Date: May 5, 2009
 * Time: 7:12:58 PM
 */
public class TabLoader extends AbstractTabLoader<Map<String, Object>>
{
    // Infer whether there are headers
    public TabLoader(File inputFile) throws IOException
    {
        super(inputFile, null);
    }

    public TabLoader(File inputFile, boolean hasColumnHeaders) throws IOException
    {
        super(inputFile, hasColumnHeaders);
    }

    public TabLoader(Reader reader, boolean hasColumnHeaders) throws IOException
    {
        super(reader, hasColumnHeaders);
    }

    // Infer whether there are headers
    public TabLoader(String src) throws IOException
    {
        super(src, null);
    }

    public TabLoader(String src, boolean hasColumnHeaders) throws IOException
    {
        super(src, hasColumnHeaders);
    }

    public CloseableIterator<Map<String, Object>> iterator()
    {
        return mapIterator();
    }
}
