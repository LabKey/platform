/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.parser.ParseError;
import org.jsoup.parser.ParseErrorList;
import org.jsoup.parser.Parser;
import org.jsoup.parser.XmlTreeBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Helpers for using jSoup to validate and tidy up user-supplied HTML.
 * User: kevink
 * Date: 6/21/12
 */
public class JSoupUtil
{
    @Nullable
    public static Document convertHtmlToDocument(final String html, final boolean asXML, final Collection<String> errors)
    {
        org.jsoup.nodes.Document doc = asXML ? parseXmlDOM(html, errors) : parseHtmlDOM(html, errors);
        return doc == null || doc.childrenSize() == 0 ? null : W3CDom.convert(doc);
    }

    // helper for script validation
    public static String convertHtmlToXml(String html, Collection<String> errors)
    {
        return tidyHTML(html, true, errors);
    }

    public static String tidyHTML(final String html, boolean asXML, final Collection<String> errors)
    {
        org.jsoup.nodes.Document doc = asXML ? parseXmlDOM(html, errors) : parseHtmlDOM(html, errors);
        return doc == null ? null : doc.toString();
    }

    public static String tidyXML(final String xml, final Collection<String> errors)
    {
        return tidyHTML(xml, true, errors);
    }


    private static org.jsoup.nodes.Document parseXmlDOM(String content, final Collection<String> errors)
    {
        if (StringUtils.isBlank(content))
            return null;

        Parser parser = new Parser(new XmlTreeBuilder());
        org.jsoup.nodes.Document result = parser.parseInput(content, "");
        translateErrors(parser.getErrors(), errors);
        return result;
    }

    private static org.jsoup.nodes.Document parseHtmlDOM(String content, final Collection<String> errors)
    {
        if (StringUtils.trimToNull(content) == null)
        {
            return null;
        }
        Parser parser = Parser.htmlParser();
        org.jsoup.nodes.Document result = Jsoup.parse(content, parser);
        translateErrors(parser.getErrors(), errors);
        return result;
    }

    private static void translateErrors(ParseErrorList in, Collection<String> out)
    {
        for (ParseError parseError : in)
        {
            out.add(parseError.toString());
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            // Use a read-only collection when we don't expect any errors
            Collection<String> errors = Collections.emptyList();

            // See #18726
            convertHtmlToDocument("", true, errors);
            convertHtmlToDocument("", false, errors);

            assertNull(tidyHTML("", true, errors));
            assertNull(tidyHTML("", false, errors));
            assertNull(tidyXML("", errors));

            tidyHTML("<!-- -->", true, errors);
            tidyHTML("<!-- -->", false, errors);
        }

        @Test
        public void testSanitize()
        {
            List<String> errors = new ArrayList<>();

            // Basic injection for "rel"
            assertEquals("<a href=\"http://labkey.com\" rel=\"noopener noreferrer\" target=\"_blank\">Huh</a>", PageFlowUtil.sanitizeHtml("<a href=\"http://labkey.com\" target= \"_blank\">Huh</a>", errors));
            assertEquals(0, errors.size());

            // Send in some mangled HTML
            assertEquals("<a rel=\"noopener noreferrer\" target=\"_blank\">Huh</a>", PageFlowUtil.sanitizeHtml("<a target=\"_blank\">Huh</b><p class=\"", errors));
            assertEquals(0, errors.size());

            String okShort = "<p><a href=\"http://google.com\" rel=\"noopener noreferrer\" target=\"_blank\">Test</a>";
            assertEquals(okShort, PageFlowUtil.sanitizeHtml(okShort, errors));
            assertEquals(0, errors.size());


            // Try something that's OK
            String okLong = """
                <p>test</p>\s
                <p><a href="http://google.com" rel="noopener noreferrer" target="_blank">Test</a>\s
                <a href="http://google.com" rel="noopener noreferrer" target="_blank">Test2</a>\s
                <a href="http://google.com" rel="noopener noreferrer" target="_blank">Test 3</a>\s
                <a href="http://google.com" rel="noopener noreferrer" target="_blank">Test 66</a>\s
                </p>\s
                """;
            assertEquals(okLong, PageFlowUtil.sanitizeHtml(okLong, errors));
            assertEquals(0, errors.size());
        }
    }
}
