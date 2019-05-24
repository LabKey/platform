package org.labkey.api.util;

import java.util.function.Function;

public interface HasHtmlString extends Function<HtmlStringBuilder,HtmlStringBuilder>
{
    HtmlString getHtmlString();

    @Override
    default HtmlStringBuilder apply(HtmlStringBuilder builder)
    {
        builder.append(this);
        return builder;
    }
}