/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.RedirectException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: adam
 * Date: Oct 10, 2007
 * Time: 6:49:05 PM
 */
public abstract interface AuthenticationProvider
{
    public ActionURL getConfigurationLink();
    public String getName();
    public void logout(HttpServletRequest request);
    public void activate() throws Exception;
    public void deactivate() throws Exception;
    public boolean isPermanent();

    public static interface RequestAuthenticationProvider extends AuthenticationProvider
    {
        public ValidEmail authenticate(HttpServletRequest request, HttpServletResponse response) throws ValidEmail.InvalidEmailException, RedirectException;
    }

    public static interface LoginFormAuthenticationProvider extends AuthenticationProvider
    {
        // id and password will not be blank (not null, not empty, not whitespace only)
        public ValidEmail authenticate(String id, String password) throws ValidEmail.InvalidEmailException;
    }
}
