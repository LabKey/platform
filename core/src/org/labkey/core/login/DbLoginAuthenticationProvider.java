/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
import org.labkey.api.security.AuthenticationManager.AuthenticationValidator;
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
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: adam
 * Date: Oct 12, 2007
 * Time: 1:31:18 PM
 */
public class DbLoginAuthenticationProvider implements LoginFormAuthenticationProvider
{
    public boolean isPermanent()
    {
        return true;
    }

    @NotNull
    public String getName()
    {
        return "Database";
    }

    @NotNull
    public String getDescription()
    {
        return "Stores user names and passwords in the LabKey database";
    }

    @Override
    public boolean isFicamApproved()
    {
        return true;
    }

    @Override
    // id and password will not be blank (not null, not empty, not whitespace only)
    public @NotNull AuthenticationResponse authenticate(@NotNull String id, @NotNull String password, URLHelper returnURL) throws ValidEmail.InvalidEmailException
    {
        ValidEmail email = new ValidEmail(id);
        String hash = SecurityManager.getPasswordHash(email);
        User user = UserManager.getUser(email);

        if (null == hash || null == user)
            return AuthenticationResponse.createFailureResponse(this, FailureReason.userDoesNotExist);

        if (!SecurityManager.matchPassword(password,hash))
            return AuthenticationResponse.createFailureResponse(this, FailureReason.badPassword);

        // Password is correct for this user; now check password rules and expiration.

        PasswordRule rule = DbLoginManager.getPasswordRule();
        Collection<String> messages = new LinkedList<>();

        if (!rule.isValidForLogin(password, user, messages))
        {
            return getChangePasswordResponse(user, returnURL, FailureReason.complexity);
        }
        else
        {
            PasswordExpiration expiration = DbLoginManager.getPasswordExpiration();

            if (expiration.hasExpired(() -> SecurityManager.getLastChanged(user)))
            {
                return getChangePasswordResponse(user, returnURL, FailureReason.expired);
            }
        }

        return AuthenticationResponse.createSuccessResponse(this, email);
    }

    // A simple test validator that expires every authentication after 100 requests
    private static class TestValidator implements AuthenticationValidator
    {
        private final AtomicInteger _count = new AtomicInteger();

        @Override
        public boolean test(HttpServletRequest httpServletRequest)
        {
            int c = _count.incrementAndGet();

            if (c % 10 == 0)
                Logger.getLogger(DbLoginAuthenticationProvider.class).info(c + " requests");

            return c % 100 != 0;
        }
    }

    // If this appears to be a browser request then return an AuthenticationResponse that will result in redirect to the change password page.
    private AuthenticationResponse getChangePasswordResponse(User user, URLHelper returnURL, FailureReason failureReason)
    {
        ActionURL redirectURL = null;

        try
        {
            ViewContext ctx = HttpView.currentContext();

            if (null != ctx)
            {
                Container c = ctx.getContainer();

                if (null != c)
                {
                    // We have a container, so redirect to password change page

                    // Fall back plan is the home page
                    if (null == returnURL)
                        returnURL = AppProps.getInstance().getHomePageActionURL();

                    LoginUrls urls = PageFlowUtil.urlProvider(LoginUrls.class);
                    redirectURL = urls.getChangePasswordURL(c, user, returnURL, "Your " + failureReason.getMessage() + "; please choose a new password.");
                }
            }
        }
        catch (EmptyStackException e)
        {
            // Basic auth is checked in AuthFilter, so there won't be a ViewContext in that case. #11653
        }

        return AuthenticationResponse.createFailureResponse(this, failureReason, redirectURL);
    }

    public ActionURL getConfigurationLink()
    {
        return PageFlowUtil.urlProvider(LoginUrls.class).getConfigureDbLoginURL();
    }

    @Override
    public @NotNull Collection<String> getPropertyCategories()
    {
        return Collections.singleton(DbLoginManager.DATABASE_AUTHENTICATION_CATEGORY_KEY);
    }
}
