/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

package org.labkey.authentication.ldap;

import org.labkey.api.security.AuthenticationProvider;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;

/**
 * User: adam
 * Date: Oct 12, 2007
 * Time: 1:31:18 PM
 */
public class LdapAuthenticationProvider implements AuthenticationProvider.LoginFormAuthenticationProvider
{
    public boolean isPermanent()
    {
        return false;
    }

    public void activate() throws Exception
    {
        LdapAuthenticationManager.activate();
    }

    public void deactivate() throws Exception
    {
        LdapAuthenticationManager.deactivate();
    }

    public String getName()
    {
        return "LDAP";
    }

    public String getDescription()
    {
        return "Uses the LDAP protocol to authenticate against an institution's directory server";
    }

    public ActionURL getConfigurationLink()
    {
        return LdapController.getConfigureURL(false);
    }

    // id and password will not be blank (not null, not empty, not whitespace only)
    public ValidEmail authenticate(String id, String password, URLHelper returnURL) throws ValidEmail.InvalidEmailException
    {
        // Consider: allow user ids other than email
        ValidEmail email = new ValidEmail(id);

        if (SecurityManager.isLdapEmail(email) && LdapAuthenticationManager.authenticate(email, password))
            return email;

        return null;
    }

    public void logout(HttpServletRequest request)
    {
        // No special handling required
    }    
}
