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

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider.REQUIRED_FOR;

public abstract class SecondarySaveConfigurationForm extends SaveConfigurationForm
{
    private String _requiredFor = null;

    public String getRequiredFor()
    {
        return _requiredFor;
    }

    @SuppressWarnings("unused")
    public void setRequiredFor(String requiredFor)
    {
        _requiredFor = requiredFor;
    }

    @Override
    public @NotNull Map<String, Object> getPropertyMap()
    {
        Map<String, Object> map = new HashMap<>();
        map.put(REQUIRED_FOR, getRequiredFor());
        return map;
    }
}
