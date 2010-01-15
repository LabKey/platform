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

import org.apache.log4j.Logger;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;

/**
 * User: adam
 * Date: Oct 12, 2007
 * Time: 1:31:18 PM
 */
public class DbLoginAuthenticationProvider implements LoginFormAuthenticationProvider
{
    private static final Logger _log = Logger.getLogger(DbLoginAuthenticationProvider.class);

    public boolean isPermanent()
    {
        return true;
    }

    public void activate() throws Exception
    {
    }

    public void deactivate() throws Exception
    {
    }

    public String getName()
    {
        return "Database";
    }

    public String getDescription()
    {
        return "Stores user names and passwords in the LabKey database";
    }

    // id and password will not be blank (not null, not empty, not whitespace only)
    public ValidEmail authenticate(String id, String password) throws ValidEmail.InvalidEmailException
    {
        ValidEmail email = new ValidEmail(id);
        String hash = SecurityManager.getPasswordHash(email);

        if (null == hash)
            _log.error("DbLoginAuthenticationProvider: Invalid login. name=" + email + ", does not exist.");
        else if (!hash.equals(Crypt.digest(password)))
            _log.error("DbLoginAuthenticationProvider: Invalid login. name=" + email + ", bad password.");
        else
            return email;

        return null;
    }


    public ActionURL getConfigurationLink()
    {
        return PageFlowUtil.urlProvider(LoginUrls.class).getConfigureDbLoginURL();
    }


    public void logout(HttpServletRequest request)
    {
        // No special handling required
    }
}
