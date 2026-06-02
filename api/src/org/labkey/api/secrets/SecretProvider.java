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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * SPI for a secret source. Implementations cover built-in sources (startup property files,
 * environment variables) and external stores (e.g., AWS SSM Parameter Store).
 * Providers are consulted in priority order by {@link SecretService}.
 */
public interface SecretProvider
{
    /** Returns the secret for the given property name, or null if not available from this source. */
    @Nullable String getSecret(String propertyName);

    /** Human-readable name for this source, shown on the admin secrets page. */
    @NotNull String getDescription();
}
