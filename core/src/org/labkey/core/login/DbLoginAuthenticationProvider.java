/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
package org.labkey.core.login;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;

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
    public ValidEmail authenticate(String id, String password, URLHelper returnURL) throws ValidEmail.InvalidEmailException, RedirectException
    {
        ValidEmail email = new ValidEmail(id);
        String hash = SecurityManager.getPasswordHash(email);

        if (null == hash)
        {
            _log.error("Invalid login. name=" + email + ", does not exist.");
            return null;
        }

        if (!SecurityManager.matchPassword(password,hash))
        {
            _log.error("Invalid login. name=" + email + ", incorrect password.");
            return null;
        }

        // Password is correct for this user.  Now check password rules and expiration.

        // TODO: skip checks if basic auth

        PasswordRule rule = DbLoginManager.getPasswordRule();
        User user = UserManager.getUser(email);
        assert null != user;
        Collection<String> messages = new LinkedList<String>();

        if (!rule.isValidForLogin(password, user, messages))
            return handleProblem(user, returnURL, "doesn't meet the current complexity requirements");

        PasswordExpiration expiration = DbLoginManager.getPasswordExpiration();
        Date lastChanged = SecurityManager.getLastChanged(user);

        if (expiration.hasExpired(lastChanged))
            return handleProblem(user, returnURL, "has expired");

        return email;
    }


    private ValidEmail handleProblem(User user, URLHelper returnURL, String description) throws RedirectException
    {
        _log.info("Password for " + user.getEmail() + " " + description + ".");

        ViewContext ctx = HttpView.currentContext();

        if (null != ctx)
        {
            Container c = ctx.getContainer();

            if (null != c)
            {
                // We have a container and a returnURL, so redirect to password change page

                // Fall back plan is the home page.
                if (null == returnURL)
                    returnURL = AppProps.getInstance().getHomePageActionURL();

                LoginUrls urls = PageFlowUtil.urlProvider(LoginUrls.class);
                ActionURL changePasswordURL = urls.getChangePasswordURL(c, user.getEmail(), returnURL, "Your password " + description + "; please choose a new password.");

                throw new RedirectException(changePasswordURL);
            }
        }

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
