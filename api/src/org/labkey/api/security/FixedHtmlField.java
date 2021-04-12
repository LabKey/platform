package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.HtmlString;

public class FixedHtmlField extends SettingsField
{
    /**
     * Displays a server-supplied fixed HTML field on an authentication dialog
     * @param caption Label for field
     * @param html Well-formed, properly encoded HTML to display. The html parameter supports string substitution of the
     *             current configuration's property values. For example: "configuration=${configuration} description=${description}".
     *             These substitutions are <b>case-sensitive</b>.
     * @return The field
     */
    public static FixedHtmlField of(@NotNull String caption, @NotNull HtmlString html)
    {
        FixedHtmlField field = new FixedHtmlField();
        field.put("type", FieldType.fixedHtml);
        field.put("caption", caption);
        field.put("html", html);

        return field;
    }

    /**
     * Displays a server-supplied fixed HTML field on an authentication dialog
     * @param caption Label for field
     * @param html Well-formed, properly encoded HTML to display. The html parameter supports string substitution of the
     *             current configuration's property values. For example: "configuration=${configuration} description=${description}".
     *             These substitutions are <b>case-sensitive</b>.
     * @param description Text for the tooltip
     * @return The field
     */
    public static FixedHtmlField of(@NotNull String caption, @NotNull HtmlString html, String description)
    {
        FixedHtmlField field = of(caption, html);
        field.put("description", description);

        return field;
    }
}
