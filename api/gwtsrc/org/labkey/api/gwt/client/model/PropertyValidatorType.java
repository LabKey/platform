/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
        @Override
        public String getLabel()
        {
            return "RegEx Validator";
        }

        @Override
        public HelpPopup createHelpPopup()
        {
            return new HelpPopup(getLabel(), "RegEx validators allow you to specify a regular expression that defines what string values are valid");
        }
    },
    Range
    {
        @Override
        public String getLabel()
        {
            return "Range Validator";
        }

        @Override
        public HelpPopup createHelpPopup()
        {
            return new HelpPopup(getLabel(), "Range validators allow you to specify numeric comparisons that must be satisfied, such as the value must be greater than or less than some constant");
        }
    },
    Lookup
    {
        @Override
        public String getLabel()
        {
            return "Lookup Validator";
        }

        @Override
        public HelpPopup createHelpPopup()
        {
            return new HelpPopup(getLabel(), "Lookup validators allow you to require that any value is present in the lookup's target table or query");
        }

        @Override
        public boolean isConfigurable()
        {
            return false;
        }
    },
    TextLength
    {
        @Override
        public String getLabel()
        {
            return "Length Validator";
        }

        @Override
        public HelpPopup createHelpPopup()
        {
            return new HelpPopup(getLabel(), "Length validators allow you to validate the length of a text field");
        }
        @Override
        public boolean isConfigurable()
        {
            return false;
        }
        @Override
        public boolean isHidden()
        {
            return true;
        }
    },
    TextChoice
    {
        @Override
        public String getLabel()
        {
            return "Text Choice Validator";
        }

        @Override
        public HelpPopup createHelpPopup()
        {
            return new HelpPopup(getLabel(), "Text Choice validators allow you to specify a set of text values that are used to constrain values for a given domain field, like a light-weight lookup.");
        }
    };

    public abstract HelpPopup createHelpPopup();

    public abstract String getLabel();

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
