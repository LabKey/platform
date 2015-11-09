/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.util;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;

import java.util.Map;

/**
    These are the supported formatting functions that can be used with string substitution, for example, when substituting
    values into a details URL or the javaScriptEvents property of a JavaScriptDisplayColumnFactory. The function definitions
    are patterned off Ext.util.Format (formats used in ExtJs templates), http://docs.sencha.com/extjs/4.2.1/#!/api/Ext.util.Format

    Examples:

        ${Name:htmlEncode}
        ${MyParam:urlEncode}

    We should add more functions and allow parameterized functions. As we add functions, we should use the Ext names and
    parameters if at all possible.

 * User: adam
 * Date: 6/20/13
 * Time: 8:43 AM

*/
public enum SubstitutionFormat
{
    passThrough("none")
        {
            @Override
            public String format(String value)
            {
                return value;
            }
        },
    htmlEncode("html")
        {
            @Override
            public String format(String value)
            {
                return PageFlowUtil.filter(value);
            }
        },
    jsString("jsString")
        {
            @Override
            public String format(String value)
            {
                return PageFlowUtil.jsString(value);
            }
        },
    urlEncode("path")
        {
            @Override
            public String format(String value)
            {
                return PageFlowUtil.encodePath(value);
            }
        },
    encodeURIComponent("uricomponent")  // like javascript encodeURIComponent
        {
            @Override
            public String format(String value)
            {
                return PageFlowUtil.encodeURIComponent(value);
            }
        },
    encodeURI("uri")  // like javascript encodeURI
        {
            @Override
            public String format(String value)
            {
                return PageFlowUtil.encodeURI(value);
            }
        };

    final String _shortName;

    SubstitutionFormat(String name)
    {
        _shortName = name;
    }

    public abstract String format(String value);

    private final static Map<String, SubstitutionFormat> _map = new CaseInsensitiveHashMap<>();

    static
    {
        for (SubstitutionFormat format : SubstitutionFormat.values())
        {
            _map.put(format.name(), format);
            if (null != format._shortName)
                _map.put(format._shortName, format);
        }
    }

    // More lenient than SubstitutionFormat.valueOf(), returns null for non-match
    public static @Nullable SubstitutionFormat getFormat(String formatName)
    {
        return _map.get(formatName);
    }
}
