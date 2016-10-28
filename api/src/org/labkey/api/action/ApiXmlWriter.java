/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.action;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONString;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.DateUtil;

import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Response writer that renders in XML.
 * User: jeckels
 * Date: 4/22/13
 */
public class ApiXmlWriter extends ApiResponseWriter
{
    private static final String ARRAY_ELEMENT_NAME = "element";
    public static final String CONTENT_TYPE = "text/xml";
    private XMLStreamWriter _xmlWriter;
    private boolean _closed = false;

    public ApiXmlWriter(HttpServletResponse response, String contentTypeOverride) throws IOException
    {
        super(response);
        XMLOutputFactory outFactory = XMLOutputFactory.newInstance();
        try
        {
            _xmlWriter = outFactory.createXMLStreamWriter(getWriter());
            _xmlWriter.writeStartDocument();
            _xmlWriter.writeStartElement("response");

            response.setContentType(null == contentTypeOverride ? CONTENT_TYPE : contentTypeOverride);
            response.setCharacterEncoding("utf-8");
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
        }
    }

    protected void writeObject(Object value) throws IOException
    {
        verifyOpen();
        if (value == null)
        {
            return;
        }

        try
        {
            if (value instanceof JSONString)
            {
                String s = ((JSONString) value).toJSONString();
                _xmlWriter.writeCharacters(filter(s));
                return;
            }
        }
        catch (Exception e)
        {
            /* forget about it */
        }

        try
        {
            if (value instanceof Number)
            {
                _xmlWriter.writeCharacters(filter(JSONObject.numberToString((Number) value)));
            }
            else if (value instanceof Boolean)
            {
                _xmlWriter.writeCharacters(filter(value.toString()));
            }
            else if (value instanceof JSONObject)
            {
                writeJsonObjInternal((JSONObject) value);
            }
            else if (value instanceof JSONArray)
            {
                writeJsonArray((JSONArray) value);
            }
            else if (value instanceof Map)
            {
                writeJsonObjInternal(new JSONObject((Map) value));
            }
            else if (value instanceof Collection)
            {
                writeJsonArray(new JSONArray((Collection) value));
            }
            else if (value.getClass().isArray())
            {
                writeJsonArray(new JSONArray(value));
            }
            else if (value instanceof Date)
            {
                _xmlWriter.writeCharacters(filter(DateUtil.formatJsonDateTime((Date) value)));
            }
            else
            {
                _xmlWriter.writeCharacters(filter(value.toString()));
            }
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
        }
    }

    private static String filter(String s)
    {
        if (s == null)
        {
            return s;
        }
        return s.replaceAll("[\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\u000B\u000C\u000E\u000F\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F\uFFFE\uFFFF]", "");
    }

    private void writeJsonArray(JSONArray jsonArray) throws XMLStreamException, IOException
    {
        verifyOpen();
        for (int i = 0; i < jsonArray.length(); i++)
        {
            _xmlWriter.writeStartElement(ARRAY_ELEMENT_NAME);
            writeObject(jsonArray.get(i));
            _xmlWriter.writeEndElement();
        }
    }


    @Override
    public void close() throws IOException
    {
        if (!_closed)
        {
            try
            {
                _xmlWriter.writeEndElement();
                _xmlWriter.writeEndDocument();
                _closed = true;
            }
            catch (XMLStreamException e)
            {
                throw new IOException(e);
            }
        }
    }

    protected void writeJsonObjInternal(JSONObject obj) throws IOException, XMLStreamException
    {
        verifyOpen();
        for (Map.Entry<String, Object> entry : obj.entrySet())
        {
            _xmlWriter.writeStartElement(escapeElementName(entry.getKey()));
            writeObject(entry.getValue());
            _xmlWriter.writeEndElement();
        }
    }


    public void startResponse() throws IOException
    {
        assert _streamStack.size() == 0 : "called startResponse() after response was already started!";
        //we always return an object at the top level
        _streamStack.push(new StreamState());
    }

    public void endResponse() throws IOException
    {
        verifyOpen();
        assert _streamStack.size() == 1 : "called endResponse without a corresponding startResponse()!";
        close();
        _streamStack.pop();
    }

    public void startMap(String name) throws IOException
    {
        verifyOpen();
        StreamState state = _streamStack.peek();
        assert (null != state) : "startResponse will start the root-level map!";
        try
        {
            _xmlWriter.writeStartElement(escapeElementName(name));
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
        }
        _streamStack.push(new StreamState(name, state.getLevel() + 1));
    }

    public void endMap() throws IOException
    {
        verifyOpen();
        try
        {
            _xmlWriter.writeEndElement();
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
        }
        _streamStack.pop();
    }

    public void writeProperty(String name, Object value) throws IOException
    {
        verifyOpen();
        try
        {
            _xmlWriter.writeStartElement(escapeElementName(name));
            writeObject(value);
            _xmlWriter.writeEndElement();
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
        }
    }

    public void startList(String name) throws IOException
    {
        verifyOpen();
        StreamState state = _streamStack.peek();
        try
        {
            _xmlWriter.writeStartElement(escapeElementName(name));
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
        }

        _streamStack.push(new StreamState(name, state.getLevel() + 1));
    }

    /** Replace characters that aren't valid in XML element names */
    private String escapeElementName(String s)
    {
        return s.replace('/', '_').replace('<', '_').replace('>', '_').replace('"', '_').replace('\'', '_');
    }

    public void endList() throws IOException
    {
        verifyOpen();
        try
        {
            _xmlWriter.writeEndElement();
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
        }
        _streamStack.pop();
    }

    private void verifyOpen()
    {
        if (_closed)
        {
            throw new IllegalStateException("Writer has already been closed");
        }
    }

    public void writeListEntry(Object entry) throws IOException
    {
        verifyOpen();
        try
        {
            _xmlWriter.writeStartElement(ARRAY_ELEMENT_NAME);
            writeObject(entry);
            _xmlWriter.writeEndElement();
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testFilter()
        {
            assertEquals("test", filter("test"));
            assertEquals("test", filter("test\u0015"));
            assertEquals("test", filter("\u0015test"));
            assertEquals("testtest", filter("test\u0015test"));
            assertEquals("testtest", filter("test\u0015\u0015test"));
            assertEquals("testtest", filter("\u0000test\u0015\u0015test\u0000"));
            assertEquals("", filter("\u0000"));
            assertEquals("\n\r", filter("\u0000\n\r"));
        }
    }
}
