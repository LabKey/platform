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

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Base class for DocumentParser implementations that helps assemble XHTML content from the underlying document content. 
 * User: jeckels
 * Date: Jul 14, 2010
 */
public abstract class AbstractDocumentParser implements SearchService.DocumentParser
{
    private static final String XHTML_URI = "http://www.w3.org/1999/xhtml";

    /** Start a new XHTML element */
    protected void startTag(ContentHandler h, String tag) throws SAXException
    {
        h.startElement(XHTML_URI, tag, tag, EMPTY_ATTRIBUTES);
    }

    /** Closes an already started XHTML element */
    protected void endTag(ContentHandler h, String tag) throws SAXException
    {
        h.endElement(XHTML_URI, tag, tag);
    }

    /** Writes character data to the XHTML stream */
    protected void write(ContentHandler h, String text) throws SAXException
    {
        h.characters(text.toCharArray(), 0, text.length());
    }

    /** Writes character data to the XHTML stream */
    protected void write(ContentHandler h, StringBuilder sb, char[] buf) throws SAXException
    {
        int len = Math.min(sb.length(),buf.length);
        sb.getChars(0, len, buf, 0);
        h.characters(buf,0,len);
    }

    protected void newline(ContentHandler h) throws SAXException
    {
        h.characters(NL, 0, NL.length);
    }

    protected void tab(ContentHandler h) throws SAXException
    {
        h.characters(TAB, 0, TAB.length);
    }

    private static final char[] NL = new char[] { '\n' };
    private static final char[] TAB = new char[] { '\t' };

    /**
     * Creates the structure of a basic XHTML document to pass to Lucene, and then calls parseContent to let
     * subclasses generate the real content
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler) throws IOException, SAXException
    {
        startTag(handler, "html");
        startTag(handler, "body");

        try
        {
            parseContent(stream, handler);
        }
        catch (ParseFinishedException e) {}

        endTag(handler,"body");
        endTag(handler,"html");
    }

    protected abstract void parseContent(InputStream stream, ContentHandler handler) throws IOException, SAXException;

    /** Indicates that the parsing can finish - we've already consumed all of the interesting parts of the file */
    public static class ParseFinishedException extends SAXException {}

    private static final Attributes EMPTY_ATTRIBUTES = new Attributes()
    {
        public int getLength()
        {
            return 0;
        }

        public String getURI(int i)
        {
            return null;
        }

        public String getLocalName(int i)
        {
            return null;
        }

        public String getQName(int i)
        {
            return null;
        }

        public String getType(int i)
        {
            return null;
        }

        public String getValue(int i)
        {
            return null;
        }

        public int getIndex(String s, String s1)
        {
            return 0;
        }

        public int getIndex(String s)
        {
            return 0;
        }

        public String getType(String s, String s1)
        {
            return null;
        }

        public String getType(String s)
        {
            return null;
        }

        public String getValue(String s, String s1)
        {
            return null;
        }

        public String getValue(String s)
        {
            return null;
        }
    };
}
