/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;

/**
 * A mime type map that implements the java.net.FileNameMap interface.
 * <p/>
 * copied from Tomcat, modified to read from mime.types
 */
public class MimeMap implements FileNameMap
{

    public static Hashtable<String, MimeType> defaultMap = new Hashtable<String, MimeType>(101);

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
    }

    static
    {
        defaultMap.put("txt", new MimeType("text/plain"));
        defaultMap.put("log", new MimeType("text/plain"));
        defaultMap.put("html", new MimeType("text/html"));
        defaultMap.put("htm", new MimeType("text/html"));
        defaultMap.put("gif", new MimeType("image/gif", true));
        defaultMap.put("jpg", new MimeType("image/jpeg", true));
        defaultMap.put("jpe", new MimeType("image/jpeg", true));
        defaultMap.put("jpeg", new MimeType("image/jpeg", true));
        defaultMap.put("png", new MimeType("image/png", true));
        defaultMap.put("ico", new MimeType("image/vnd.microsoft.icon"));
        defaultMap.put("iqy", new MimeType("text/x-ms-iqy"));
        defaultMap.put("java", new MimeType("text/plain"));
        defaultMap.put("body", new MimeType("text/html"));
        defaultMap.put("rtx", new MimeType("text/richtext"));
        defaultMap.put("tsv", new MimeType("text/tab-separated-values"));
        defaultMap.put("etx", new MimeType("text/x-setext"));
        defaultMap.put("ps", new MimeType("application/x-postscript"));
        defaultMap.put("class", new MimeType("application/java"));
        defaultMap.put("csh", new MimeType("application/x-csh"));
        defaultMap.put("sh", new MimeType("application/x-sh"));
        defaultMap.put("tcl", new MimeType("application/x-tcl"));
        defaultMap.put("tex", new MimeType("application/x-tex"));
        defaultMap.put("texinfo", new MimeType("application/x-texinfo"));
        defaultMap.put("texi", new MimeType("application/x-texinfo"));
        defaultMap.put("t", new MimeType("application/x-troff"));
        defaultMap.put("tr", new MimeType("application/x-troff"));
        defaultMap.put("roff", new MimeType("application/x-troff"));
        defaultMap.put("man", new MimeType("application/x-troff-man"));
        defaultMap.put("me", new MimeType("application/x-troff-me"));
        defaultMap.put("ms", new MimeType("application/x-wais-source"));
        defaultMap.put("src", new MimeType("application/x-wais-source"));
        defaultMap.put("zip", new MimeType("application/zip"));
        defaultMap.put("bcpio", new MimeType("application/x-bcpio"));
        defaultMap.put("cpio", new MimeType("application/x-cpio"));
        defaultMap.put("gtar", new MimeType("application/x-gtar"));
        defaultMap.put("shar", new MimeType("application/x-shar"));
        defaultMap.put("sv4cpio", new MimeType("application/x-sv4cpio"));
        defaultMap.put("sv4crc", new MimeType("application/x-sv4crc"));
        defaultMap.put("tar", new MimeType("application/x-tar"));
        defaultMap.put("ustar", new MimeType("application/x-ustar"));
        defaultMap.put("dvi", new MimeType("application/x-dvi"));
        defaultMap.put("hdf", new MimeType("application/x-hdf"));
        defaultMap.put("latex", new MimeType("application/x-latex"));
        defaultMap.put("bin", new MimeType("application/octet-stream"));
        defaultMap.put("oda", new MimeType("application/oda"));
        defaultMap.put("pdf", new MimeType("application/pdf"));
        defaultMap.put("ps", new MimeType("application/postscript"));
        defaultMap.put("eps", new MimeType("application/postscript"));
        defaultMap.put("ai", new MimeType("application/postscript"));
        defaultMap.put("rtf", new MimeType("application/rtf"));
        defaultMap.put("nc", new MimeType("application/x-netcdf"));
        defaultMap.put("cdf", new MimeType("application/x-netcdf"));
        defaultMap.put("cer", new MimeType("application/x-x509-ca-cert"));
        defaultMap.put("exe", new MimeType("application/octet-stream"));
        defaultMap.put("gz", new MimeType("application/x-gzip"));
        defaultMap.put("Z", new MimeType("application/x-compress"));
        defaultMap.put("z", new MimeType("application/x-compress"));
        defaultMap.put("hqx", new MimeType("application/mac-binhex40"));
        defaultMap.put("mif", new MimeType("application/x-mif"));
        defaultMap.put("ief", new MimeType("image/ief"));
        defaultMap.put("tiff", new MimeType("image/tiff"));
        defaultMap.put("tif", new MimeType("image/tiff"));
        defaultMap.put("ras", new MimeType("image/x-cmu-raster"));
        defaultMap.put("pnm", new MimeType("image/x-portable-anymap"));
        defaultMap.put("pbm", new MimeType("image/x-portable-bitmap"));
        defaultMap.put("pgm", new MimeType("image/x-portable-graymap"));
        defaultMap.put("ppm", new MimeType("image/x-portable-pixmap"));
        defaultMap.put("rgb", new MimeType("image/x-rgb"));
        defaultMap.put("xbm", new MimeType("image/x-xbitmap"));
        defaultMap.put("xpm", new MimeType("image/x-xpixmap"));
        defaultMap.put("xwd", new MimeType("image/x-xwindowdump"));
        defaultMap.put("au", new MimeType("audio/basic"));
        defaultMap.put("snd", new MimeType("audio/basic"));
        defaultMap.put("aif", new MimeType("audio/x-aiff"));
        defaultMap.put("aiff", new MimeType("audio/x-aiff"));
        defaultMap.put("aifc", new MimeType("audio/x-aiff"));
        defaultMap.put("wav", new MimeType("audio/x-wav"));
        defaultMap.put("mpeg", new MimeType("video/mpeg"));
        defaultMap.put("mpg", new MimeType("video/mpeg"));
        defaultMap.put("mpe", new MimeType("video/mpeg"));
        defaultMap.put("qt", new MimeType("video/quicktime"));
        defaultMap.put("mov", new MimeType("video/quicktime"));
        defaultMap.put("avi", new MimeType("video/x-msvideo"));
        defaultMap.put("movie", new MimeType("video/x-sgi-movie"));
        defaultMap.put("avx", new MimeType("video/x-rad-screenplay"));
        defaultMap.put("wrl", new MimeType("x-world/x-vrml"));
        defaultMap.put("mpv2", new MimeType("video/mpeg2"));

        /* Add XML related MIMEs */

        defaultMap.put("xml", new MimeType("text/xml"));
        defaultMap.put("xsl", new MimeType("text/xml"));
        defaultMap.put("svg", new MimeType("image/svg+xml"));
        defaultMap.put("svgz", new MimeType("image/svg+xml"));
        defaultMap.put("wbmp", new MimeType("image/vnd.wap.wbmp"));
        defaultMap.put("wml", new MimeType("text/vnd.wap.wml"));
        defaultMap.put("wmlc", new MimeType("application/vnd.wap.wmlc"));
        defaultMap.put("wmls", new MimeType("text/vnd.wap.wmlscript"));
        defaultMap.put("wmlscriptc", new MimeType("application/vnd.wap.wmlscriptc"));
        defaultMap.put("mzxml", new MimeType("application/mzxml"));

        // mime.types
        try
        {
            ClassLoader cl = MimeMap.class.getClassLoader();
            InputStream is = cl.getResourceAsStream("org/labkey/api/util/mime.types");
            if (null == is)
                is = cl.getResourceAsStream("mime.types");
            BufferedReader in = new BufferedReader(new InputStreamReader(is));

            String s;
            while (null != (s = in.readLine()))
            {
                String[] a = s.split("\\s");
                if (a.length < 2) continue;
                for (int i = 1; i < a.length; i++)
                {
                    MimeType existingType = defaultMap.get(a[i]);
                    boolean inlineImage = existingType == null ? false : existingType.isInlineImage();
                    defaultMap.put(a[i], new MimeType(a[0], inlineImage));
                }
            }
        }
        catch (Exception x)
        {
            x.printStackTrace();
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
        MimeType type = map.get(extn.toLowerCase());
        if (type == null) type = defaultMap.get(extn.toLowerCase());
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

}
