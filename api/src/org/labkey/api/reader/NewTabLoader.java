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
public class NewTabLoader extends AbstractTabLoader<Map<String, Object>>
{
    // Infer whether there are headers
    public NewTabLoader(File inputFile) throws IOException
    {
        setSource(inputFile);
    }

    public NewTabLoader(File inputFile, boolean hasColumnHeaders) throws IOException
    {
        setSource(inputFile);
        setHasColumnHeaders(hasColumnHeaders);
    }

    public NewTabLoader(Reader reader, boolean hasColumnHeaders)
    {
        setSource(reader);
        setHasColumnHeaders(hasColumnHeaders);
    }

    // Infer whether there are headers
    public NewTabLoader(String src)
    {
        setSource(src);
    }

    public NewTabLoader(String src, boolean hasColumnHeaders)
    {
        if (src == null)
            throw new IllegalArgumentException("src cannot be null");
        setHasColumnHeaders(hasColumnHeaders);
        setSource(src);
    }

    public NewTabLoader(File inputFile, int skipLines)
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
