package org.labkey.api.util;

import java.util.function.Function;

public interface HasHtmlString extends Function<HtmlStream,HtmlStream>
{
    HtmlString getHtmlString();

    @Override
    default HtmlStream apply(HtmlStream builder)
    {
        builder.append(this.getHtmlString());
        return builder;
    }
}