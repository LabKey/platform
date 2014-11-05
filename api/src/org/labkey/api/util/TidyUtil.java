/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.tidy.Tidy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: kevink
 * Date: 6/21/12
 */
public class TidyUtil
{
    private static final Pattern scriptPattern = Pattern.compile("(<script.*?>)(.*?)(</script>)", Pattern.CASE_INSENSITIVE| Pattern.DOTALL);

    public static Document convertHtmlToDocument(final String html, final boolean asXML, final Collection<String> errors)
    {
        Tidy tidy = configureHtmlTidy(asXML);
        return tidyParseDOM(tidy, html, errors);
    }

    public static String tidyHTML(final String html, boolean asXML, final Collection<String> errors)
    {
        Tidy tidy = configureHtmlTidy(asXML);
        return tidyParse(tidy, html, errors);
    }

    public static String tidyXML(final String xml, final Collection<String> errors)
    {
        Tidy tidy = configureXmlTidy();
        return tidyParse(tidy, xml, errors);
    }

    // helper for script validation
    public static String convertHtmlToXml(String html, Collection<String> errors)
    {
        return tidyHTML(html, true, errors);
    }

    private static Tidy configureHtmlTidy(final boolean asXML)
    {
        Tidy tidy = new Tidy();
        if (asXML)
            tidy.setXHTML(true);
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8");
        tidy.setOutputEncoding("UTF-8");
        tidy.setDropEmptyParas(false); // radeox wikis use <p/> -- don't remove them
        tidy.setTrimEmptyElements(false); // keeps tidy from converting <p/> to <br><br>

        return tidy;
    }

    private static Tidy configureXmlTidy()
    {
        Tidy tidy = new Tidy();
        tidy.setXmlOut(true);
        tidy.setXmlTags(true);
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8"); // utf8
        tidy.setOutputEncoding("UTF-8"); // utf8

        return tidy;
    }

    private static void tidyParse(final Tidy tidy, final InputStream is, final OutputStream os, final Collection<String> errors)
    {
        StringWriter err = new StringWriter();

        tidy.setErrout(new PrintWriter(err));
        try {
            tidy.parse(is, os);
        }
        catch (NullPointerException e)
        {
            errors.add("Tidy failed to parse html. Check that all html tags are valid and not malformed.");
        }
        tidy.getErrout().close();

        collectErrors(err, errors);
    }

    private static String tidyParse(final Tidy tidy, final String content, final Collection<String> errors)
    {
        try
        {
            // parse wants to use streams
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tidyParse(tidy, new ByteArrayInputStream(content.getBytes("UTF-8")), out, errors);

            return new String(out.toByteArray(), "UTF-8");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }

    private static Document tidyParseDOM(final Tidy tidy, final String content, final Collection<String> errors)
    {
        // TIDY does not properly parse the contents of script tags!
        // see bug 5007
        // CONSIDER: fix jtidy see ParserImpl$ParseScript
        Map<String,String> scriptMap = new HashMap<>();
        StringBuffer stripped = new StringBuffer(content.length());
        Matcher scriptMatcher = scriptPattern.matcher(content);
        int unique = content.hashCode();
        int count = 0;

        while (scriptMatcher.find())
        {
            count++;
            String key = "{{{" + unique + ":::" + count + "}}}";
            String match = scriptMatcher.group(2);
            scriptMap.put(key,match);
            scriptMatcher.appendReplacement(stripped, "$1" + key + "$3");
        }
        scriptMatcher.appendTail(stripped);

        StringWriter err = new StringWriter();
        try
        {
            // parse wants to use streams
            tidy.setErrout(new PrintWriter(err));

            String strippedString = stripped.toString().trim();

            if (strippedString.isEmpty())
                return null;

            Document doc = tidy.parseDOM(new ByteArrayInputStream(strippedString.getBytes("UTF-8")), null);

            // fix up scripts
            if (null != doc && null != doc.getDocumentElement())
            {
                NodeList nl = doc.getDocumentElement().getElementsByTagName("script");
                for (int i=0 ; i<nl.getLength() ; i++)
                {
                    Node script = nl.item(i);
                    NodeList childNodes = script.getChildNodes();
                    if (childNodes.getLength() != 1)
                        continue;
                    Node child = childNodes.item(0);
                    if (!(child instanceof CharacterData))
                        continue;
                    String contents = ((CharacterData)child).getData();
                    String replace = scriptMap.get(contents);
                    if (null == replace)
                        continue;
                    doc.createTextNode(replace);
                    script.removeChild(childNodes.item(0));
                    script.appendChild(doc.createTextNode(replace));
                }
            }

            tidy.getErrout().close();
            collectErrors(err, errors);

            return doc;
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }

    private static void collectErrors(final StringWriter err, final Collection<String> errors)
    {
        String errorString = err.toString();

        for (String error : errorString.split("\n"))
        {
            if (error.contains("Error:"))
                errors.add(error.trim());
        }

        // Provide a generic error when JTidy flips out and doesn't report the actual error
        String genericError = "This document has errors that must be fixed";

        if (errors.isEmpty() && errorString.contains(genericError))
            errors.add(genericError);
    }


    // TODO: Add many more test cases... jtidy behaves very badly
    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            Collection<String> errors = new LinkedList<>();

            // See #18726
            convertHtmlToDocument("", true, errors);
            convertHtmlToDocument("", false, errors);

            // TODO: Fix these cases
//            tidyHTML("", true, errors);
//            tidyHTML("", false, errors);
//            tidyXML("", errors);

            // TODO: I don't think these are fixable until we ditch jtidy, see #18725
//            tidyHTML("<!-- -->", true, errors);
//            tidyHTML("<!-- -->", false, errors);
        }
    }
}
