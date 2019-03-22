/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.gwt.client.util;

import java.util.List;

/**
 * GWT client code is the only place we should be using these methods; use org.apache.commons.lang3.StringUtils instead.
 * This class will be removed in a future release of LabKey.
 *
 * User: Mark Igra
 * Date: Feb 15, 2007
 * Time: 10:20:35 PM
 */

@Deprecated
public class StringUtils
{
    public static boolean isEmpty(String str)
    {
        return null == str || str.length()==0;
    }
    
    public static String trimToNull(String str)
    {
        if (null == str)
            return str;

        str = str.trim();
        return str.length() == 0 ? null : str;
    }

    public static String trimToEmpty(String str)
    {
        if (null == str)
            return "";

        return str.trim();
    }

    public static boolean equals(String a, String b)
    {
        return (null == a && null == b) || (null != a && a.equals(b));
    }

    public static String join(List l, String join)
    {
        StringBuffer sb = new StringBuffer();
        String sep = "";
        for (int i = 0; i < l.size(); i++)
        {
            sb.append(sep);
            sb.append(l.get(i).toString());
            sep = join;
        }

        return sb.toString();
    }

    static public String filter(String s, boolean encodeSpace)
    {
        if (null == s || 0 == s.length())
            return "";

        int len = s.length();
        int i = 0;

        StringBuffer sb = new StringBuffer(2 * len);
        sb.append(s.substring(0, i));
        boolean newline = false;

        for (; i < len; ++i)
        {
            char c = s.charAt(i);

            //Character.isWhitespace() not supported in GWT
            if (c != '\t' && c != ' ')
                newline = false;
            if ('\r' == c || '\n' == c)
                newline = true;

            switch (c)
            {
                case '&':
                    sb.append("&amp;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '\n':
                    if (encodeSpace)
                        sb.append("<br>\n");
                    else
                        sb.append(c);
                    break;
                case '\t':
                    if (!encodeSpace)
                        sb.append(c);
                    else if (newline)
                        sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
                    else
                        sb.append("&nbsp; &nbsp; ");
                    break;
                case ' ':
                    if (encodeSpace && newline)
                        sb.append("&nbsp;");
                    else
                        sb.append(' ');
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }

        return sb.toString();
    }


    public static String filter(Object o)
    {
        return filter(o == null ? null : o.toString());
    }

    /**
     * HTML encode a string
     */
    public static String filter(String s)
    {
        return filter(s, false);
    }
}
