package org.labkey.api.jsp.taglib;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

public class PanelTag extends BodyTagSupport
{
    private String className = null;
    private String id = null;

    public String getClassName()
    {
        return className;
    }

    public void setClassName(String className)
    {
        this.className = className;
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public void setId(String id)
    {
        this.id = id;
    }

    public int doStartTag() throws JspException
    {
        StringBuilder sb = new StringBuilder();

        sb.append("<div class=\"panel panel-default");
        if (StringUtils.isNotEmpty(getClassName()))
            sb.append(" " + getClassName().trim());
        sb.append("\"");

        if (StringUtils.isNoneEmpty(getId()))
            sb.append(" id=\"" + getId() + "\"");
        sb.append(">");

        sb.append("<div class=\"panel-body\">");

        write(sb);
        return BodyTagSupport.EVAL_BODY_INCLUDE;
    }

    public int doEndTag() throws JspException
    {
        StringBuilder sb = new StringBuilder();

        sb.append("</div></div>");

        write(sb);
        return BodyTagSupport.EVAL_PAGE;
    }

    private void write(StringBuilder sb) throws JspException
    {
        try
        {
            pageContext.getOut().write(sb.toString());
        }
        catch (IOException e)
        {
            throw new JspException(e);
        }
    }
}
