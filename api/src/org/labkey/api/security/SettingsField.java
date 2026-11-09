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

import java.util.HashMap;

public class SettingsField extends HashMap<String, Object>
{
    public enum FieldType
    {
        section,
        checkbox,
        fixedHtml,
        input,
        options,
        password,
        pem  // file picker that allows upload of a PEM file (used on SAML settings page)
    }

    public static SettingsField of(@NotNull String name, @NotNull FieldType type, @NotNull String caption)
    {
        SettingsField sf = new SettingsField();
        sf.put("name", name);
        sf.put("type", type.toString());
        sf.put("caption", caption);

        return sf;
    }

    public static SettingsField of(@NotNull String name, @NotNull FieldType type, @NotNull String caption, @NotNull String description, boolean required, Object defaultValue)
    {
        SettingsField sf = of(name, type, caption, required, defaultValue);
        sf.put("description", description);

        return sf;
    }

    public static SettingsField of(@NotNull String name, @NotNull FieldType type, @NotNull String caption, boolean required, Object defaultValue)
    {
        SettingsField sf = of(name, type, caption);
        sf.put("required", required);
        sf.put("defaultValue", defaultValue);

        return sf;
    }

    public static SettingsField getStandardDomainField()
    {
        return SettingsField.of(
            "domain",
            SettingsField.FieldType.input,
            "Associated email domain",
            "Email domain that indicates which users are expected to authenticate using this configuration. " +
                "For example, if set to 'labkey.org', all users who enter xxxxxx@labkey.org are presumed to authenticate using this configuration. " +
                "This is often left blank, but if provided, LabKey will suggest this authentication method to users with the specified domain if they fail an authentication attempt using a different configuration. " +
                "LabKey will also not create database logins when administrators add user accounts with this email domain.",
            false,
            ""
        );
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
