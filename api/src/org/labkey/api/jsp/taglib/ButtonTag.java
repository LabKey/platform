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

import org.labkey.api.util.Button;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import java.io.IOException;

public class ButtonTag extends SimpleTagBase
{
    private String _text;
    private String _href;
    private String _onclick;
    private String _action;
    private String _name;
    private String _id;
    private Boolean _submit = true;

    @Override
    public void doTag() throws IOException
    {
        Button.ButtonBuilder button = PageFlowUtil.button(_text).id(_id);

        // TODO: This shouldn't have inconsistent logic from Button.java, should just be a pass through
        if (_href != null)
            button.href(_href).onClick(_onclick);
        else
        {
            if (_onclick != null && _action != null)
                throw new IllegalArgumentException("onclick and action cannot both be set");

            String onClickScript = "";
            if (_onclick != null)
                onClickScript = _onclick;
            if (_action != null)
                onClickScript = ("this.form.action='" + _action + "';this.form.method='POST';");

            button.submit(_submit).onClick(onClickScript).name(_name);
        }

        getOut().print(button);
    }

    public void setHref(String href)
    {
        _href = href;
    }

    public void setHref(URLHelper url)
    {
        if (null != url)
            _href = url.toString();
    }

    public void setText(String text)
    {
        _text = text;
    }

    public void setOnclick(String onclick)
    {
        _onclick = onclick;
    }

    public void setAction(ActionURL action)
    {
        _action = action.toString();
    }

    public void setName(String name)
    {
        _name = name;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public void setSubmit(Boolean submit)
    {
        _submit = submit;
    }
}
