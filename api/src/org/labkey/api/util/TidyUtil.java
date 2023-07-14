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
import org.jsoup.parser.Parser;
import org.jsoup.parser.XmlTreeBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.regex.Pattern;

/**
 * Helpers for using jSoup to validate and tidy up user-supplied HTML.
 * User: kevink
 * Date: 6/21/12
 */
public class TidyUtil
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

//
//    private static Tidy configureHtmlTidy(final boolean asXML)
//    {
//
//        Tidy tidy = new Tidy();
//        if (asXML)
//            tidy.setXHTML(true);
//        tidy.setShowWarnings(false);
//        tidy.setIndentContent(false);
//        tidy.setSmartIndent(false);
//        tidy.setInputEncoding("UTF-8");
//        tidy.setOutputEncoding("UTF-8");
//        tidy.setDropEmptyParas(false); // radeox wikis use <p/> -- don't remove them
//        tidy.setTrimEmptyElements(false); // keeps tidy from converting <p/> to <br><br>
//        tidy.setWraplen(0);
//        tidy.setWrapAttVals(false);
//
//        return tidy;
//    }
//
//    private static XmlTreeBuilder configureXmlTidy()
//    {
//        Tidy tidy = new Tidy();
//        tidy.setXmlOut(true);
//        tidy.setXmlTags(true);
//        tidy.setShowWarnings(false);
//        tidy.setIndentContent(false);
//        tidy.setSmartIndent(false);
//        tidy.setInputEncoding("UTF-8"); // utf8
//        tidy.setOutputEncoding("UTF-8"); // utf8
//
//        return tidy;
//    }

//    private static String tidyParse(final Tidy tidy, final String content, final Collection<String> errors)
//    {
//        StringWriter err = new StringWriter();
//        tidy.setErrout(new PrintWriter(err));
//        StringWriter out = new StringWriter();
//        try
//        {
//            tidy.parse(new StringReader(content), out);
//        }
//        catch (NullPointerException e)
//        {
//            errors.add("Tidy failed to parse html. Check that all html tags are valid and not malformed.");
//        }
//        tidy.getErrout().close();
//
//        collectErrors(err, errors);
//
//        return out.getBuffer().toString();
//    }

    private static org.jsoup.nodes.Document parseXmlDOM(String content, final Collection<String> errors)
    {
        if (StringUtils.isBlank(content))
            return null;

        Parser parser = new Parser(new XmlTreeBuilder());
        return parser.parseInput(content, "");
    }

    private static org.jsoup.nodes.Document parseHtmlDOM(String content, final Collection<String> errors)
    {
        return Jsoup.parse(content);
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
    }
}
