/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.*;

public class ImageButton extends ButtonBase implements ClickListener
{
    private ClickListenerCollection _clickListeners = new ClickListenerCollection();
    private String _text;

    public ImageButton(String text, ClickListener listener)
    {
        this(text);
        addClickListener(listener);
    }

    public ImageButton(String text)
    {
        super(DOM.createSpan());
        addClickListener(this);

        _text = text;
        DOM.setAttribute(getElement(), "id", "button_" + text);

        refreshState();
        super.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                if (isEnabled())
                    _clickListeners.fireClick(sender);
            }
        });
    }

    public void addClickListener(ClickListener listener)
    {
        _clickListeners.add(listener);
    }

    public void removeClickListener(ClickListener listener)
    {
        _clickListeners.remove(listener);
    }
    
    public String getText()
    {
        return _text;
    }

    public void refreshState()
    {
        setHTML("<a class='" + (isEnabled() ? "labkey-button" : "labkey-disabled-button")
                + "'><span>" + _text + "</span></a>");
    }

    public void setText(String text)
    {
        if (_text.equals(text))
            return;

        _text = text;
        refreshState();
    }

    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);
        refreshState();
    }

    /** to make life simple, just override onClick instead of registering a listener */
    public void onClick(Widget sender)
    {
    }
}
