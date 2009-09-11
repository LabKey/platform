/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import com.google.gwt.user.client.ui.*;
import com.google.gwt.event.dom.client.*;

/**
 * User: jeckels
 * Date: Feb 6, 2008
 */
public class Tooltip extends PopupPanel implements MouseListener, MouseOverHandler, MouseOutHandler
{
    private Label _label;
    private Widget _sourceWidget;

    public Tooltip(String text)
    {
        super(true);
        _label = new Label(text);
        add(_label);
        setStyleName("gwt-ToolTip");
    }

    private Tooltip(String text, Widget sourceWidget)
    {
        this(text);
        _sourceWidget = sourceWidget;
    }

    public void onMouseOver(MouseOverEvent event)
    {
        onMouseEnter(_sourceWidget);
    }

    public void onMouseOut(MouseOutEvent event)
    {
        hide();
    }

    public void onMouseEnter(Widget sender)
    {
        show();
        int height = getOffsetHeight();
        setPopupPosition(sender.getAbsoluteLeft() + 13, sender.getAbsoluteTop() + height);
    }

    public void onMouseLeave(Widget sender)
    {
        hide();
    }

    public void onMouseDown(Widget sender, int x, int y)
    {
    }

    public void onMouseMove(Widget sender, int x, int y)
    {
    }

    public void onMouseUp(Widget sender, int x, int y)
    {
    }

    public void setText(String text)
    {
        _label.setText(text);
    }

    public static void addTooltip(HasAllMouseHandlers widget, String text)
    {
        if (!(widget instanceof Widget))
        {
            throw new IllegalArgumentException("Must use a widget that implements HasAllMouseHandlers");
        }
        Tooltip tooltip = new Tooltip(text, (Widget)widget);
        widget.addMouseOverHandler(tooltip);
        widget.addMouseOutHandler(tooltip);
    }
}
