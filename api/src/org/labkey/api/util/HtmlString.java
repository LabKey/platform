package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlString
{
    // Helpful constants for convenience (and efficiency)
    public static HtmlString EMPTY_STRING = HtmlString.of("");
    public static HtmlString NBSP = HtmlString.unsafe("&nbsp;");

    private final String _s;

    /**
     * Returns an HtmlString that wraps an HTML encoded version of the passed in String.
     * @param s A String. A null value results in an empty HtmlString (equivalent of HtmlString.of("")).
     * @return An HtmlString that encodes and wraps the String.
     */
    public static @NotNull HtmlString of(@Nullable String s)
    {
        return new HtmlString(h(s));
    }

    /**
     * Returns an HtmlString that wraps the passed in String <b>without applying any HTML encoding.</b> Use of this method
     * is dangerous and can lead to security vulnerabilities and broken HTML pages. You are responsible for ensuring that
     * all parts of the String are correctly encoded.
     * @param s A String. A null value results in an empty HtmlString (equivalent of HtmlString.of("")).
     * @return An HtmlString that wraps the String without encoding.
     */
    public static @NotNull HtmlString unsafe(@Nullable String s)
    {
        return new HtmlString(null == s ? "" : s);
    }

    private static @NotNull String h(@Nullable String s)
    {
        return PageFlowUtil.filter(s);
    }

    // Callers should use factory methods of() and unsafe() instead
    private HtmlString(String s)
    {
        _s = s;
    }

    @Override
    public String toString()
    {
        return _s;
    }
}
