package org.labkey.api.writer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * User: adam
 * Date: 1/10/2015
 * Time: 4:07 PM
 */

/**
 * PrintWriter that guarantees UTF-8 encoding of all characters, which the standard PrintWriter doesn't make very easy.
 */
public class UTF8PrintWriter extends PrintWriter
{
    public UTF8PrintWriter(OutputStream out)
    {
        super(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    public UTF8PrintWriter(File file) throws FileNotFoundException
    {
        super(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
    }
}
