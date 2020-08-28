package org.labkey.api.util;

/**
 * Any class can implement this interface to implement {@code HasHtmlString} in a standard way. The {@code getHtmlString()}
 * method returns an encoded version of the class's {@code toString()} method. Classes that implement this interface can be
 * safely rendered by JSPs.
 */
public interface SimpleHasHtmlString extends HasHtmlString
{
    @Override
    default HtmlString getHtmlString()
    {
        return HtmlString.of(toString());
    }
}
