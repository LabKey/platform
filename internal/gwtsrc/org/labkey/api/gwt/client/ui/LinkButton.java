package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.ui.*;

/**
 * User: brittp
 * Date: Feb 9, 2007
 * Time: 5:33:32 PM
 */
public class LinkButton extends Composite
{
    public LinkButton(String html, final String link)
    {
        this(html, link, false); 
    }

    public LinkButton(String html, final String link, boolean isBracketStyle)
    {
        Widget w;
        if (isBracketStyle)
            w = new HTML("[<a href=\'" + link + "'>" + html + "</a>]");
        else
            w = new ImageButton(html, new ClickListener()
                {
                    public void onClick(Widget sender)
                    {
                        WindowUtil.setLocation(link);
                    }
                });

        initWidget(w);
    }
}
