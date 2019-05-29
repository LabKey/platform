package org.labkey.api.util;

public interface HtmlStream
{
    HtmlStream append(String s);

    HtmlStream append(HtmlString hs);

    HtmlStream append(HasHtmlString hhs);
}
