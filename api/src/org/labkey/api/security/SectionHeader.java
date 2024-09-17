package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;

public class SectionHeader extends SettingsField
{
    public static SectionHeader of(@NotNull String caption)
    {
        SectionHeader of = new SectionHeader();
        of.put("type", FieldType.section);
        of.put("caption", caption);

        return of;
    }
}