package org.labkey.api.jsp.taglib;

import org.labkey.api.jsp.taglib.SimpleTagBase;
import org.apache.commons.lang.ObjectUtils;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.AbstractMap;

public class OptionsTag extends SimpleTagBase
{
    Map<?, String> _map;
    Object _value;
    public void doTag() throws JspException, IOException
    {
        JspWriter out = getOut();
        for (Map.Entry<?, String> option : _map.entrySet())
        {
            out.write("\n<option value=\"");
            out.write(h(option.getKey()));
            out.write("\"");
            if (ObjectUtils.equals(option.getKey(), _value))
            {
                out.write(" selected");
            }
            out.write(">");
            out.write(h(option.getValue()));
            out.write("</option>");
        }
    }

    public void setMap(Map<?, String> map)
    {
        _map = map;
    }

    public void setValue(Object value)
    {
        _value = value;
    }

}
