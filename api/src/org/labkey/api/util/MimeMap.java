/*
 * Copyright (c) 2003-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.logging.LogHelper;

import java.io.File;
import java.io.InputStream;
import java.net.FileNameMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A mime type map that implements the java.net.FileNameMap interface.
 * Copied from Tomcat, modified to read from mime.txt, loaded as a classloader resource.
 */
public class MimeMap implements FileNameMap
{
    private static final Map<String, MimeType> mimeTypeMap = new HashMap<>(101);
    private static final Map<String, MimeType> extensionMap = new HashMap<>();

    public static final MimeMap DEFAULT = new MimeMap();

    public static class MimeType
    {
        private final String _contentType;
        private final boolean _inline;

        public MimeType(String contentType)
        {
            this(contentType, false);
        }

        public MimeType(String contentType, boolean inline)
        {
            _contentType = contentType;
            _inline = inline;
        }

        public String getContentType()
        {
            return _contentType;
        }

        // e.g. is displayable by all browsers
        public boolean canInline()
        {
            return _inline;
        }

        public boolean isImage()
        {
            return _contentType.startsWith("image/");
        }

        public boolean isInlineImage()
        {
            return canInline() && isImage();
        }

        @Override
        public String toString()
        {
            return _contentType;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MimeType mimeType = (MimeType) o;

            if (_inline != mimeType._inline) return false;
            return _contentType != null ? _contentType.equals(mimeType._contentType) : mimeType._contentType == null;

        }

        @Override
        public int hashCode()
        {
            int result = _contentType != null ? _contentType.hashCode() : 0;
            result = 31 * result + (_inline ? 1 : 0);
            return result;
        }

        // Constants for a few common mime types. We could add a lot more. If you add/remove constants then modify the list below.
        // And if you can find a more official MimeType enum or constant list in one of our libraries then by all means get rid of these.
        public static final MimeType GIF = new MimeType("image/gif", true);
        public static final MimeType JPEG = new MimeType("image/jpeg", true);
        public static final MimeType PDF = new MimeType("application/pdf", true);
        public static final MimeType PNG = new MimeType("image/png", true);
        public static final MimeType SVG = new MimeType("image/svg+xml", true);

        public static final MimeType HTML = new MimeType("text/html");
        public static final MimeType PLAIN = new MimeType("text/plain");
        public static final MimeType XML = new MimeType("text/xml");
    }

    static
    {
        for (MimeType mt : Arrays.asList(MimeType.GIF, MimeType.JPEG, MimeType.PDF, MimeType.PNG, MimeType.SVG, MimeType.HTML, MimeType.PLAIN, MimeType.XML))
        {
            mimeTypeMap.put(mt.getContentType(), mt);
        }

        try (InputStream is = MimeMap.class.getResourceAsStream("mime.txt"))
        {
            // tab delimited file is easier to edit in a spreadsheet
            List<String> lines = IOUtils.readLines(is);
            for (String line : lines)
            {
                int tab = StringUtils.indexOfAny(line, "\t ");
                if (tab < 0) continue;
                String extn = StringUtils.trimToNull(line.substring(0,tab));
                String mimeType = StringUtils.trimToNull(line.substring(tab+1));
                if (null != extn && null != mimeType)
                {
                    MimeType mt = mimeTypeMap.computeIfAbsent(mimeType, MimeType::new);
                    extensionMap.put(extn, mt);
                }
            }
        }
        catch (Exception e)
        {
            LogHelper.getLogger(MimeMap.class, "Resolves file names and extensions to MIME types").error("Failed loading MIME types", e);
        }
    }

    public MimeType getMimeType(String extn)
    {
        extn = extn.toLowerCase();
        return extensionMap.get(extn);
    }

    public boolean canInlineFor(String filename)
    {
        MimeType mime = getMimeTypeFor(filename);
        return mime != null && mime.canInline() && mime != MimeMap.MimeType.HTML;
    }


    @Nullable
    public MimeType getMimeTypeFor(String fileName)
    {
        String extn = FileUtil.getExtension(fileName);
        if (null == extn)
            return null;
        return getMimeType(extn);
    }


    public String getContentType(String extn)
    {
        MimeType type = getMimeType(extn);
        return type == null ? null : type.getContentType();
    }

    public boolean isInlineImage(String extn)
    {
        MimeType type = getMimeType(extn);
        return type != null && type.isInlineImage();
    }


    public boolean isInlineImageFor(String fileName)
    {
        String extn = FileUtil.getExtension(fileName);
        if (extn != null)
        {
            return isInlineImage(extn);
        }
        else
        {
            return false;
        }
    }

    public boolean isInlineImageFor(@NotNull File file)
    {
        String extn = FileUtil.getExtension(file);
        if (extn != null)
        {
            return isInlineImage(extn);
        }
        else
        {
            return false;
        }
    }


    public boolean isOfficeDocument(String extn)
    {
        MimeType mimeType = getMimeType(extn);
        if (null == mimeType)
            return false;
        String contentType = mimeType.getContentType();
        if (!contentType.startsWith("application/"))
            return false;
        String subType = contentType.substring("application/".length());
        return (subType.startsWith("vnd.openxmlformats-officedocument") || subType.startsWith("vnd.ms") || subType.startsWith("ms") || subType.equals("x-excel"));
    }


    public boolean isOfficeDocumentFor(String fileName)
    {
        String extn = FileUtil.getExtension(fileName);
        if (extn != null)
        {
            return isOfficeDocument(extn);
        }
        else
        {
            return false;
        }
    }


    @Override
    public String getContentTypeFor(String fileName)
    {
        String extn = FileUtil.getExtension(fileName);
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



    public static class TestCase extends Assert
    {
        @Test
        public void testMimeMap()
        {
            MimeMap m = new MimeMap();

            MimeType h = new MimeType("text/html");
            assertEquals(h,m.getMimeType("html"));
            assertEquals(h.hashCode(), m.getMimeType("html").hashCode());

            assertEquals("image/jpeg", m.getContentTypeFor("photo.jpg"));
            assertEquals("image/jpeg", m.getContentTypeFor("photo.jpeg"));
            assertEquals("text/html", m.getContentTypeFor("file.htm"));
            assertEquals("text/html", m.getContentTypeFor("file.html"));

            assertTrue(m.isInlineImageFor("photo.jpg"));
            assertTrue(m.isInlineImageFor("photo.svg"));
            assertFalse(m.isInlineImageFor("file.html"));
            MimeType pdf = m.getMimeType("pdf");
            assertTrue(pdf.canInline());
            assertFalse(pdf.isInlineImage());

            assertTrue(m.isOfficeDocument("doc"));
            assertTrue(m.isOfficeDocument("docx"));
            assertTrue(m.isOfficeDocument("xls"));
            assertTrue(m.isOfficeDocument("xlsx"));
            assertTrue(m.isOfficeDocument("ppt"));
            assertTrue(m.isOfficeDocument("pptx"));
        }
    }
}
