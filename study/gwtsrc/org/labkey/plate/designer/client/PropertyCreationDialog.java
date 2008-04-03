package org.labkey.plate.designer.client;

import com.google.gwt.user.client.Window;
import org.labkey.api.gwt.client.ui.TextBoxDialogBox;

/**
 * User: jeckels
 * Date: Apr 20, 2007
 */
public class PropertyCreationDialog extends TextBoxDialogBox
{
    private final PropertyPanel _panel;

    public PropertyCreationDialog(PropertyPanel panel)
    {
        super("Add Property", "Name");
        _panel = panel;
    }

    protected boolean commit(String propName)
    {
        if (propName.length() == 0)
        {
            Window.alert("You must specify a property name.");
            return false;
        }
        return _panel.addProperty(propName);
    }
}
