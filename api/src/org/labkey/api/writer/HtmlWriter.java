/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.api.writer;

import jakarta.servlet.ServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SafeToRender;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.stream.Collectors;

public class HtmlWriter implements Appendable
{
    private final Writer _writer;

    private HtmlWriter(Writer writer)
    {
        _writer = writer;
    }

    public static HtmlWriter of(Writer writer)
    {
        return new HtmlWriter(writer);
    }

    public static HtmlWriter of(ServletResponse response) throws IOException
    {
        return new HtmlWriter(response.getWriter());
    }

    public Writer unwrap()
    {
        return _writer;
    }

    public void write(SafeToRender safeToRender)
    {
        try
        {
            if (safeToRender instanceof DOM.Renderable r)
                r.appendTo(_writer);
            else
                _writer.write(safeToRender.toString());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void write(Number number)
    {
        try
        {
            _writer.write(number.toString());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void write(boolean b)
    {
        try
        {
            _writer.write(Boolean.toString(b));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void write(JSONObject json)
    {
        try
        {
            _writer.write(json.toString());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void write(JSONArray array)
    {
        try
        {
            _writer.write(array.toString());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    // Outputs an HTML-encoded version of the input string
    public void write(String s)
    {
        write(HtmlString.of(s));
    }

    // Outputs an HTML-encoded version of the input object
    public void write(Object o)
    {
        if (o instanceof DOM.Renderable r)
        {
            r.appendTo(this);
        }
        else
        {
            write(HtmlString.of(o));
        }
    }

    public static class AttributeValue
    {
        private final String _name;
        private final String _value;

        private AttributeValue(String name, String value)
        {
            _name = name;
            _value = value;
        }

        public static AttributeValue of(DOM.Attribute attribute, String value)
        {
            return new AttributeValue(attribute.name(), value);
        }

        public static AttributeValue cl(String className)
        {
            return new AttributeValue("class", className);
        }

        public static AttributeValue data(String name, String value)
        {
            return new AttributeValue("data-" + name, value);
        }

        private String name()
        {
            return _name;
        }

        private String value()
        {
            return _value;
        }
    }

    // Use DOM instead. This is useful only for methods that don't close their elements.
    public void writeElementStart(DOM.Element el, List<AttributeValue> attributes)
    {
        write(HtmlString.unsafe("<" + el.name() + (!attributes.isEmpty() ? " " +
            attributes.stream()
                .map(a -> a.name() +  "=\"" + PageFlowUtil.filter(a.value()) + "\"")
                .collect(Collectors.joining(" ")) : "") + ">"));
    }

    // Use DOM instead. This is useful only for methods that don't open their elements.
    public void writeElementEnd(DOM.Element el)
    {
        write(HtmlString.unsafe("</" + el.name() + ">"));
    }

    @Override
    public Appendable append(CharSequence csq) throws IOException
    {
        _writer.append(csq);
        return this;
    }

    @Override
    public Appendable append(CharSequence csq, int start, int end) throws IOException
    {
        _writer.append(csq, start, end);
        return this;
    }

    @Override
    public Appendable append(char c) throws IOException
    {
        _writer.append(c);
        return this;
    }
}
