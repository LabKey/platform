package org.labkey.api.jsp.taglib;

import org.labkey.api.action.SpringActionController;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.util.Set;

public class CheckboxTag extends SimpleTagBase
{
    protected String _name;
    protected Object _value;
    protected Set _checkedSet;

    public void doTag() throws JspException, IOException
    {
        JspWriter out = getOut();
        out.write("<input type=\"checkbox\" name=\"");
        out.write(h(_name));
        out.write("\" value=\"");
        out.write(h(_value));
        out.write("\"");
        if (_checkedSet != null)
        {
            if (_checkedSet.contains(_value))
            {
                out.write(" checked");
            }
        }
        out.write(">");
        out.write("<input type=\"hidden\" name=\"");
        out.write(SpringActionController.FIELD_MARKER);
        out.write(h(_name));
        out.write("\">");
    }

    public void setName(String name)
    {
        _name = name;
    }

    public void setValue(Object value)
    {
        _value = value;
    }

    public void setCheckedSet(Set set)
    {
        _checkedSet = set;
    }
}
