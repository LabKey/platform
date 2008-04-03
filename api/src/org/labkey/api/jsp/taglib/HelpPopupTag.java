package org.labkey.api.jsp.taglib;

import org.labkey.api.util.PageFlowUtil;

import javax.servlet.jsp.tagext.BodyTagSupport;
import javax.servlet.jsp.JspException;
import java.io.IOException;

public class HelpPopupTag extends BodyTagSupport
{
    private String title;
    public int doStartTag() throws JspException
    {
        return EVAL_BODY_BUFFERED;
    }


    public int doEndTag() throws JspException
    {
        try
        {
            pageContext.getOut().write(PageFlowUtil.helpPopup(title, getBodyContent().getString(), true));
        }
        catch (IOException e)
        {
            throw new JspException(e);
        }
        return EVAL_PAGE;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getTitle()
    {
        return this.title;
    }


    public void release()
    {
        title = null;
        super.release();
    }
}
