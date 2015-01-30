package org.labkey.api.reader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Convenience class to create a Reader that guarantees UTF-8 encoding of all characters
 */
public class UTF8Reader extends InputStreamReader
{
    public UTF8Reader(InputStream in)
    {
        super(in, StandardCharsets.UTF_8);
    }

    public UTF8Reader(File file) throws FileNotFoundException
    {
        super(new FileInputStream(file), StandardCharsets.UTF_8);
    }
}
