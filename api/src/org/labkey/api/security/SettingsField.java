package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class SettingsField extends HashMap<String, Object>
{
    public enum FieldType
    {
        checkbox,
        fixedHtml,
        input,
        options,
        password,
        textarea  // textarea that allows paste or file upload (e.g., SAML settings)
    }

    public static SettingsField of(@NotNull String name, @NotNull FieldType type, @NotNull String caption, @NotNull String description, boolean required, Object defaultValue)
    {
        SettingsField sf = of(name, type, caption, required, defaultValue);
        sf.put("description", description);

        return sf;
    }

    public static SettingsField of(@NotNull String name, @NotNull FieldType type, @NotNull String caption, boolean required, Object defaultValue)
    {
        SettingsField sf = new SettingsField();
        sf.put("name", name);
        sf.put("type", type.toString());
        sf.put("caption", caption);
        sf.put("required", required);
        sf.put("defaultValue", defaultValue);

        return sf;
    }

    /*
        If set on a checkbox field, the checkbox state will determine the visibility of all subsequent fields.
     */
    public SettingsField dictateFieldVisibility()
    {
        put("dictateFieldVisibility", true);
        return this;
    }
}
