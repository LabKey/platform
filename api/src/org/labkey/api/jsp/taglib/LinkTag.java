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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;

import javax.servlet.jsp.JspWriter;
import java.io.IOException;

public class LinkTag extends SimpleTagBase
{
    private URLHelper _href;
    private String _text;

    public void setHref(URLHelper url)
    {
        _href = url;
    }

    public void setText(String text)
    {
        _text = text;
    }

    @Override
    public void doTag() throws IOException
    {
        JspWriter out = getOut();
        out.print(PageFlowUtil.link(_text).href(_href));
    }
}
