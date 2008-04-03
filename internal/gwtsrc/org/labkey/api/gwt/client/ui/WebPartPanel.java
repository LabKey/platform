package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;

/**
 * User: jeckels
 * Date: Jun 13, 2007
 */
public class WebPartPanel extends FlexTable
{
    public WebPartPanel(String title, Widget contents)
    {
        setStyleName("wp");
        setText(0, 0, title);

        getRowFormatter().setStyleName(0, "wpHeader");
        getCellFormatter().setStyleName(0, 0, "wpTitle");

        setWidget(1, 0, contents);
    }

    public void setContent(Widget content)
    {
        setWidget(1, 0, content);
    }
}
