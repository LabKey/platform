package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.DOM;

/**
 * User: jeckels
 * Date: Feb 8, 2008
 */
public class FormUtil
{
    public static void setValueInForm(String value, Element element)
    {
        DOM.setElementAttribute(element, "value", value == null ? "" : value);
    }


    public static String getValueInForm(Element element)
    {
        String value = DOM.getElementAttribute(element, "value");
        if ("".equals(value))
        {
            return null;
        }
        return value;
    }
}
