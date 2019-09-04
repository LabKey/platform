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

import org.labkey.api.annotations.RemoveIn20_1;
import org.labkey.api.util.PageFlowUtil;

import javax.servlet.jsp.tagext.BodyTagSupport;
import javax.servlet.jsp.JspException;
import java.io.IOException;

@Deprecated // Use JspBase.helpPopup()
@RemoveIn20_1
public class HelpPopupTag extends BodyTagSupport
{
    private String title;

    @Override
    public int doStartTag()
    {
        return EVAL_BODY_BUFFERED;
    }


    @Override
    public int doEndTag() throws JspException
    {
        try
        {
            pageContext.getOut().print(PageFlowUtil.helpPopup(title, getBodyContent().getString(), true));
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


    @Override
    public void release()
    {
        title = null;
        super.release();
    }
}
