/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

/**
 * Static methods for compressing and decompressing strings and byte arrays using different methods
 * User: adam
 * Date: 1/13/12
 */
public class Compress
{
    private Compress()
    {
    }


    /**
     * Compress a string using the GZIP algorithm.  GZIP is less efficient than DEFLATE (GZIP uses DEFLATE internally
     * but adds some overhead... which becomes negligible as the source grows in size) but is supported natively by most
     * browsers.  GZIP should usually be used when sending compressed content externally (over the internet, etc.).
     */
    public static byte[] compressGzip(String source)
    {
        return compressGzip(getBytes(source));
    }


    /**
     * Compress a byte[] using the GZIP algorithm
     */
    public static byte[] compressGzip(byte[] bytes)
    {
        // GZIPOutputStream must be closed before getting byte array, so nest the try-with-resources blocks
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream())
        {
            try (GZIPOutputStream zip = new GZIPOutputStream(buf))
            {
                zip.write(bytes);
            }

            return buf.toByteArray();
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    /** create a compressed output from a file.  the name of the compressed file will be the input filename plus '.gz' */
    public static File compressGzip(File input)
    {
        File output = new File(input.getPath() + ".gz");
        return compressGzip(input, output);
    }

    /** create a compressed output file from the input file */
    public static File compressGzip(File input, File output)
    {
        try (FileInputStream i = new FileInputStream(input); GZIPOutputStream o = new GZIPOutputStream(new FileOutputStream(output)))
        {
            FileUtil.copyData(i, o);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return output;
    }

    /** Decompress a byte array that was compressed using GZIP. */
    public static String decompressGzip(byte[] bytes)
    {
        try (GZIPInputStream is = new GZIPInputStream(new ByteArrayInputStream(bytes)); ByteArrayOutputStream buf = new ByteArrayOutputStream())
        {
            IOUtils.copy(is, buf);

            return buf.toString(StringUtilsLabKey.DEFAULT_CHARSET.name());
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    /** create a compressed output file from the input file */
    public static File decompressGzip(File input, File output)
    {
        try (GZIPInputStream i = new GZIPInputStream(new FileInputStream(input)); FileOutputStream o = new FileOutputStream(output))
        {
            FileUtil.copyData(i, o);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return output;
    }

    /**
     * Compress a string using the DEFLATE algorithm.  Using DEFLATE is more efficient than GZIP compression
     * (less overhead) but many common browsers don't accept this format directly.  Best for internal use.
     */
    public static byte[] deflate(String source)
    {
        return deflate(getBytes(source));
    }

    /** Compress a byte array using the DEFLATE algorithm (see above). */
    public static byte[] deflate(byte[] bytes)
    {
        byte[] buffer = new byte[bytes.length];
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length))
        {
            deflater.setInput(bytes);
            deflater.finish();

            while (!deflater.finished())
            {
                int count = deflater.deflate(buffer);
                bos.write(buffer, 0, count);
            }

            return bos.toByteArray();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            deflater.end();
        }
    }


    // Decompress a byte array that was compressed using deflate().
    public static String inflate(byte[] source) throws DataFormatException
    {
        byte[] buffer = new byte[source.length * 3];
        Inflater decompressor = new Inflater();

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(source.length))
        {
            decompressor.setInput(source, 0, source.length);

            while (!decompressor.finished())
            {
                int count = decompressor.inflate(buffer);
                bos.write(buffer, 0, count);
            }

            return getString(bos);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            decompressor.end();
        }
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
        return source.getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
    }


    private static String getString(ByteArrayOutputStream bos)
    {
        return new String(bos.toByteArray(), StringUtilsLabKey.DEFAULT_CHARSET);
    }


    public static class TestCase extends Assert
    {
        @Test
        public void test() throws DataFormatException
        {
            String shortString = "this is a test";
            String longString = "schema_name placing referencing external dynamic_function_code pctfree float transaction_active time containstable current_user regr_avgx asymmetric dlpreviouscopy regr_avgy role right else tinytext host pascal partial extract day_microsecond nocompress xmldeclaration bit oids sysdate master_heartbeat_period prefix temp lines each array dynamic unique xmlattributes syb_identity transform no_write_to_binlog token inline sqlexception xmlexists ignore member cobol g setuser dynamic_function session_user c keys a processexit checkpoint hour join m k analyze ref new t including day_second readtext current_default_transform_group sequence p library nfc having public zone nfd dlnewcopy change drop exception regr_count distributed lead insensitive bitvar derived updatetext initialize append execute infile translate_regex ilike double ntile day_minute uncommitted names column scope_name day large update raw indicator connect message_text specific_name distinctrow char_length granted atomic normalize following lag nonclustered name offset pad precision offsets assertion bom source xmlserialize freeze retrieve attributes lower routine_schema regr_sxx regr_sxy rank hex parameter_mode replication empty endtran reset purge coalesce element final increment hold regr_syy var_pop row session localtimestamp message_octet_length csv parameter_ordinal_position regexp binary trim_array preceding space encoding hierarchy datetime_interval_code listen analyse constraint_catalog restart rowcount stdin bigint data_pgs exclude unknown dbcc translate method force disk authorization contains float8 pli where type every float4 xmlroot revoke inherits datalink read_write family primary action fusion none utc_date cycle minus int8 scroll int3 int4 enum unnest depth int1 character_length int2 owned rows exp user_defined_type_code restore owner reconfigure scale xmlelement column_value rollback security unpivot while nfkd nfkc corresponding path returned_octet_length notnull compute outfile xmlvalidate vacuum command_function_code current_role xmlpi alias into modify x509 errorexit mlslabel database postfix plan implementation also nested_table_id matched nchar mirrorexit validate substring dlurlpathwrite year boolean procedural copy xmltable template returned_length static ordinality whitespace under unnamed always mediumblob optionally returned_sqlstate system lineno nesting dummy current_path max_cardinality use percentile_cont arch_store deferred errlvl absolute server_name fulltexttable immutable row_number varchar procedure sensitive localtime fortran delayed sqrt allocate varchar2 bulk alter assignment admin nocreateuser securityaudit key_member xmlquery cursor_name stddev_samp deref lateral percent synonym current_schema implicit escaped absent collate requiring identified varcharacter reserved_pgs values nclob datetime_interval_precision replica shared control normalized condition select indexes uri cost destructor end-exec optimize unpartition cast ceiling case modifies octet_length valid user_defined_type_name corr deferrable position_regex xmliterate initially overriding resignal serializable invoker utc_timestamp general tables immediate scope_schema usage smallint mediumtext ties longtext regr_r2 xmlforest specifictype noaudit nocreatedb syb_terminate first_value unlock dlvalue equals xmlconcat high_priority like not year_month start strict some translation range backward validator link goto mapping self escape according mirror revert delete parameter_specific_name percentile_disc starting end proc options module cache cascaded utc_time characteristics return substring_regex delimiter encrypted degree chain databases collation_schema writetext uid noholdlock xmlbinary undo transaction tinyblob rename enable preserve function namespace tablespace holdlock nosuperuser tsequal straight_join locator connection user_defined_type_catalog symmetric permission tran strip stable rowguidcol identity_insert match output cardinality parser characters call submultiset rollup temporary state attribute convert trigger_schema class_origin upper full isnull var_samp quote blocked superuser dlurlserver check current_catalog character_set_name array_agg greatest decimal sqlerror begin blob sqlca deallocate instead disconnect group user first low_priority storage nil using char_convert store transactions_committed passthrough hour_second columns privileges unbounded until free over references search trigger_catalog fs slow sysid dlurlcomplete once savepoint setof number header octets nologin zerofill view explain separator go before exclusive nowait pivot leave write current domain loop map do left shutdown catalog_name linear max integer xmlnamespaces lc_ctype bit_length operator yes xmlcast exists nothing successful configuration comments least fillfactor by long completion close constraint_schema any unencrypted parameter_name light key condition_number reassign db instantiable get and compress recursive current_date arith_overflow freetext diagnostics collect terminate trusted timezone_hour controlrow set existing routine sqlwarning statistics style transactions_rolled_back disable trim openxml all lc_collate schemas at tablesample as audit xmlschema base64 like_regex cascade off uescape multiset longblob clustered enclosed recipe no scope_catalog break of operation overlaps abs verbose on only identitycol import parameter_specific_schema move limit fetch structure character_set_schema or sublist numeric_truncation concurrently include excluding committed variadic then month comment ln plans interval descriptor sqlcode merge constraint mumps inout rownum syb_restree sql_big_result null backup similar spatial sequences true reindex unlisten sql within archive insert minute_second count last save second selective char location rowcnt integrity xmlcomment column_name prior maxextents level more partition when value subclass_origin sets current_transform_group_for_type minvalue returning trailing int used_pgs heavy inherit preorder relative statement flag overlay permanent intersect called release scope mediumint from add standalone collation online user_option id real if table_name accessible read dlurlcompleteonly between less constructor fulltext is covar_pop sql_calc_found_rows acl delimiters extend resource collation_catalog in print section ada nocreaterole local sqlstate found message_length raiserror nulls occurrences_regex schema defaults forward key_type option volatile system_user defined catalog mod constraints stripe declare content reads second_microsecond xmltext max_rows_per_page floor leading elseif load definer asensitive conversion desc ceil simple clob next data tinyint date discard perm timestamp document nullif whenever ignore_server_ids dlurlpathonly intersection noinherit replace rlike variable checked confirm respect abort to breadth both inner character_set_catalog openquery ssl createdb untyped after varying browse commit ordering connection_name login parameters instance routine_name text index timezone_minute opendatasource maxvalue xmlagg xml size input waitfor regr_intercept routine_catalog generated than freetexttable exec require foreign varbinary natural result stdout dump sum signal recheck repeat iterate command_function destroy errordata textsize deny dispatch top functions xor dlurlpath nth_value recovery width_bucket xmldocument collation_name server row_count handler out aggregate cursor numeric read_only for percent_rank dlurlcompletewrite unlink distinct infix minute_microsecond open are createuser wrapper grouping file initial describe false dlurlscheme others national table prepare without create exit asc truncate cube language trigger_name cross position nocheck offline version character mode dual identity dec returned_cardinality union length deterministic min trigger top_level_count nullable dictionary unsigned access transforms current_timestamp xmlparse terminated hour_minute master_ssl_verify_server_cert old grant kill show cume_dist password power returns avg work except middleint openrowset indent constraint_name global class bernoulli restrict parameter_specific_catalog default window createrole hour_microsecond dense_rank passing prepared filter treat day_hour regr_slope specific div object minute share stddev_pop parameter order isolation notify rule with last_value user_defined_type_schema covar_samp lock sql_small_result current_time outer continue repeatable cluster rowid";
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
