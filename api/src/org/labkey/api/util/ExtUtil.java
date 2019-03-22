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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

/**
 * Utilities for dealing with ExtJS, largely around date and number format translation to/from Java.
 * User: matthew
 * Date: 1/15/12
 * Time: 5:44 PM
 */
public class ExtUtil
{
    public static String toExtDateFormatFn(String j)
    {
        String x = toExtDateFormat(j);
        if (null == x)
            return null;
        return "(Ext4.util.Format.dateRenderer(" + PageFlowUtil.jsString(x) + "))";
    }


    /** May return null, if no good translation is available */
    @Nullable
    public static String toExtDateFormat(String j)
    {
        StringBuilder x = new StringBuilder();
        boolean inQuotes = false;
        for (int i=0 ; i<j.length() ; )
        {
            char c = j.charAt(i++);

            if (inQuotes)
            {
                if (c != '\'')
                    x.append("\\").append(c);
                else if (i<j.length() && j.charAt(i)=='\'')
                    x.append(j.charAt(i++));
                else
                    inQuotes = false;
                continue;
            }
            if (c == '\'')
            {
                if (i<j.length() && j.charAt(i) == '\'')
                    x.append(j.charAt(i++));
                else
                    inQuotes = true;
                continue;
            }

            int count=1;
            for ( ; i<j.length() && c==j.charAt(i) ; i++)
                count++;
            switch(c)
            {
            case 'G':
                x.append("\\A\\D"); break;
            case 'y':
                x.append(count < 3 ? "y" : "Y"); break;
            case 'M':
                x.append(count < 2 ? "n" : count < 3 ? "m" : count<4 ? "M" : "F"); break;
            case 'w':
                x.append("W"); break;     // same starting index?
            case 'W':
                return null;
            case 'D':
                x.append('z'); break;    // same starting index?
            case 'd':
                x.append(count<2 ? "j" : "d"); break;
            case 'F':
                return null;            // Date of week in month???
            case 'E':
                x.append(count < 4 ? "D" : "l"); break;
            case 'a':
                x.append("A"); break;
            case 'H':
                x.append(count < 2 ? "G" : "H"); break;
            case 'k':
                return null;
            case 'K':
                return null;
            case 'h':
                 x.append(count < 2 ? "g" : "h"); break;
            case 'm':
                x.append("i"); break;
            case 's':
                x.append("s"); break;
            case 'S':
                rep(x, "u", count); break;
            case 'z': {}
                x.append("T"); break;
            case 'Z': {}
                x.append("O"); break;
            case '\\':
                rep(x,"\\\\",count); break;
            case ' ': case ':': case '.': case ',': case '-':
                rep(x, c, count); break;
            default:
                rep(x, "\\" + c, count); break;
            }
        }
        return x.toString();
    }


    private static void rep(StringBuilder x, String s, int count)
    {
        while (count-- > 0)
            x.append(s);
    }

    private static void rep(StringBuilder x, char c, int count)
    {
        while (count-- > 0)
            x.append(c);
    }


    /**
     * Could do a lot better here
     *   ) handle  negative formats
     *   ) handle chars before/after the number format
     */
    public static String toExtNumberFormatFn(String j)
    {
        if (-1 != j.indexOf(';'))
            j = j.substring(0,j.indexOf(';'));
        j = j.trim();
        boolean isPercentage = false;
        if (j.endsWith("%"))
        {
            isPercentage = true;
            j = j.substring(0,j.length()-1);
            if (j.length() == 0)
                j = "0";
        }
        String x = toExtNumberFormat(j);

        if (isPercentage)
        {
            return "((function(){" +
                    "var fmt = Ext4.util.Format.numberRenderer(" + PageFlowUtil.jsString(x) + "); return function(d){return fmt(d*100) + '%';};" +
                    "})())";
        }
        else
            return "(Ext4.util.Format.numberRenderer(" + PageFlowUtil.jsString(x) + "))";
    }


    /**
     * best attempt Java number format -> Ext number format.  Does not handle negative see toExtNumberFormatFn()
     * Java has a lot more options...
     */
    @NotNull
    public static String toExtNumberFormat(String j)
    {
        StringBuilder sb = new StringBuilder();
        for (int i=0 ; i<j.length() ; i++)
        {
            char c = j.charAt(i);
            switch (c)
            {
                case '0':
                case '#':
                    sb.append('0'); break;
                case '.':
                case ',':
                    sb.append(c); break;
                default:
                    break;
            }
        }
        return sb.toString();
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testDate()
        {
            assertEquals("Y-m-d", toExtDateFormat("yyyy-MM-dd"));
            assertEquals("Y.m.d \\A\\D \\a\\t H:i:s T", toExtDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z"));     //	2001.07.04 AD at 12:08:56 PDT
            assertEquals("D, M j, 'y", toExtDateFormat("EEE, MMM d, ''yy"));                            //	Wed, Jul 4, '01
            assertEquals("g:i A", toExtDateFormat("h:mm a"));                                           //	12:08 PM
            assertEquals("h \\o'\\c\\l\\o\\c\\k A, T", toExtDateFormat("hh 'o''clock' a, zzzz"));       //	12 o'clock PM, Pacific Daylight Time
            assertEquals("g:i A, T", toExtDateFormat("h:mm a, z"));                                     //	0:08 PM, PDT
            assertEquals("Y.F.d \\A\\D h:i A", toExtDateFormat("yyyyy.MMMMM.dd GGG hh:mm aaa"));        // 02001.July.04 AD 12:08 PM
            assertEquals("D, j M Y H:i:s O", toExtDateFormat("EEE, d MMM yyyy HH:mm:ss Z"));            //	Wed, 4 Jul 2001 12:08:56 -0700
            assertEquals("ymdHisO", toExtDateFormat("yyMMddHHmmssZ"));                                  //	010704120856-0700
        }


        @Test
        public void testNumber()
        {
            assertEquals("0", toExtNumberFormat("#"));
            assertEquals("0.00", toExtNumberFormat("#.00"));
            assertEquals("0.0000", toExtNumberFormat("0.0000"));
            assertEquals("0,000", toExtNumberFormat("#,##0"));
            assertEquals("0,000.00", toExtNumberFormat("#,##0.00"));
            // TODO
            //    negative number formats
            //    characters
            // e.g. #,000.00;(#,000.00)
        }
    }
}


