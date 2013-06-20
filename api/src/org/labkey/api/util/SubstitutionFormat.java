package org.labkey.api.util;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: 6/20/13
 * Time: 8:43 AM
 */

/*

    These are the supported formatting functions that can be used with string substitution, for example, when substituting
    values into a details URL or the javaScriptEvents property of a JavaScriptDisplayColumnFactory. The function definitions
    are patterned off Ext.util.Format (formats used in ExtJs templates), http://docs.sencha.com/extjs/4.2.1/#!/api/Ext.util.Format

    Examples:

        ${Name:htmlEncode}
        ${MyParam:urlEncode}

    We should add more functions and allow paramaterized functions. As we add fucntions, we should use the Ext names and
    parameters if at all possible.

*/
public enum SubstitutionFormat
{
    passThrough
        {
            @Override
            public String format(String value)
            {
                return value;
            }
        },
    htmlEncode
        {
            @Override
            public String format(String value)
            {
                return PageFlowUtil.filter(value);
            }
        },
    urlEncode
        {
            @Override
            public String format(String value)
            {
                return PageFlowUtil.encodePath(value);
            }
        };

    public abstract String format(String value);

    private final static Map<String, SubstitutionFormat> _map = new HashMap<>();

    static
    {
        for (SubstitutionFormat format : SubstitutionFormat.values())
            _map.put(format.name(), format);
    }

    // More lenient than SubstitutionFormat.valueOf(), returns null for non-match
    public static @Nullable SubstitutionFormat getFormat(String formatName)
    {
        return _map.get(formatName);
    }
}
