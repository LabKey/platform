/*
 * Copyright (c) 2003-2009 Fred Hutchinson Cancer Research Center
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

import org.apache.log4j.Logger;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.*;
import java.util.*;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * A mime type map that implements the java.net.FileNameMap interface.
 * <p/>
 * copied from Tomcat, modified to read from mime.types
 */
public class MimeMap implements FileNameMap
{
    static Hashtable<String, MimeType> mimeTypeMap = new Hashtable<String, MimeType>(101);
    static Map<String,MimeType> extensionMap = new HashMap<String, MimeType>();
    
    private static class MimeType
    {
        private String _contentType;
        private boolean _inlineImage;

        public MimeType(String contentType)
        {
            this(contentType, false);
        }

        public MimeType(String contentType, boolean inlineImage)
        {
            _contentType = contentType;
            _inlineImage = inlineImage;
        }

        public String getContentType()
        {
            return _contentType;
        }

        public boolean isInlineImage()
        {
            return _inlineImage;
        }

        @Override
        public String toString()
        {
            return _contentType;
        }
    }

    static
    {
        mimeTypeMap.put("image/gif", new MimeType("image/gif", true));
        mimeTypeMap.put("image/jpeg", new MimeType("image/jpeg", true));
        mimeTypeMap.put("image/png", new MimeType("image/png", true));
        
        try
        {
            // tab delimited file is easier to edit in a spreadsheet
            InputStream is = MimeMap.class.getResourceAsStream("mime.txt");
            List<String> lines = IOUtils.readLines(is);
            for (String line : lines)
            {
                int tab = StringUtils.indexOfAny(line, "\t ");
                if (tab < 0) continue;
                String extn = StringUtils.trimToNull(line.substring(0,tab));
                String mimetype = StringUtils.trimToNull(line.substring(tab+1));
                if (null != extn && null != mimetype)
                {
                    MimeType mt = mimeTypeMap.get(mimetype);
                    if (null == mt)
                        mimeTypeMap.put(mimetype, (mt = new MimeType(mimetype)));
                    extensionMap.put(extn, mt);
                }
            }
            is.close();
        }
        catch (Exception e)
        {
            Logger.getInstance(MimeMap.class).error("unexpected error", e);
        }
    }


    private Hashtable<String, MimeType> map = new Hashtable<String, MimeType>();

    public void addContentType(String extn, String type)
    {
        addContentType(extn, type, false);
    }

    public void addContentType(String extn, String type, boolean inline)
    {
        map.put(extn, new MimeType(type.toLowerCase(), inline));
    }

    public Enumeration getExtensions()
    {
        return map.keys();
    }

    private MimeType getMimeType(String extn)
    {
        extn = extn.toLowerCase();
        MimeType type = map.get(extn);
        if (type == null)
            type = extensionMap.get(extn);
        return type;
    }

    public String getContentType(String extn)
    {
        MimeType type = getMimeType(extn);
        return type == null ? null : type.getContentType();
    }

    public void removeContentType(String extn)
    {
        map.remove(extn.toLowerCase());
    }

    /**
     * Get extension of file, without fragment id
     */
    public static String getExtension(String fileName)
    {
        // play it safe and get rid of any fragment id
        // that might be there
        int length = fileName.length();

        int newEnd = fileName.lastIndexOf('#');
        if (newEnd == -1) newEnd = length;
        // Instead of creating a new string.
        //         if (i != -1) {
        //             fileName = fileName.substring(0, i);
        //         }
        int i = fileName.lastIndexOf('.', newEnd);
        if (i != -1)
        {
            return fileName.substring(i + 1, newEnd);
        }
        else
        {
            // no extension, no content type
            return null;
        }
    }

    public boolean isInlineImage(String extn)
    {
        MimeType type = getMimeType(extn);
        return type != null && type.isInlineImage();
    }

    public boolean isInlineImageFor(String fileName)
    {
        String extn = getExtension(fileName);
        if (extn != null)
        {
            return isInlineImage(extn);
        }
        else
        {
            return false;
        }
    }


    public String getContentTypeFor(String fileName)
    {
        String extn = getExtension(fileName);
        if (extn != null)
        {
            return getContentType(extn);
        }
        else
        {
            // no extension, no content type
            return null;
        }
    }



     public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testMimeMap()
        {
            MimeMap m = new MimeMap();
            assertEquals(m.getContentTypeFor("photo.jpg"),"image/jpeg");
            assertEquals(m.getContentTypeFor("photo.jpeg"),"image/jpeg");
            assertEquals(m.getContentTypeFor("file.htm"), "text/html");
            assertEquals(m.getContentTypeFor("file.html"), "text/html");
            assertTrue(m.isInlineImageFor("photo.jpg"));
            assertFalse(m.isInlineImageFor("file.html"));
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
