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
package org.labkey.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.EnumUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.AuthenticationConfiguration.SecondaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;

import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider.REQUIRED_FOR;

public abstract class BaseSecondaryAuthenticationConfiguration<AP extends SecondaryAuthenticationProvider<?>> extends BaseAuthenticationConfiguration<AP> implements SecondaryAuthenticationConfiguration<AP>
{
    private final @NotNull AuthenticationConfiguration.SecondaryAuthenticationConfiguration.RequiredFor _requiredFor;

    public BaseSecondaryAuthenticationConfiguration(AP provider, Map<String, Object> standardSettings, Map<String, Object> properties)
    {
        super(provider, standardSettings);
        _requiredFor = EnumUtils.getEnum(RequiredFor.class, (String)properties.get(REQUIRED_FOR), RequiredFor.all);
    }

    @Override
    public @Nullable String getNotRequiredMessage(User user, HttpServletRequest request)
    {
        return _requiredFor.isRequired(user) ? null : "lacks the \"Require Secondary Authentication\" role";
    }

    @Override
    public @NotNull Map<String, Object> getCustomProperties()
    {
        Map<String, Object> map = new HashMap<>();
        map.put(REQUIRED_FOR, _requiredFor.name());

        return map;
    }
}
