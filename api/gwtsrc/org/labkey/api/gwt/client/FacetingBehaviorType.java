/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

/**
 * User: klum
 * Date: Apr 11, 2012
 */
public enum FacetingBehaviorType
{
    AUTOMATIC("Auto detect",
            "The column will be examined to determine if the faceted filter panel can be shown. A column is a faceting candidate if it is a " +
            "lookup, dimension or of type: (Boolean, Integer, Text, DateTime)."),
    ALWAYS_ON("On",
            "The faceted filter panel will be shown by default."),
    ALWAYS_OFF("Off",
            "The faceted filter panel will not be shown by default.");

    private final String _label;
    private final String _helpText;

    private static String _helpPopupHtml;

    FacetingBehaviorType(String label, String helpText)
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
                FacetingBehaviorType type = values()[i];
                helpString.append("<b>").append(type.getLabel()).append("</b>: ").append(type.getHelpText());
                if (i < values().length - 1)
                    helpString.append("<br><br>");
            }
            _helpPopupHtml = helpString.toString();
        }
        return _helpPopupHtml;
    }
}
