/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.jsp.taglib;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.context.NoSuchMessageException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.util.Enumeration;

public class ErrorsTag extends TagSupport
{
    @Override
    public int doStartTag() throws JspException
    {
        ViewContext context = HttpView.currentContext();
        JspWriter out = pageContext.getOut();

        try
        {
            // There are spring tags for this.  But I want to make this work for migration beehive->spring
            int count=0;
            Enumeration<String> e = pageContext.getAttributeNamesInScope(PageContext.REQUEST_SCOPE);
            while (e.hasMoreElements())
            {
                String s = e.nextElement();
                if (s.startsWith(BindingResult.MODEL_KEY_PREFIX))
                {
                    Object o = pageContext.getAttribute(s, PageContext.REQUEST_SCOPE);
                    if (!(o instanceof BindingResult))
                        continue;
                    BindingResult errors = (BindingResult)o;
                    if (null != errors.getAllErrors())
                        for (ObjectError m : errors.getAllErrors())
                        {
                            count++;
                            if (count == 1)
                                out.print(HtmlString.unsafe("<div class=\"labkey-error\">"));
                            try
                            {
                                if (m instanceof LabKeyError)
                                    out.print(((LabKeyError)m).renderToHTML(context));
                                else
                                    out.print(HtmlString.of(context.getMessage(m), true));
                            }
                            catch (NoSuchMessageException nsme)
                            {
                                out.print(HtmlString.unsafe("Unknown error: " + m));
                                ExceptionUtil.logExceptionToMothership((HttpServletRequest)pageContext.getRequest(), nsme);
                                Logger log = LogManager.getLogger(ErrorsTag.class);
                                log.error("Failed to find a message: " + m, nsme);
                            }
                            out.println(HtmlString.BR);
                        }
                }
            }
            if (count > 0)
                out.println(HtmlString.unsafe("</div>"));
        }
        catch (IOException e)
        {
            throw new JspException(e);
        }
        return SKIP_BODY;
    }
}
