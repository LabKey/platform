package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public class OptionsField extends SettingsField
{
    private final Map<String, String> _optionsMap = new LinkedHashMap<>();

    public static OptionsField of(@NotNull String name, @NotNull String caption, @NotNull String description, boolean required, String defaultValue)
    {
        OptionsField of = new OptionsField();
        of.put("name", name);
        of.put("type", FieldType.options);
        of.put("caption", caption);
        of.put("description", description);
        of.put("required", required);
        of.put("defaultValue", defaultValue);

        return of;
    }

    public OptionsField addOption(String value, String label)
    {
        putIfAbsent("options", _optionsMap);
        _optionsMap.put(value, label);

        return this;
    }
}
