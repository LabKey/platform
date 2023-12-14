/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

import org.labkey.api.util.HtmlString;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.HttpView;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

/** This tag works perfectly well, however, it breaks editing script in IntelliJ */
public class ScriptTag extends BodyTagSupport
{
    static final HtmlString endTag = HtmlString.unsafe("</script>");

    @Override
    public int doStartTag() throws JspException
    {
        try
        {
            HtmlString startTag = HtmlString.unsafe("<script type=\"text/javascript\" nonce=\"" + HttpView.currentPageConfig().getScriptNonce() + "\">");
            pageContext.getOut().print(startTag);
        }
        catch(IOException x)
        {
            throw UnexpectedException.wrap(x);
        }
        return BodyTagSupport.EVAL_BODY_INCLUDE;
    }

    @Override
    public int doEndTag() throws JspException
    {
        try
        {
            pageContext.getOut().print(endTag);
        }
        catch(IOException x)
        {
            throw UnexpectedException.wrap(x);
        }

        return BodyTagSupport.EVAL_PAGE;
    }
}
