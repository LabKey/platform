/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.secrets;

import org.labkey.api.settings.StartupProperty;

/**
 * Describes a named secret that a module needs to access. Register instances with
 * {@link SecretService#register} during module startup; retrieve values via
 * {@link SecretService#getSecret}.
 *
 * Startup property file convention: {@code secret.<propertyName>=<value>}
 * Java property convention:  {@code -Plabkey.prop.secret.<propertyName>=<value>}
 * Environment variable convention: {@code export <propertyName>=<value>}
 */
public class SecretProperty implements StartupProperty
{
    private final String _name;
    private final String _description;

    public SecretProperty(String name)
    {
        this(name, "Secret: " + name);
    }

    public SecretProperty(String name, String description)
    {
        _name = name;
        _description = description;
    }

    @Override
    public String getPropertyName()
    {
        return _name;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }
}
