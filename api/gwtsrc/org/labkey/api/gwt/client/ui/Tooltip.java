/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.event.dom.client.*;

/**
 * User: jeckels
 * Date: Feb 6, 2008
 */
public class Tooltip extends PopupPanel implements MouseOverHandler, MouseOutHandler
{
    private Label _label;
    private Widget _sourceWidget;

    private Tooltip(String text, Widget sourceWidget)
    {
        super(true);
        _label = new Label(text);
        add(_label);
        setStyleName("gwt-ToolTip");
        _sourceWidget = sourceWidget;
    }

    public void onMouseOver(MouseOverEvent event)
    {
        show();
        int height = getOffsetHeight();
        int width = getOffsetHeight();
        int top = _sourceWidget.getAbsoluteTop() + height;
        int left = _sourceWidget.getAbsoluteLeft() + 13;
        int rightOverhang = left + width - (Window.getScrollLeft() + Window.getClientWidth());
        if (rightOverhang > 0)
        {
            left -= rightOverhang;
        }
        setPopupPosition(left, top);
    }

    public void onMouseOut(MouseOutEvent event)
    {
        hide();
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
