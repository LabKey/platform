/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.gwt.client.util;

import com.google.gwt.http.client.URL;

/**
 * User: brittp
 * Date: Feb 2, 2007
 * Time: 9:49:20 AM
 */
public class PropertyUtil
{
    public static native String getServerProperty(String propName)
        /*-{
            var value = $wnd.LABKEY.GWTProperties[propName];
            if (!value)
                return null;
            else
                return value;
        }-*/;

    public static native String getCurrentURL()
        /*-{
            var location = $wnd.location + '';
            // Safari returns current location with spaces, rather than %20.
            // The "g" parameter creates a "global" regex, which will replace all instances.
            location = location.replace(new RegExp(' ', 'g'), '%20');
            location = location.replace(new RegExp('\\+', 'g'), '%20');
            return location;
        }-*/;

    public static String getContainerPath()
    {
        return getServerProperty("container");
    }

    // Is the current container a project?
    public static boolean isProject()
    {
        String path = getContainerPath();
        // Currently in a project if container path is not the root and has no slashes after the first character
        return !"/".equals(path) && (-1 == getContainerPath().indexOf('/', 1));
    }

    public static String getController()
    {
        return getServerProperty("controller");
    }

    public static String getAction()
    {
        return getServerProperty("action");
    }

    public static String getQueryString()
    {
        return getServerProperty("queryString");
    }

    public static String getReturnURL()
    {
        return getServerProperty("returnUrl");
    }

    public static String getRedirectURL()
    {
        return getServerProperty("redirectUrl");
    }

    public static String getCancelURL()
    {
        return getServerProperty("cancelUrl");
    }

    public static String getContextPath()
    {
        String ret = getServerProperty("contextPath");
        if (ret == null)
            return "";
        return ret;
    }

    // @Nullable (commented because GWT doesn't recognize)
    public static String getMaxAllowedPhi()
    {
        return getServerProperty("maxAllowedPhi");
    }

    public static String getRelativeURL(String action)
    {
        return getRelativeURL(action, getController());
    }

    public static String getRelativeURL(String action, String pageFlow)
    {
        String[] pathParts = PropertyUtil.getContainerPath().split("/");
        String encodedPath = "/";
        for (String pathPart : pathParts)
        {
            if (pathPart.length() > 0)
            {
                //issue 14006: changed encodeComponent to encodePathSegment, b/c the former will convert spaces to '+'
                String part = URL.encodePathSegment(pathPart);
                encodedPath += part + "/";
            }
        }
        if (!action.contains("."))
            action = action + ".view";
        return getContextPath() + encodedPath + pageFlow + "-" + action;
    }

    /** @return true if the two arguments are both null, or are .equals() */
    public static boolean nullSafeEquals(Object o1, Object o2)
    {
        if (o1 == o2)
        {
            return true;
        }

        return o1 != null && o1.equals(o2);
    }
}
