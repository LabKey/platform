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
package org.labkey.core.secrets;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.secrets.SecretProvider;

class EnvironmentVariableSecretProvider implements SecretProvider
{
    @Override
    public @Nullable String getSecret(String propertyName)
    {
        String value = System.getenv(propertyName);
        return StringUtils.isNotBlank(value) ? value : null;
    }

    @Override
    public @NotNull String getDescription()
    {
        return "Environment variable";
    }
}
