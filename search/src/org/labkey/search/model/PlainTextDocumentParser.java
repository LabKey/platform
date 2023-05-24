package org.labkey.search.model;

import org.labkey.api.reader.Readers;
import org.labkey.api.search.AbstractDocumentParser;
import org.labkey.api.util.MimeMap;
import org.labkey.api.webdav.WebdavResource;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;

public class PlainTextDocumentParser extends AbstractDocumentParser
{
    @Override
    public String getMediaType()
    {
        return MimeMap.MimeType.PLAIN.getContentType();
    }

    @Override
    public boolean detect(WebdavResource resource, String contentType, byte[] buf)
    {
        return MimeMap.MimeType.PLAIN.getContentType().equals(contentType) || resource.getName().toLowerCase().endsWith(".txt");
    }

    @Override
    protected void parseContent(InputStream stream, ContentHandler handler) throws IOException, SAXException
    {
        char[] buf = new char[1000];
        BufferedReader reader = Readers.getReader(stream);
        int chars;

        do
        {
            chars = reader.read(buf);
            if (chars != -1)
                handler.characters(buf, 0, chars);
        }
        while (chars != -1);
    }
}
