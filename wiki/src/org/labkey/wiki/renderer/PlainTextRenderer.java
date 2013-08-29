/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.wiki.renderer;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.WikiRenderer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Tamra Myers
 * Date: Aug 16, 2006
 * Time: 12:33:01 PM
 */
public class PlainTextRenderer implements WikiRenderer
{

    private static final Pattern urlPatternStart = Pattern.compile("((http|https|ftp|mailto)://\\S+).*");

    /**
     * this does wiki-lite formatting.  recognize explicit urls, translate whitespace etc
     *
     * @param text
     */
    public FormattedHtml format(String text)
    {
        if (text == null)
            return new FormattedHtml("");

        StringBuilder sb = new StringBuilder();

        boolean translateSpace = true;
        String linkClassName = "link";
        boolean newline = false;

        for (int i = 0; i < text.length(); ++i)
        {
            char c = text.charAt(i);

            if (!Character.isWhitespace(c))
                newline = false;
            else if ('\r' == c || '\n' == c)
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
                    if (translateSpace)
                        sb.append("<br>\n");
                    else
                        sb.append(c);
                    break;
                case '\t':
                    if (!translateSpace)
                        sb.append(c);
                    else if (newline)
                        sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
                    else
                        sb.append("&nbsp; &nbsp; ");
                    break;
                case ' ':
                    if (translateSpace && newline)
                        sb.append("&nbsp;");
                    else
                        sb.append(' ');
                    break;
                case 'f':
                case 'h':
                case 'm':
                    String sub = text.substring(i);
                    if (c == 'f' && sub.startsWith("ftp://") ||
                            c == 'h' && (sub.startsWith("http://") || sub.startsWith("https://")) ||
                            c == 'm' && sub.startsWith("mailto://"))
                    {
                        Matcher m = urlPatternStart.matcher(sub);
                        if (m.find())
                        {
                            String href = m.group(1);
                            char endChar = href.charAt(href.length() - 1);
                            while (Character.getType(endChar) == Character.END_PUNCTUATION ||
                                    Character.getType(endChar) == Character.FINAL_QUOTE_PUNCTUATION ||
                                    Character.getType(endChar) == Character.OTHER_PUNCTUATION ||
                                    Character.getType(endChar) == Character.MATH_SYMBOL||
                                    Character.getType(endChar) == Character.CURRENCY_SYMBOL)
                            {
                                href = href.substring(0, href.length() - 1);
                                endChar = href.charAt(href.length() - 1);
                            }

                            i += href.length() - 1;
                            href = PageFlowUtil.filter(href);
                            sb.append("<a class=\"").append(linkClassName).append("\" href=\"").append(href).append("\">").append(href).append("</a>");
                            break;
                        }
                        // fall-through
                    }
                    sb.append(c);
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }

        return new FormattedHtml(sb.toString(), false);
    }
}
