package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.ui.HTML;

/**
 * User: jeckels
 * Date: Feb 22, 2012
 */
public class LabKeyLinkHTML extends HTML
{
    public LabKeyLinkHTML(String linkText)
    {
        this(linkText, null);
    }

    public LabKeyLinkHTML(String linkText, String href)
    {
        setLabKeyLinkHTML(linkText, href);
    }

    public void setLabKeyLinkHTML(String linkText)
    {
        setLabKeyLinkHTML(linkText, null);
    }

    public void setLabKeyLinkHTML(String linkText, String href)
    {
        if (href == null)
        {
            href = "javascript: void(0)";
        }
        setHTML("<a class='labkey-text-link' href=\"" + href + "\">" + linkText + "<span class='css-arrow-right'></span></a>");
    }
}

