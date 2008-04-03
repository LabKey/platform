package org.labkey.api.wiki;

/**
 * Created by IntelliJ IDEA.
 * User: Tamra Myers
 * Date: Aug 16, 2006
 * Time: 11:57:19 AM
 */
public interface WikiRenderer
{
    public interface WikiLinkable
    {
        String getTitle();

        String getName();
    }

    public FormattedHtml format(String text);
}
