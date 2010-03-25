/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.gwt.client;

import java.io.Serializable;

/*
 * User: brittp
 * Date: Jan 29, 2009
 * Time: 2:55:25 PM
 */

public enum DefaultValueType implements Serializable
{
    FIXED_EDITABLE("Editable default", "An editable default value will be entered for the user.  The default value will be the same for every user for every upload."),
    FIXED_NON_EDITABLE("Fixed value", "Fixed values cannot be edited by the user.  This option is used to save fixed data with each inserted data row."),
    LAST_ENTERED("Last entered", "An editable default value will be entered for the user's first use of the form.  During subsequent uploads, the user will see their last entered value.");

    private String _label;
    private String _helpText;
    private static String _helpPopupHtml;

    DefaultValueType(String label, String helpText)
    {
        _label = label;
        _helpText = helpText;
    }

    public String getLabel()
    {
        return _label;
    }

    public String getHelpText()
    {
        return _helpText;
    }

    public static String getHelpPopupHtml()
    {
        if (_helpPopupHtml == null)
        {
            StringBuilder helpString = new StringBuilder();
            for (int i = 0; i < values().length; i++)
            {
                DefaultValueType type = values()[i];
                helpString.append("<b>").append(type.getLabel()).append("</b>: ").append(type.getHelpText());
                if (i < values().length - 1)
                    helpString.append("<br><br>");
            }
            _helpPopupHtml = helpString.toString();
        }
        return _helpPopupHtml;
    }
}