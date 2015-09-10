package org.labkey.api.gwt.client.ui;

import com.extjs.gxt.ui.client.widget.Html;
import com.google.gwt.user.client.ui.HTML;

/**
 * Created by klum on 9/8/2015.
 */
public class FontButton extends HTML
{
    public FontButton(String fontClass)
    {
        super("<span class='fa " + fontClass + "'></span>");

        setStyleName("gwt-FontImage gwt-PushButton");
    }

    public void setEnabled(boolean enabled)
    {
        if (enabled)
            removeStyleName("gwt-PushButton-up-disabled");
        else
            addStyleName("gwt-PushButton-up-disabled");
    }
}
