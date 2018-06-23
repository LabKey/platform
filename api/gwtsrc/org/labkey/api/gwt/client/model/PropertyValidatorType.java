/*
 * Copyright (c) 2010-2015 LabKey Corporation
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
package org.labkey.api.gwt.client.model;

import org.labkey.api.gwt.client.ui.HelpPopup;

/**
 * User: jeckels
 * Date: Oct 10, 2010
 */
public enum PropertyValidatorType
{
    RegEx
    {
        public HelpPopup createHelpPopup()
        {
            return new HelpPopup("RegEx Validator", "RegEx validators allow you to specify a regular expression that defines what string values are valid");
        }
    },
    Range
    {
        public HelpPopup createHelpPopup()
        {
            return new HelpPopup("Range Validator", "Range validators allow you to specify numeric comparisons that must be satisfied, such as the value must be greater than or less than some constant");
        }
    },
    Lookup
    {
        public HelpPopup createHelpPopup()
        {
            return new HelpPopup("Lookup Validator", "Lookup validators allow you to require that any value is present in the lookup's target table or query");
        }

        public boolean isConfigurable()
        {
            return false;
        }
    },
    Length
    {
        public HelpPopup createHelpPopup()
        {
            return new HelpPopup("Length Validator", "Length validators allow you to validate the length of a text field");
        }
        public boolean isConfigurable()
        {
            return false;
        }
        public boolean isHidden()
        {
            return true;
        }
    };

    public abstract HelpPopup createHelpPopup();

    public String getTypeName()
    {
        return toString().toLowerCase();
    }

    public boolean isConfigurable()
    {
        return true;
    }

    public boolean isHidden()
    {
        return false;
    }

    public static PropertyValidatorType getType(String typeName)
    {
        for (PropertyValidatorType type : values())
        {
            if (type.getTypeName().equals(typeName))
            {
                return type;
            }
        }
        return null;
    }
}
