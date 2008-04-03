package org.labkey.api.jsp.taglib;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.HttpView;
import org.springframework.validation.BindingResult;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.apache.log4j.Logger;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;

public class ErrorsTag extends TagSupport
{
    public int doStartTag() throws JspException
    {
        ViewContext context = HttpView.currentContext();
        JspWriter out = pageContext.getOut();

        try
        {
            out.print(PageFlowUtil.getStrutsError((HttpServletRequest) pageContext.getRequest(), null));

            // There are spring tags for this.  But I want to make this work for migration beehive->spring
            int count=0;
            Enumeration e = pageContext.getAttributeNamesInScope(PageContext.REQUEST_SCOPE);
            while (e.hasMoreElements())
            {
                String s = (String)e.nextElement();
                if (s.startsWith(BindingResult.MODEL_KEY_PREFIX))
                {
                    Object o = pageContext.getAttribute(s, PageContext.REQUEST_SCOPE);
                    if (!(o instanceof BindingResult))
                        continue;
                    BindingResult errors = (BindingResult)o;
                    if (null != errors.getAllErrors())
                        for (MessageSourceResolvable m : (List<MessageSourceResolvable>)errors.getAllErrors())
                        {
                            count++;
                            if (count == 1)
                                out.print("<font color=\"red\" class=\"error\">");
                            try
                            {
                                out.print(context.getMessage(m));
                            }
                            catch (NoSuchMessageException nsme)
                            {
                                out.print("Unknown error: " + m);
                                ExceptionUtil.logExceptionToMothership((HttpServletRequest)pageContext.getRequest(), nsme);
                                Logger log = Logger.getLogger(ErrorsTag.class);
                                log.error("Failed to find a message: " + m, nsme);
                            }
                            out.println("<br>");
                        }
                }
            }
            if (count > 0)
                out.println("</font>");
        }
        catch (IOException e)
        {
            throw new JspException(e);
        }
        return SKIP_BODY;
    }
}