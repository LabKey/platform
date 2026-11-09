/*
 * Copyright (c) 2022-2026 LabKey Corporation
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

import org.labkey.api.security.Encryption.Algorithm;

import static org.labkey.api.security.AuthenticationManager.ENCRYPTION_MIGRATION_HANDLER;

// This is a separate class to help break initialization ordering issues. Previous loop, for example: CoreModule.init()
// invokes AuthenticationManager which initializes AES which attempts to register a WarningProvider with a service that
// hasn't been initialized yet.
class AES
{
    private static final Algorithm AES = Encryption.getAES128(ENCRYPTION_MIGRATION_HANDLER);

    static Algorithm get()
    {
        return AES;
    }
}
