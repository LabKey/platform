package org.labkey.api.gwt.client.util;

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
            return $wnd.location;
        }-*/;

    public static String getContainerPath()
    {
        return getServerProperty("container");
    }

    public static String getPageFlow()
    {
        return getServerProperty("pageFlow");
    }

    public static String getContextPath()
    {
        String ret = getServerProperty("contextPath");
        if (ret == null)
            return "";
        return ret;
    }

    public static String getRelativeURL(String action)
    {
        return getRelativeURL(action, getPageFlow());
    }

    public static String getRelativeURL(String action, String pageFlow)
    {
        return getContextPath() + "/" + pageFlow +
                PropertyUtil.getContainerPath() + "/" + action + ".view";
    }
}
