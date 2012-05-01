/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.KeywordCandidates;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

    //create a compressed output from a file.  the name of the compressed file will the the input filename plus '.gz'
    public static File compressGzip (File input)
    {
        File output = new File(input.getPath() + ".gz");
        return compressGzip(input, output);
    }

    //create a compressed output file from the input file
    public static File compressGzip (File input, File output)
    {
        try
        {
            FileInputStream i = null;
            GZIPOutputStream o = null;

            try
            {
                i = new FileInputStream(input);
                o = new GZIPOutputStream(new FileOutputStream(output));
                FileUtil.copyData(i, o);
            }
            finally
            {
                if (i != null) try { i.close(); } catch (IOException e) {  }
                if (o != null) try { o.close(); } catch (IOException e) {  }
            }

            return output;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
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

    //create a compressed output file from the input file
    public static File decompressGzip (File input, File output)
    {
        try
        {
            GZIPInputStream i = null;
            FileOutputStream o = null;

            try
            {
                i = new GZIPInputStream(new FileInputStream(input));
                o = new FileOutputStream(output);
                FileUtil.copyData(i, o);
            }
            finally
            {
                if (i != null) try { i.close(); } catch (IOException e) {  }
                if (o != null) try { o.close(); } catch (IOException e) {  }
            }

            return output;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
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

        try
        {
            deflater.setInput(bytes);
            deflater.finish();

            while (!deflater.finished())
            {
                int count = deflater.deflate(buffer);
                bos.write(buffer, 0, count);
            }
        }
        finally
        {
            deflater.end();

            try
            {
                bos.close();
            }
            catch (IOException e)
            {
                // Ignore
            }
        }

        return bos.toByteArray();
    }


    // Decompress a byte array that was compressed using deflate().
    public static String inflate(byte[] source) throws DataFormatException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(source.length);
        byte[] buffer = new byte[source.length * 3];
        Inflater decompressor = new Inflater();

        try
        {
            decompressor.setInput(source, 0, source.length);

            while (!decompressor.finished())
            {
                int count = decompressor.inflate(buffer);
                bos.write(buffer, 0, count);
            }
        }
        finally
        {
            decompressor.end();

            try
            {
                bos.close();
            }
            catch (IOException e)
            {
                //
            }
        }

        return getString(bos);
    }


    // Compress a byte[] using simple run-length encoding... single byte count (0-255) followed by character to repeat.
    // Best for strings that include repeated sequences of characters.
    public static byte[] compressRle(String source, Algorithm algorithm)
    {
        return compressRle(getBytes(source), algorithm);
    }


    public enum Algorithm
    {
        // Compress using simple run-length encoding.  Bytes are read in pairs, with first byte the count (0-255) and
        // second byte the character to repeat.  This is appropriate for strings that include special characters (> 127).
        SimpleRle() {
            @Override
            void encode(ByteArrayOutputStream buf, byte b, int count)
            {
                buf.write(count);
                buf.write(b);
            }

            @Override
            void decode(ByteArrayOutputStream buf, byte[] bytes)
            {
                for (int i = 0; i < bytes.length; i += 2)
                {
                    int count = bytes[i] & 255;
                    byte b = bytes[i+1];

                    for (int j = 0; j < count; j++)
                        buf.write(b);
                }
            }
        },

        // Compress using run-length encoding optimized for strings comprised entirely of ASCII <= 127.  Compressed
        // strings are never larger than their input, even in perverse cases, since non-repeating characters are
        // represented in a single byte.  While reading:
        // - If current byte is >= 128, output the low 7 bits as a single character
        // - Otherwise, that byte plus the high bit of the following byte comprise the repeat count (0-255) of the low 7 bits of the following byte
        AsciiRle() {
            @Override
            void encode(ByteArrayOutputStream buf, byte b, int count)
            {
                if (b < 0)
                    throw new IllegalStateException("This compression algorithm does not support extended ASCII charcters (> 127)");

                if (1 == count)
                {
                    buf.write(b | 128);             // Single-character case; set the high bit
                }
                else
                {
                    buf.write(count & 127);         // First 7 bits of count
                    buf.write((count & 128) | b);   // High bit of count plus the character
                }
            }

            @Override
            void decode(ByteArrayOutputStream buf, byte[] bytes)
            {
                for (int i = 0; i < bytes.length; )
                {
                    byte first = bytes[i];

                    if (first < 0)
                    {
                        byte chr = (byte)(first & 127);
                        buf.write(chr);
                        i++;
                    }
                    else
                    {
                        byte chr = (byte)(bytes[i+1] & ((byte)127));
                        int count = first | (bytes[i+1] & 128);

                        for (int j = 0; j < count; j++)
                            buf.write(chr);

                        i += 2;
                    }
                }
            }
        };

        abstract void encode(ByteArrayOutputStream buf, byte b, int count);
        abstract void decode(ByteArrayOutputStream buf, byte[] bytes);
    }

    // Compress a byte[] using simple run-length encoding... see above.
    public static byte[] compressRle(byte[] bytes, Algorithm algorithm)
    {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        for (int i = 0; i < bytes.length; )
        {
            byte b = bytes[i];
            int count = 1;

            while (++i < bytes.length && b == bytes[i] && count < 255)
                count++;

            algorithm.encode(buf, b, count);
        }

        return buf.toByteArray();
    }


    // Decompress a byte[] that was compressed using simple run-length encoding.
    public static String decompressRle(byte[] bytes, Algorithm algorithm)
    {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        algorithm.decode(buf, bytes);

        return getString(buf);
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


    private static String getString(ByteArrayOutputStream bos)
    {
        try
        {
            return new String(bos.toByteArray(), "UTF-8");
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
            String stringWithSequences = "wwwwwwaaaaabbabcdezzzzzzz1234555555555";
            String stringWithLongSequences = StringUtils.repeat('a', 200) + "abcdefg" + StringUtils.repeat('z', 255);
            String dnaSequence = "IIIIE@EIIIHIIFFF<<EIB;;1116//-;;>>???;8<GIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIICCCCIIIIIIIIIIIIIIIIIIHHHHHHHIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIDDDFIFFHHIIHFFHHHIIIIIIIIIIIIIIIIIIIIIIIIIIIH??DDDDDDD;;>ADDDDDD;;;77//6:DDDDDDIIID=:99=40,,,,,,0069444489::<788999<77777<9::<<977777???777;>@";
            String specialCharacters = "this \u017D string \u0080 has \u0234 funny \u0837 characters";

            test(shortString, 1.14, 0.71, 1, 0.5);
            test(longString, 0.23, 0.23, 0.98, 0.5);
            test(reallyLongString, 0.062, 0.061, 0.98, 0.5);
            test(stringWithSequences, 0.55, 0.395, 0.368, 0.25);
            test(stringWithLongSequences, 0.0357, 0.0227, 0.0173, 0.0108);
            test(dnaSequence, 0.129, 0.118, 0.159, 0.122);
            test(specialCharacters, 0.825, 0.675, 1.1, -1);   // -1 indicates that AsciiRle compress should throw because of the special characters
        }

        private void test(String s, double gzipRatio, double deflateRatio, double rleSimpleRatio, double rleAsciiRatio) throws DataFormatException
        {
            byte[] gzip = compressGzip(s);
            test("gzip", s, gzip, decompressGzip(gzip), gzipRatio);

            byte[] deflate = deflate(s);
            test("deflate", s, deflate, inflate(deflate), deflateRatio);

            byte[] rleSimple = compressRle(s, Algorithm.SimpleRle);
            test("rle simple", s, rleSimple, decompressRle(rleSimple, Algorithm.SimpleRle), rleSimpleRatio);

            try
            {
                byte[] rleAscii = compressRle(s, Algorithm.AsciiRle);
                test("rle ascii", s, rleAscii, decompressRle(rleAscii, Algorithm.AsciiRle), rleAsciiRatio);
            }
            catch (IllegalStateException e)
            {
                assertTrue("Did not expect exception with positive target ratio", rleAsciiRatio < 0);
                assertEquals("This compression algorithm does not support extended ASCII charcters (> 127)", e.getMessage());
            }
        }

        private void test(String algorithm, String source, byte[] compressed, String decompressed, double targetRatio)
        {
            assertEquals(algorithm + " didn't roundtrip.", source, decompressed);
            double ratio = (double)compressed.length / (source.length() * 2);

            // Need to be within 1% of target ratio
            double diff = targetRatio - ratio;
            assertTrue(algorithm + " " + (diff > 0 ? "exceeded maximum" : "failed to meet minimum") + " compression ratio: expected " + targetRatio + " but was " + ratio, Math.abs(diff)/targetRatio < 0.01);
        }
    }
}
