package org.labkey.api.jsp.taglib;

import org.labkey.api.jsp.taglib.SimpleTagBase;
import org.apache.commons.lang.ObjectUtils;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import java.io.IOException;

public class RadioTag extends SimpleTagBase
{
    protected String _name;
    protected Object _value;
    protected Object _currentValue;

    public void doTag() throws JspException, IOException
    {
        JspWriter out = getOut();
        out.write("<input type=\"radio\" name=\"");
        out.write(h(_name));
        out.write("\" value=\"");
        out.write(h(_value));
        out.write("\"");
        if (isChecked())
        {
            out.write(" checked");
        }
        out.write(">");
    }

    public void setName(String name)
    {
        _name = name;
    }
    public void setValue(Object value)
    {
        _value = value;
    }
    public void setCurrentValue(Object currentValue)
    {
        _currentValue = currentValue;
    }

    public boolean isChecked()
    {
        return ObjectUtils.equals(_value, _currentValue);
    }
}
