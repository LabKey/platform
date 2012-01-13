package org.labkey.api.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.KeywordCandidates;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

/**
 * User: adam
 * Date: 1/13/12
 * Time: 6:32 AM
 */

// Static methods for compressing and decompressing strings and byte arrays using different methods
public class Compress
{
    private Compress()
    {
    }


    // Compress a string using the GZIP algorithm.  GZIP is less efficient than DEFLATE (GZIP uses DEFLATE internally
    // but adds some overhead... which becomes negligible as the source grows in size) but is supported natively by most
    // browsers.  GZIP should usually be used when sending compressed content externally (over the internet, etc.).
    public static byte[] compressGzip(String source)
    {
        return compressGzip(getBytes(source));
    }


    // Compress a byte[] using the GZIP algorithm (see above).
    public static byte[] compressGzip(byte[] bytes)
    {
        try
        {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            GZIPOutputStream zip = new GZIPOutputStream(buf);
            zip.write(bytes);
            zip.close();
            return buf.toByteArray();
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }


    // Decompress a byte array that was compressed using GZIP.
    public static String decompressGzip(byte[] bytes)
    {
        try
        {
            GZIPInputStream is = new GZIPInputStream(new ByteArrayInputStream(bytes));
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            IOUtils.copy(is, buf);
            buf.close();

            return buf.toString("UTF-8");
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }


    // Compress byte array using the DEFLATE algorithm.  Using DEFLATE is more efficient than GZIP compression
    // (less overhead) but many common browsers don't accept this format directly.  Best for internal use.
    public static byte[] deflate(String source)
    {
        return deflate(getBytes(source));
    }


    // Compress a string using the DEFLATE algorithm (see above).
    public static byte[] deflate(byte[] bytes)
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length);
        byte[] buffer = new byte[bytes.length];

        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(bytes);
        deflater.finish();

        while (!deflater.finished())
        {
            int count = deflater.deflate(buffer);
            bos.write(buffer, 0, count);
        }

        try
        {
            bos.close();
        }
        catch (IOException e)
        {
            //
        }

        return bos.toByteArray();
    }


    // Decompress a byte array that was compressed using deflate().
    public static String inflate(byte[] source) throws DataFormatException
    {
        Inflater decompressor = new Inflater();
        decompressor.setInput(source, 0, source.length);

        ByteArrayOutputStream bos = new ByteArrayOutputStream(source.length);

        byte[] buffer = new byte[source.length * 3];

        while (!decompressor.finished())
        {
            int count = decompressor.inflate(buffer);
            bos.write(buffer, 0, count);
        }

        try
        {
            bos.close();
        }
        catch (IOException e)
        {
            //
        }

        return getString(bos.toByteArray());
    }


    private static byte[] getBytes(String source)
    {
        try
        {
            return source.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UTF-8 encoding not supported on this machine", e);
        }
    }


    private static String getString(byte[] source)
    {
        try
        {
            return new String(source, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UTF-8 encoding not supported on this machine", e);
        }
    }


    public static class TestCase extends Assert
    {
        @Test
        public void test() throws DataFormatException
        {
            String shortString = "this is a test";
            String longString = StringUtils.join(KeywordCandidates.get().getCandidates(), " ");
            String reallyLongString = StringUtils.repeat(longString, " ", 4);

            test(shortString, 1.2, 0.75);       // Not so efficient with a short string
            test(longString, 0.25, 0.25);       // Better with a longer string
            test(reallyLongString, 0.1, 0.1);   // Even better with repeated text
        }

        private void test(String s, double maxGzipRatio, double maxZlibRatio) throws DataFormatException
        {
            byte[] gzip = compressGzip(s);
            assertEquals(s, decompressGzip(gzip));
            double gzipRatio = (double)gzip.length / (s.length() * 2);
            assertTrue(gzipRatio < maxGzipRatio);

            byte[] deflate = deflate(s);
            assertEquals(s, inflate(deflate));
            double zlibRatio = (double)deflate.length / (s.length() * 2);
            assertTrue(zlibRatio < maxZlibRatio);
        }
    }
}
