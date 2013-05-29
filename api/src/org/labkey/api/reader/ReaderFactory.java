package org.labkey.api.reader;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * User: adam
 * Date: 5/25/13
 * Time: 5:57 PM
 */
public interface ReaderFactory
{
    public BufferedReader getReader() throws IOException;
}
