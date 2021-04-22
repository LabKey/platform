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

import java.io.IOException;

public class ButtonTag extends SimpleTagBase
{
    private String _text;
    private URLHelper _href;
    private String _onclick;
    private String _name;
    private String _id;
    private Boolean _submit = true;
    private Boolean _enabled;

    @Override
    public void doTag() throws IOException
    {
        Button.ButtonBuilder button = PageFlowUtil.button(_text).id(_id);

        // TODO: This shouldn't have inconsistent logic from Button.java, should just be a pass through
        if (_href != null)
            button.href(_href).onClick(_onclick);
        else
        {
            String onClickScript = "";
            if (_onclick != null)
                onClickScript = _onclick;

            button.submit(_submit).onClick(onClickScript).name(_name);
        }

        if (_enabled != null)
            button.enabled(_enabled.booleanValue());

        getOut().print(button);
    }

    public void setHref(URLHelper url)
    {
        _href = url;
    }

    public void setText(String text)
    {
        _text = text;
    }

    public void setOnclick(String onclick)
    {
        _onclick = onclick;
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

    public void setEnabled(Boolean enabled)
    {
        _enabled = enabled;
    }
}
