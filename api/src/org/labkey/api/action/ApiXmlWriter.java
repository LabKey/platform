/*
 * Copyright (c) 2013 LabKey Corporation
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
 * User: jeckels
 * Date: 4/22/13
 */
public class ApiXmlWriter extends ApiResponseWriter
{
    private static final String ARRAY_ELEMENT_NAME = "element";
    private static final String CONTENT_TYPE = "text/xml";
    private XMLStreamWriter _xmlWriter;

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

    public void writeObject(Object value) throws XMLStreamException, IOException
    {
        if (value == null || value.equals(null))
        {
            return;
        }

        try
        {
            if (value instanceof JSONString)
            {
                Object o = ((JSONString) value).toJSONString();
                if (o instanceof String)
                {
                    _xmlWriter.writeCharacters(o.toString());
                    return;
                }
            }
        }
        catch (Exception e)
        {
            /* forget about it */
        }
        if (value instanceof Number)
        {
            _xmlWriter.writeCharacters(JSONObject.numberToString((Number) value));
        }
        else if (value instanceof Boolean)
        {
            _xmlWriter.writeCharacters(value.toString());
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
            _xmlWriter.writeCharacters(DateUtil.formatJsonDateTime((Date) value));
        }
        else
        {
            _xmlWriter.writeCharacters(value.toString());
        }
    }

    private void writeJsonArray(JSONArray jsonArray) throws XMLStreamException, IOException
    {
        for (int i = 0; i < jsonArray.length(); i++)
        {
            _xmlWriter.writeStartElement(ARRAY_ELEMENT_NAME);
            writeObject(jsonArray.get(i));
            _xmlWriter.writeEndElement();
        }
    }


    protected void writeJsonObj(JSONObject obj) throws IOException
    {
        writeJsonObjInternal(obj);
        closeDocument();
    }

    protected void writeJsonObjInternal(JSONObject obj) throws IOException
    {
        try
        {
            for (Map.Entry<String, Object> entry : obj.entrySet())
            {
                _xmlWriter.writeStartElement(escapeElementName(entry.getKey()));
                writeObject(entry.getValue());
                _xmlWriter.writeEndElement();
            }
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
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
        assert _streamStack.size() == 1 : "called endResponse without a corresponding startResponse()!";
        closeDocument();
        _streamStack.pop();
    }

    private void closeDocument() throws IOException
    {
        try
        {
            _xmlWriter.writeEndElement();
            _xmlWriter.writeEndDocument();
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
        }
    }

    public void startMap(String name) throws IOException
    {
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
        StreamState state = _streamStack.peek();
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

    public void writeListEntry(Object entry) throws IOException
    {
        StreamState state = _streamStack.peek();
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
}
