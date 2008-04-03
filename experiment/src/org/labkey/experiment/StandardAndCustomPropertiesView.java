package org.labkey.experiment;

import org.labkey.api.view.HBox;
import org.labkey.api.view.HttpView;

/**
 * User: jeckels
 * Date: Jan 28, 2008
 */
public class StandardAndCustomPropertiesView extends HBox
{
    public StandardAndCustomPropertiesView(HttpView standardView, CustomPropertiesView customView)
    {
        super(standardView);
        if (customView.hasProperties())
        {
            addView(customView);
        }
    }
}
