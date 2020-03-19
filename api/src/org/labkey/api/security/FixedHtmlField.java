package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.HtmlString;

public class FixedHtmlField extends SettingsField
{
    public static FixedHtmlField of(@NotNull String caption, @NotNull HtmlString html)
    {
        FixedHtmlField of = new FixedHtmlField();
        of.put("type", FieldType.fixedHtml);
        of.put("caption", caption);
        of.put("html", html);

        return of;
    }
}
