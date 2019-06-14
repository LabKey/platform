package org.labkey.api.util;

/**
 * Turns any Enum into a HasHtmlString, where each constant returns an HtmlString containing its name.
 * @param <E> The Enum
 */
public interface EnumHasHtmlString<E extends Enum> extends HasHtmlString
{
    @Override
    default HtmlString getHtmlString()
    {
        return HtmlString.of(((E)this).name());
    }
}
