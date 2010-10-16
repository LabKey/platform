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
