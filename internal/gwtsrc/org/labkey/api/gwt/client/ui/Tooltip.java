package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.ui.*;

/**
 * User: jeckels
 * Date: Feb 6, 2008
 */
public class Tooltip extends PopupPanel implements MouseListener
{
    private Label _label;

    public Tooltip(String text)
    {
        super(true);
        _label = new Label(text);
        add(_label);
        setStyleName("gwt-ToolTip");
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
}
