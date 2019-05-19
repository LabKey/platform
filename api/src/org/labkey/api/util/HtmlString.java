package org.labkey.api.util;

public class HtmlString
{
    // Helpful constants for convenience (and efficiency)
    public static HtmlString EMPTY_STRING = HtmlString.of("");
    public static HtmlString NBSP = HtmlString.unsafe("&nbsp;");

    private final String _s;

    public static HtmlString of(String s)
    {
        return new HtmlString(h(s));
    }

    public static HtmlString unsafe(String s)
    {
        return new HtmlString(s);
    }

    private static String h(String s)
    {
        return PageFlowUtil.filter(s);
    }

    @Deprecated // TODO: change to private
    public HtmlString(String s)
    {
        _s = s;
    }

    @Override
    public String toString()
    {
        return _s;
    }
}
