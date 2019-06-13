package org.labkey.api.util;

import static org.labkey.api.util.PageFlowUtil.filter;


public class HtmlStringBuilder implements HtmlStream, HasHtmlString
{
    private final StringBuilder _sb = new StringBuilder();

    public static HtmlStringBuilder of(String s)
    {
        return new HtmlStringBuilder().append(s);
    }

    public static HtmlStringBuilder of(HtmlString hs)
    {
        return new HtmlStringBuilder().append(hs);
    }

    public static HtmlStringBuilder of(HasHtmlString hhs)
    {
        return new HtmlStringBuilder().append(hhs);
    }

    @Override
    public HtmlStringBuilder append(String s)
    {
        _sb.append(h(s));
        return this;
    }

    @Override
    public HtmlStringBuilder append(HtmlString hs)
    {
        _sb.append(hs.toString());
        return this;
    }

    @Override
    public HtmlStringBuilder append(HasHtmlString hhs)
    {
        _sb.append(hhs.getHtmlString());
        return this;
    }

    @Override
    public HtmlString getHtmlString()
    {
        return HtmlString.unsafe(_sb.toString());
    }

    private static String h(String s)
    {
        return filter(s);
    }
}