package org.labkey.api.jsp.taglib;

import org.labkey.api.util.PageFlowUtil;

import javax.servlet.jsp.tagext.SimpleTagSupport;
import javax.servlet.jsp.JspWriter;

public class SimpleTagBase extends SimpleTagSupport
{
    protected String h(Object o)
    {
        return PageFlowUtil.filter(o);
    }

    protected JspWriter getOut()
    {
        return getJspContext().getOut();
    }
}
