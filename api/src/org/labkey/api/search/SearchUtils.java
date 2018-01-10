/*
 * Copyright (c) 2010-2012 LabKey Corporation
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
package org.labkey.api.search;

import org.labkey.api.data.Container;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;

import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: Oct 12, 2010
 * Time: 2:19:15 PM
 */
public class SearchUtils
{
    public static HelpTopic getHelpTopic()
    {
        return new HelpTopic("luceneSearch");
    }

    public static void renderError(JspWriter out, String htmlMessage, boolean includeSpecialSymbolsMessage, boolean includeBooleanOperatorMessage, boolean includeMoreInformationMessage) throws IOException
    {
        out.write("<table>\n");
        out.write("  <tr><td><span class=\"labkey-error\">Error: " + htmlMessage + "</span></td></tr>\n");
        out.write("  <tr><td>&nbsp;</td></tr>\n");

        if (includeSpecialSymbolsMessage)
        {
            out.write("  <tr><td>These characters have special meaning within search queries: " + PageFlowUtil.filter(SPECIAL_SYMBOLS) + "\n");
            out.write("  <tr><td>&nbsp;</td></tr>\n");
            out.write("  <tr><td>You can escape special characters using \\ before the character or you can enclose the query string in double quotes.</td></tr>\n");
            out.write("  <tr><td>&nbsp;</td></tr>\n");
        }

        if (includeBooleanOperatorMessage)
        {
            out.write("  <tr><td>Boolean operators AND, OR, and NOT have special meaning within search queries.  To search for these words you can enclose the query string in double quotes.</td></tr>\n");
            out.write("  <tr><td>&nbsp;</td></tr>\n");
        }

        if (includeMoreInformationMessage)
        {
            out.write("  <tr><td>For more information, visit the " + getHelpTopic().getSimpleLinkHtml("search syntax documentation.") + "</td></tr>\n");
        }

        out.write("</table>\n");
    }

    public static class LuceneMessageParser
    {
        private static final Pattern p = Pattern.compile("Cannot parse '.*': Encountered \"(.*)\" at line (\\d+), column (\\d+).");

        private final boolean _parseable;
        private final String _encountered;
        private final int _line;
        private final int _column;

        public LuceneMessageParser(String message)
        {
            Matcher m = p.matcher(message);

            _parseable = m.lookingAt();
            _encountered = _parseable ? m.group(1) : null;
            _line = _parseable ? Integer.parseInt(m.group(2)) : -1;
            _column = _parseable ? Integer.parseInt(m.group(3)) : -1;
        }

        public boolean isParseable()
        {
            return _parseable;
        }

        public String getEncountered()
        {
            return _encountered;
        }

        public int getLine()
        {
            return _line;
        }

        public int getColumn()
        {
            return _column;
        }
    }

    public static String getStandardPrefix(String queryString)
    {
        return "Can't parse '" + queryString + "': ";
    }


    public static String getHighlightStyle()
    {
        return HIGHLIGHT_STYLE;
    }


    private static final String HIGHLIGHT_STYLE = "style=\"color: #126495;\"";  // "new blue" from kim... change to "highlight" style once we have one?
    public static final String SPECIAL_SYMBOLS = "+ - && || ! ( ) { } [ ] ^ \" ~ * ? : \\";
    public static final Set<String> SPECIAL_SYMBOLS_SET = PageFlowUtil.set(SPECIAL_SYMBOLS.split(" "));

    // Simple IOException that provides properly encoded HTML -- allows for links and formatting in the search error message.
    public static class HtmlParseException extends IOException
    {
        private final boolean _includesSpecialSymbol;
        private final boolean _includesBooleanOperator;

        // htmlMessage is HTML (e.g., all text is property filtered); query string is NOT HTML filtered
        public HtmlParseException(String htmlMessage, String queryString, int problemLocation)
        {
            super(getHtml(htmlMessage, queryString, problemLocation));
            _includesSpecialSymbol = has(queryString, SPECIAL_SYMBOLS_SET, false);
            _includesBooleanOperator = has(queryString, PageFlowUtil.set("AND", "NOT", "OR"), true);
        }

        private static String getHtml(String htmlMessage, String queryString, int problemLocation)
        {
            if (-1 == problemLocation)
            {
                return PageFlowUtil.filter(getStandardPrefix(queryString)) + htmlMessage;
            }
            else
            {
                StringBuilder sb = new StringBuilder();
                sb.append(PageFlowUtil.filter("Can't parse '"));

                for (int i = 0; i < queryString.length(); i++)
                {
                    if (problemLocation == i)
                        sb.append("<span ").append(HIGHLIGHT_STYLE).append(">");

                    sb.append(PageFlowUtil.filter(queryString.charAt(i)));

                    if (problemLocation == i)
                        sb.append("</span>");
                }

                sb.append(PageFlowUtil.filter("': "));
                sb.append(htmlMessage);

                return sb.toString();
            }
        }

        // Note: this is a case-sensitive search
        private boolean has(String queryString, Iterable<String> searchStrings, boolean wholeWordsOnly)
        {
            if (wholeWordsOnly)
            {
                Set<String> words = PageFlowUtil.set(queryString.split("\\s"));

                for (String searchString : searchStrings)
                    if (words.contains(searchString))
                        return true;
            }
            else
            {
                for (String searchString : searchStrings)
                    if (queryString.contains(searchString))
                        return true;
            }

            return false;
        }

        public boolean includesSpecialSymbol()
        {
            return _includesSpecialSymbol;
        }

        public boolean includesBooleanOperator()
        {
            return _includesBooleanOperator;
        }
    }

    public static String getPlaceholder(Container c)
    {
        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
        return "Search " + laf.getShortName();
    }
}
