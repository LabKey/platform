/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.ButtonBase;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.ClickListenerCollection;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

public class ImageButton extends ButtonBase implements ClickListener
{
    private ClickListenerCollection _clickListeners = new ClickListenerCollection();
    private List<ClickHandler> _clickHandlers = new ArrayList<ClickHandler>();
    private String _text;

    public ImageButton(String text, ClickListener listener)
    {
        this(text);
        addClickListener(listener);
    }

    public ImageButton(String text, ClickHandler handler)
    {
        this(text);
        addClickHandler(handler);
    }

    public ImageButton(String text)
    {
        super(DOM.createSpan());
        addClickListener(this);

        _text = text;
        DOM.setAttribute(getElement(), "id", "button_" + text);

        refreshState();

        addKeyPressHandler(new KeyPressHandler()
        {
            public void onKeyPress(KeyPressEvent event)
            {
                if (event.getCharCode() == ' ' && !event.isAnyModifierKeyDown())
                {
                    fireEvent(new ClickEvent()
                    {
                        // Hack - subclass exists to make the constructor public
                    });
                }
            }
        });

        super.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                if (isEnabled())
                    _clickListeners.fireClick(sender);
            }
        });
        super.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                if (isEnabled())
                {
                    for (ClickHandler clickHandler : _clickHandlers)
                    {
                        clickHandler.onClick(event);
                    }
                }
            }
        });
    }

    @Override
    public HandlerRegistration addClickHandler(final ClickHandler handler)
    {
        _clickHandlers.add(handler);
        return new HandlerRegistration()
        {
            public void removeHandler()
            {
                _clickHandlers.remove(handler);
            }
        };
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
        // TODO: This should try to use Button.ButtonBuilder
        setHTML("<a class=\"labkey-button" + (isEnabled() ? "" : " labkey-disabled-button")
                + "\"><span>" + _text + "</span></a>");
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
