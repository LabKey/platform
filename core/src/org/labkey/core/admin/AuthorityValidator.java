/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.core.admin;

import org.apache.commons.validator.routines.UrlValidator;

class AuthorityValidator extends UrlValidator
{
    public AuthorityValidator(long options)
    {
        super(options);
    }

    @Override
    public boolean isValidAuthority(String authority)
    {
        String base = authority.startsWith("*.") ? authority.substring(2) : authority;
        return super.isValidAuthority(base);
    }
}
