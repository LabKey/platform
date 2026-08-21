/*
 * Copyright (c) 2023-2026 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationManager.AuthenticationResult;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.URLHelper;
import org.springframework.validation.BindException;

import jakarta.servlet.http.HttpServletRequest;

public interface DbLoginService
{
    static void setInstance(DbLoginService impl)
    {
        ServiceRegistry.get().registerService(DbLoginService.class, impl);
    }

    static DbLoginService get()
    {
        return ServiceRegistry.get().getService(DbLoginService.class);
    }

    AuthenticationResult attemptSetPassword(Container c, User currentUser, String rawPassword, String rawPassword2, HttpServletRequest request, User affectedUser, URLHelper returnUrlHelper, String auditMessage, boolean clearVerification, boolean changeOperation, BindException errors) throws InvalidEmailException;

    PasswordRule getPasswordRule();
}
