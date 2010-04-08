package org.labkey.api.data;

import org.labkey.api.view.DisplayElement;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Apr 8, 2010
 * Time: 11:05:50 AM
 */

/**
 * Represents a reference to a built-in button
 */
public class BuiltInButtonConfig implements ButtonConfig
{
    private String _caption;

    public BuiltInButtonConfig(String caption)
    {
        _caption = caption;
    }

    public String getCaption()
    {
        return _caption;
    }

    public void setCaption(String caption)
    {
        _caption = caption;
    }

    public DisplayElement createButton(List<DisplayElement> originalButtons)
    {
        if (null == _caption)
            return null;

        for (DisplayElement de : originalButtons)
        {
            if (de instanceof ActionButton && _caption.equalsIgnoreCase(de.getCaption()))
                return (ActionButton)de;
        }
        return null;
    }
}
