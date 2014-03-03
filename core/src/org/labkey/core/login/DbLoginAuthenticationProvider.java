/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
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
import java.util.EmptyStackException;
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

    @Override
    // id and password will not be blank (not null, not empty, not whitespace only)
    public AuthenticationResponse authenticate(@NotNull String id, @NotNull String password, URLHelper returnURL) throws ValidEmail.InvalidEmailException, RedirectException
    {
        ValidEmail email = new ValidEmail(id);
        String hash = SecurityManager.getPasswordHash(email);
        User user = UserManager.getUser(email);

        if (null == hash || null == user) return AuthenticationResponse.createFailureResponse(FailureReason.userDoesNotExist);

        if (!SecurityManager.matchPassword(password,hash))
            return AuthenticationResponse.createFailureResponse(FailureReason.badPassword);

        // Password is correct for this user.  Now check password rules and expiration.

        PasswordRule rule = DbLoginManager.getPasswordRule();
        Collection<String> messages = new LinkedList<>();

        if (!rule.isValidForLogin(password, user, messages))
        {
            redirectIfPossible(user, returnURL, FailureReason.complexity);
            return AuthenticationResponse.createFailureResponse(FailureReason.complexity);
        }
        else
        {
            PasswordExpiration expiration = DbLoginManager.getPasswordExpiration();
            Date lastChanged = SecurityManager.getLastChanged(user);

            if (expiration.hasExpired(lastChanged))
            {
                redirectIfPossible(user, returnURL, FailureReason.expired);
                return AuthenticationResponse.createFailureResponse(FailureReason.expired);
            }
        }

        return AuthenticationResponse.createSuccessResponse(email);
    }

    // If this appears to be a browser request, then throw a redirect to the change password page. If not, just return.
    // TODO: Better detection of browser case? TODO: Create audit log entry in redirect case? Right now, complexity and
    // expiration issues during BASIC auth requests get logged but not the same issues during browser requests.
    private void redirectIfPossible(User user, URLHelper returnURL, FailureReason reason) throws RedirectException
    {
        ViewContext ctx = null;

        try
        {
            ctx = HttpView.currentContext();
        }
        catch (EmptyStackException e)
        {
            // Basic auth is checked in AuthFilter, so there won't be a ViewContext in that case. #11653
        }

        if (null != ctx)
        {
            Container c = ctx.getContainer();

            if (null != c)
            {
                // We have a container, so redirect to password change page

                // Fall back plan is the home page
                if (null == returnURL)
                    returnURL = AppProps.getInstance().getHomePageActionURL();

                _log.info(user.getEmail() + " failed to login: " + reason.getMessage());

                LoginUrls urls = PageFlowUtil.urlProvider(LoginUrls.class);
                ActionURL changePasswordURL = urls.getChangePasswordURL(c, user, returnURL, "Your " + reason.getMessage() + "; please choose a new password.");

                throw new RedirectException(changePasswordURL);
            }
        }
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
