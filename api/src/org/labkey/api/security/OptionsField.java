/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
