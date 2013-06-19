/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LinkTag extends SimpleTagBase
{
    String _href;
    String _text;
    String _id;
    String _onclick;
    String _rel;

    public void setHref(String href)
    {
        _href = href;
    }

    public void setHref(URLHelper url)
    {
        _href = url.toString();
    }

    public void setText(String text)
    {
        _text = text;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public void setOnclick(String onclick)
    {
        _onclick = onclick;
    }

    public void setRel(String rel)
    {
        _rel = rel;
    }

    public void doTag() throws JspException, IOException
    {
        Map<String, String> properties = new HashMap<>();
        if (_rel != null)
            properties.put("rel", _rel);

        JspWriter out = getOut();
        out.write(PageFlowUtil.textLink(_text, _href, _onclick, _id, properties));
    }
}
