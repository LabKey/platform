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
        setSource(inputFile);
    }

    public TabLoader(File inputFile, boolean hasColumnHeaders) throws IOException
    {
        setSource(inputFile);
        setHasColumnHeaders(hasColumnHeaders);
    }

    public TabLoader(Reader reader, boolean hasColumnHeaders)
    {
        setSource(reader);
        setHasColumnHeaders(hasColumnHeaders);
    }

    // Infer whether there are headers
    public TabLoader(String src)
    {
        setSource(src);
    }

    public TabLoader(String src, boolean hasColumnHeaders)
    {
        if (src == null)
            throw new IllegalArgumentException("src cannot be null");
        setHasColumnHeaders(hasColumnHeaders);
        setSource(src);
    }

    public TabLoader(File inputFile, int skipLines)
            throws IOException
    {
        setSource(inputFile);
        _skipLines = skipLines;
    }

    public CloseableIterator<Map<String, Object>> iterator()
    {
        return mapIterator();
    }
}
