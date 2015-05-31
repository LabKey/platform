package org.labkey.api.reader;

import org.labkey.api.util.StringUtilsLabKey;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 *  Factory methods to create Readers, ensuring standard charater sets and buffering by default.
 *
 *  Created by adam on 5/30/2015.
 */
public class Readers
{
    public static Reader getUnbufferedReader(InputStream in)
    {
        return new InputStreamReader(in, StringUtilsLabKey.DEFAULT_CHARSET);
    }

    public static Reader getUnbufferedReader(File file) throws FileNotFoundException
    {
        return new InputStreamReader(new FileInputStream(file), StringUtilsLabKey.DEFAULT_CHARSET);
    }

    public static BufferedReader getReader(InputStream in)
    {
        return new BufferedReader(getUnbufferedReader(in));
    }

    public static BufferedReader getReader(File in) throws FileNotFoundException
    {
        return new BufferedReader(getUnbufferedReader(in));
    }
}
