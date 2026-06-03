/*
 * Copyright (c) 2024-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
