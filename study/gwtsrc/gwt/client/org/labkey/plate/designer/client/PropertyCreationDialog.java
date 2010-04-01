/*
 * Copyright (c) 2010 LabKey Corporation
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

package gwt.client.org.labkey.plate.designer.client;

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
