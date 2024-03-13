package org.labkey.api.settings;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum Theme
{
    Harvest,
    Leaf,
    Madison,
    Mono,
    Ocean,
    Overcast,
    Seattle,
    Sky;

    private static final Map<String, Theme> THEMES = new CaseInsensitiveHashMap<>(Arrays.stream(values()).collect(Collectors.toMap(Enum::name, t -> t)));

    public static @Nullable Theme getTheme(String name)
    {
        return THEMES.get(name);
    }

    public static final Theme DEFAULT = Seattle;
}
