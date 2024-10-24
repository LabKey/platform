/*
 * Copyright (c) 2010-2019 LabKey Corporation
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

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.Throttle;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ApiKeyManager;
import org.labkey.api.security.AuthenticationManager.AuthenticationValidator;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.ConfigurationSettings;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.PasswordRule;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.StandardStartupPropertyHandler;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.core.login.DbLoginManager.Key;

import java.util.Collection;
import java.util.EmptyStackException;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.labkey.api.security.AuthenticationManager.AUTH_LOGGING_THROTTLE_TTL;
import static org.labkey.api.security.SecurityManager.API_KEY;
import static org.labkey.core.login.DbLoginManager.DATABASE_AUTHENTICATION_CATEGORY_KEY;

public class DbLoginAuthenticationProvider implements LoginFormAuthenticationProvider<DbLoginConfiguration>
{
    @Override
    public DbLoginConfiguration getAuthenticationConfiguration(@NotNull ConfigurationSettings ignored)
    {
        Map<String, Object> properties = Map.of(
            "RowId", 0,
            "Enabled", true,
            "Name", getName()
        );

        Map<String, String> stringProperties = DbLoginManager.getProperties();

        return new DbLoginConfiguration(this, stringProperties, properties);
    }

    @Override
    public boolean isPermanent()
    {
        return true;
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Database";
    }

    @Override
    @NotNull
    public String getDescription()
    {
        return "Stores user names and password hashes in the LabKey database";
    }

    @Override
    public boolean isFicamApproved()
    {
        return true;
    }

    private static final Throttle<String> API_KEY_LAST_USED_THROTTLE = new Throttle<>("API key LastUsed update throttle",
    1000, AUTH_LOGGING_THROTTLE_TTL, apiKey -> ApiKeyManager.get().updateLastUsed(apiKey));

    @Override
    // id and password will not be blank (not null, not empty, not whitespace only)
    public @NotNull AuthenticationResponse authenticate(DbLoginConfiguration configuration, @NotNull String id, @NotNull String password, URLHelper returnURL) throws InvalidEmailException
    {
        // Check for API key first
        if (API_KEY.equals(id))
        {
            User user = ApiKeyManager.get().authenticateFromApiKey(password);
            final AuthenticationResponse ret;

            if (user != null)
            {
                // API keys are exempt from secondary authentication, Issue 48764
                ret = AuthenticationResponse.createSuccessResponse(configuration, new ValidEmail(user.getEmail()), null, Map.of(), UserManager.UserAuditEvent.API_KEY, false);
                // Update core.ApiKeys.LastUsed (throttled)
                API_KEY_LAST_USED_THROTTLE.execute(password);
            }
            else
            {
                ret = AuthenticationResponse.createFailureResponse(configuration, FailureReason.badApiKey);
            }

            return ret;
        }
        else
        {
            String hash;
            User user;

            try
            {
                ValidEmail email = new ValidEmail(id);
                hash = SecurityManager.getPasswordHash(email);
                user = UserManager.getUser(email);
                if (null == hash || null == user)
                    return AuthenticationResponse.createFailureResponse(configuration, FailureReason.userDoesNotExist);
            }
            catch (InvalidEmailException e)
            {
                // This invalid email address might be in the database. If so, attempt to authenticate; if not, throw.
                hash = SecurityManager.getPasswordHash(id);
                user = UserManager.getUsers(true).stream()
                    .filter(u -> u.getEmail().equals(id))
                    .findAny()
                    .orElse(null);
                if (null == hash || null == user)
                    throw e;
            }

            if (!SecurityManager.matchPassword(password, hash))
                return AuthenticationResponse.createFailureResponse(configuration, FailureReason.badPassword);

            if (user.isActive())
            {
                // Password is correct and user is active; now check password rules and expiration
                PasswordRule rule = configuration.getPasswordRule();
                Collection<String> messages = new LinkedList<>();

                if (!rule.isValidForLogin(password, user, messages))
                {
                    return getChangePasswordResponse(configuration, user, returnURL, FailureReason.complexity);
                }
                else
                {
                    PasswordExpiration expiration = configuration.getExpiration();
                    User user2 = user;

                    if (expiration.hasExpired(() -> SecurityManager.getLastChanged(user2)))
                    {
                        return getChangePasswordResponse(configuration, user, returnURL, FailureReason.expired);
                    }
                }
            }

            return AuthenticationResponse.createSuccessResponse(configuration, user);
        }
    }

    @Override
    public void handleStartupProperties()
    {
        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>(DATABASE_AUTHENTICATION_CATEGORY_KEY, Key.class)
        {
            @Override
            public void handle(Map<Key, StartupPropertyEntry> map)
            {
                if (!map.isEmpty())
                {
                    WritablePropertyMap propertyMap = PropertyManager.getWritableProperties(DATABASE_AUTHENTICATION_CATEGORY_KEY, true);
                    propertyMap.clear();
                    map.forEach((key, value)->propertyMap.put(key.getPropertyName(), value.getValue()));
                    propertyMap.save();
                }
            }
        });
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
                LogManager.getLogger(DbLoginAuthenticationProvider.class).info(c + " requests");

            return c % 100 != 0;
        }
    }

    // If this appears to be a browser request then return an AuthenticationResponse that will result in redirect to the change password page.
    private AuthenticationResponse getChangePasswordResponse(DbLoginConfiguration configuration, User user, URLHelper returnURL, FailureReason failureReason)
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

        return AuthenticationResponse.createFailureResponse(configuration, failureReason, redirectURL);
    }
}
