/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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

import com.fasterxml.jackson.annotation.JsonValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.security.Crypt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Create GUID that looks like this f082cbda-b574-4e1e-9dba-b9e9b377f5b1.
 *
 *    CONSIDER: laying out the hex digits as described in rfc 4122
 *    UUID                   = time-low "-" time-mid "-"
 *                            time-high-and-version "-"
 *                            clock-seq-and-reserved
 *                            clock-seq-low "-" node
 *    time-low               = 4hexOctet
 *    time-mid               = 2hexOctet
 *    time-high-and-version  = 2hexOctet
 *    clock-seq-and-reserved = hexOctet
 *    clock-seq-low          = hexOctet
 *    node                   = 6hexOctet
 *    hexOctet               = hexDigit hexDigit
 *    hexDigit =
 *         "0" / "1" / "2" / "3" / "4" / "5" / "6" / "7" / "8" / "9" /
 *         "a" / "b" / "c" / "d" / "e" / "f" /
 *         "A" / "B" / "C" / "D" / "E" / "F"
 *
 *    The following is an example of the string representation of a UUID as
 *    a URN:
 *
 *    urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6
 */


@SuppressWarnings({"UnnecessarySemicolon"})
public class GUID implements Serializable, Parameter.JdbcParameterValue
{
    private static final int version  =       0x00001000;
    private static final int reserved =       0x00008000;
    private static final int clock_seq_mask = 0x00003fff;
    private static final int time_hi_mask =   0x00000fff;

    private static final Object lock = new Object();
    private static final Random rand = createRandom();
    private static final String clock_seq_and_node = genClockSeqAndReserved() + "-" + genNodeIdentifier();
    private static long msTimer = System.currentTimeMillis();
    private static int nanoCounter = 0xffffffff;


    private static String genClockSeqAndReserved()
    {
        int clock_seq_and_reserved = reserved | (rand.nextInt() & clock_seq_mask);
        return String.format("%04x", clock_seq_and_reserved);
    }


    private static String genNodeIdentifier()
    {
        //
        // Get unique string representing this machine
        //

        String netIdentity = networkIdentifier();

        //
        // get unique value representing this process
        //

        int pid = getPid();

        // preformat all this e.g. f082cbdab574
        String netDigest = Crypt.MD5.digest(netIdentity);
        pid = 0x0000ffff & (pid*Integer.valueOf(netDigest.substring(8,15),16));
        return String.format("%s%04x", netDigest.substring(0, 8), pid);
    }


    private static String networkIdentifier()
    {
        StringBuilder sbSource = new StringBuilder();
        Process p = null;

        try
        {
            ProcessBuilder cmd = new ProcessBuilder("ipconfig.exe", "/all");
            cmd.redirectErrorStream(true);
            p = cmd.start();
        }
        catch (Throwable t) {;}
        if (null == p)
        {
            try
            {
                ProcessBuilder cmd = new ProcessBuilder("ifconfig", "-a");
                cmd.redirectErrorStream(true);
                p = cmd.start();
            }
            catch (Throwable t) {;}
        }

        if (null != p)
        {
            try (InputStream str = p.getInputStream())
            {
                Pattern pattern = Pattern.compile(".*(\\p{XDigit}\\p{XDigit}(-|:)\\p{XDigit}\\p{XDigit}(-|:)\\p{XDigit}\\p{XDigit}(-|:)\\p{XDigit}\\p{XDigit}(-|:)\\p{XDigit}\\p{XDigit}(-|:)\\p{XDigit}\\p{XDigit}).*");
                BufferedReader in = new BufferedReader(new InputStreamReader(str));
                String line;
                while (null != (line = in.readLine()))
                {
                    Matcher m = pattern.matcher(line);
                    if (!m.find())
                        continue;
                    String mac = m.group(1);
                    sbSource.append(mac).append("\n");
                }
            }
            catch (IOException ignored)
            {
            }
            finally
            {
                p.destroy();
            }
        }

        if (0 == sbSource.length())
        {
            try
            {
                byte[] addr = InetAddress.getLocalHost().getAddress();
                sbSource.append((0x00ff & (int) addr[0])).append('.')
                        .append((0x00ff & (int) addr[1])).append('.')
                        .append((0x00ff & (int) addr[2])).append('.')
                        .append((0x00ff & (int) addr[3]));
            }
            catch (Throwable t)
            {
                ;
            }
        }

        if (0 == sbSource.length())
            sbSource.append(Long.toHexString(rand.nextLong()));

        return sbSource.toString();
    }

    private static int getPid()
    {
        int pid = rand.nextInt();
        try
        {
            String procName = ManagementFactory.getRuntimeMXBean().getName();
            int at=procName.indexOf('@');
            if (at > 0)
                pid = Integer.valueOf(procName.substring(0,at));
        }
        catch (Exception x)
        {
            ;
        }
        return pid;
    }


    public static String makeGUID()
    {
        long time;

        synchronized (lock)
        {
            nanoCounter = (nanoCounter + 1) % 10000;
            if (0 == nanoCounter)
                msTimer = Math.max(msTimer + 1, System.currentTimeMillis()); // technically should be relative to 15 October 1582
            time = msTimer * 10000 + nanoCounter;
        }

        long time_low = 0x0000ffffffffL & time;
        long time_mid = 0x00000000ffffL & (time >> 32);
        long time_hi_and_version = version | (time_hi_mask & (time>>48));

        String guid;
        guid = String.format("%08x-%04x-%04x-%s",
                time_low,
                time_mid,
                time_hi_and_version,
                clock_seq_and_node);
        return guid;
    }

    public static final String guidRegEx = "\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}";
    public static final Pattern guidPattern = Pattern.compile(guidRegEx);

    public static boolean isGUID(String s)
    {
        // quick check
        if (s.length() != 36 || s.charAt(8) != '-' || s.charAt(13) != '-' || s.charAt(18) != '-' || s.charAt(23) != '-')
            return false;
        return guidPattern.matcher(s).find();
    }


    public static String makeURN()
    {
        return "urn:uuid:" + makeGUID();
    }


    /**
     * return an unpredictable unique string
     */
    public static String makeHash()
    {
        return makeHash("");
    }

    public static String makeHash(String addl)
    {
        return Crypt.MD5.digest(makeGUID() + rand.nextLong() + addl);
    }


    private static Random createRandom()
    {
        SecureRandom s = new SecureRandom();
        s.setSeed(System.currentTimeMillis());
        s.setSeed(System.nanoTime());
        return s;
    }


    //
    // INSTANCE
    //
    private final String _str;

    public GUID()
    {
        _str = GUID.makeGUID();
    }

    public GUID(String str)
    {
        if (!isGUID(str))
            throw new IllegalArgumentException(str);
        _str = str;
    }

    @JsonValue
    @Override
    public String toString()
    {
        return _str;
    }

    @Override
    public boolean equals(Object o)
    {
        return (o instanceof GUID) && _str.equals(((GUID)o)._str);
    }

    @Override
    public int hashCode()
    {
        return _str.hashCode();
    }

    public String toStringNoDashes()
    {
        return _str.replace("-", "");
    }

    @Nullable
    @Override
    public Object getJdbcParameterValue()
    {
        return _str;
    }

    @NotNull
    @Override
    public JdbcType getJdbcParameterType()
    {
        return JdbcType.GUID;
    }
}

