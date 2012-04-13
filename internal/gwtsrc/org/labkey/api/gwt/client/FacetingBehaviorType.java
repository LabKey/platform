package org.labkey.api.gwt.client;

/**
 * Created by IntelliJ IDEA.
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

    private String _label;
    private String _helpText;
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
