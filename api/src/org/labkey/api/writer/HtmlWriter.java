package org.labkey.api.writer;

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

    public Writer unwrap()
    {
        return _writer;
    }

    public void write(SafeToRender safeToRender)
    {
        try
        {
            _writer.write(safeToRender.toString());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void write(Number number) throws IOException
    {
        _writer.write(number.toString());
    }

    public void write(boolean b) throws IOException
    {
        _writer.write(Boolean.toString(b));
    }

    public void write(JSONObject json) throws IOException
    {
        _writer.write(json.toString());
    }

    public void write(JSONArray array) throws IOException
    {
        _writer.write(array.toString());
    }

    public void write(String s)
    {
        try
        {
            _writer.write(PageFlowUtil.filter(s));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
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

        public static AttributeValue of(String name, String value)
        {
            // TODO: eliminate "lk-" option after tests are migrated to use "data-" only
            if (!name.equals("class") && !name.startsWith("data-") && !name.startsWith("lk-"))
                throw new IllegalStateException("Illegal attribute name: " + name);

            return new AttributeValue(name, value);
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
