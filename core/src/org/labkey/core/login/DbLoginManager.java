/*
 * Copyright (c) 2010-2026 LabKey Corporation
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
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.security.ApiKeyManager;
import org.labkey.api.security.AuthenticationConfiguration.PrimaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfigurationCache;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.AuthenticationResult;
import org.labkey.api.security.AuthenticationManager.PrimaryAuthenticationResult;
import org.labkey.api.security.AuthenticationSettingsAuditTypeProvider.AuthSettingsAuditEvent;
import org.labkey.api.security.DbLoginService;
import org.labkey.api.security.LoginManager;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.PasswordRule;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserManager.SessionHandler;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.usageMetrics.UsageMetricsProvider;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.core.login.LoginController.SaveDbLoginPropertiesForm;
import org.springframework.validation.BindException;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

import static org.labkey.api.util.IntegerUtils.asInteger;
import static org.labkey.api.security.AuthenticationManager.AuthenticationStatus.Success;

public class DbLoginManager implements DbLoginService
{
    // TODO: Move Logins table operations here

    private static final Logger LOG = LogHelper.getLogger(DbLoginManager.class, "Database login information");

    static final String DATABASE_AUTHENTICATION_CATEGORY_KEY = "DatabaseAuthentication";

    public static DbLoginConfiguration getConfiguration()
    {
        Collection<DbLoginConfiguration> configurations = AuthenticationManager.getActiveConfigurations(DbLoginConfiguration.class);
        if (configurations.size() != 1)
            throw new IllegalStateException("Expected exactly one DbAuthenticationConfiguration, but was: " + configurations.size());

        return configurations.iterator().next();
    }

    @Override
    public PasswordRule getPasswordRule()
    {
        return getConfiguration().getPasswordRule();
    }

    public static PasswordExpiration getPasswordExpiration()
    {
        return getConfiguration().getExpiration();
    }

    @Override
    public AuthenticationResult attemptSetPassword(Container c, User currentUser, String rawPassword, String rawPassword2, HttpServletRequest request, User affectedUser, URLHelper returnUrlHelper, String auditMessage, boolean clearVerification, boolean changeOperation, BindException errors) throws InvalidEmailException
    {
        String password = StringUtils.trimToEmpty(rawPassword);
        String password2 = StringUtils.trimToEmpty(rawPassword2);

        Collection<String> messages = new LinkedList<>();

        if (!getPasswordRule().isValidToStore(password, password2, affectedUser, changeOperation, messages))
        {
            for (String message : messages)
                errors.reject("setPassword", message);
            return null;
        }

        try
        {
            LoginManager.setPassword(affectedUser, password);
            // Invalidate all sessions belonging to this user that were authenticated via database authentication
            UserManager.handleSessionsForUser(affectedUser, new SessionHandler()
            {
                @Override
                public boolean handleSession(HttpSession session)
                {
                    LOG.debug("Checking if session {} used database authentication", session.getId());
                    PrimaryAuthenticationConfiguration<?> configuration = AuthenticationManager.getConfiguration(session);

                    if (configuration instanceof DbLoginConfiguration)
                    {
                        Map<String, Object> map = AuthenticationManager.getAuthenticationProperties(session);
                        Integer apiKeyRowId = asInteger(map.get(ApiKeyManager.API_KEY_ROW_ID));

                        // Don't invalidate API key authentications
                        LOG.debug("Checking if session {} was authenticated via username/password (not an API key)", session.getId());
                        if (apiKeyRowId == null)
                        {
                            LOG.debug("Attempting to invalidate session {}", session.getId());
                            session.invalidate();
                            return true;
                        }
                    }

                    return false;
                }

                @Override
                public void complete(int count)
                {
                    LOG.debug("Invalidated {} for {}.", StringUtilsLabKey.pluralize(count, "session"), affectedUser);
                }
            });
        }
        catch (SecurityManager.UserManagementException e)
        {
            errors.reject("setPassword", "Setting password failed: " + e.getMessage() + ". Contact the " + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getShortName() + " team.");
            return null;
        }

        try
        {
            if (clearVerification)
                LoginManager.setVerification(affectedUser, null);
            UserManager.addToUserHistory(affectedUser, auditMessage);
        }
        catch (SecurityManager.UserManagementException e)
        {
            errors.reject("setPassword", "Resetting verification failed. Contact the " + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getShortName() + " team.");
            return null;
        }

        // The affected user's sessions were all terminated above. If the request's session doesn't exist or is a guest
        // session then log the user in using the just-entered credentials. An admin resetting a password or changing a
        // password while impersonating won't be affected.
        if (SecurityManager.getSessionOwner(request.getSession()) == null)
        {
            PrimaryAuthenticationResult result = AuthenticationManager.authenticate(request, affectedUser.getEmail(), password, returnUrlHelper, true);

            if (result.getStatus() == Success)
            {
                // This user has passed primary authentication
                AuthenticationManager.setPrimaryAuthenticationResult(request, result);
                return AuthenticationManager.handleAuthentication(request, c);
            }
        }

        return new AuthenticationResult(returnUrlHelper);
    }

    public enum Key implements StartupProperty
    {
        Strength()
        {
            @Override
            public String getDescription()
            {
                return "Password strength. Valid values: " + Arrays.toString(PasswordRule.values());
            }
        },
        Expiration()
        {
            @Override
            public String getDescription()
            {
                return "Password expiration. Valid values: " + Arrays.toString(PasswordExpiration.values());
            }
        }
    }

    public static void saveProperties(User user, SaveDbLoginPropertiesForm form)
    {
        Map<String, String> oldProperties = getProperties();

        WritablePropertyMap map = PropertyManager.getWritableProperties(DATABASE_AUTHENTICATION_CATEGORY_KEY, true);
        map.clear();
        map.put(Key.Strength.toString(), form.getStrength());
        map.put(Key.Expiration.toString(), form.getExpiration());
        map.save();
        AuthenticationConfigurationCache.clear();

        String changes = StringUtilsLabKey.getMapDifference(oldProperties, map);

        if (!changes.isEmpty())
        {
            AuthSettingsAuditEvent event = new AuthSettingsAuditEvent("Database authentication settings were changed");
            event.setChanges(String.join(", ", changes));
            AuditLogService.get().addEvent(user, event);
        }
    }

    public static @NotNull Map<String, String> getProperties()
    {
        return PropertyManager.getProperties(DATABASE_AUTHENTICATION_CATEGORY_KEY);
    }

    public static UsageMetricsProvider getMetricsProvider()
    {
        return () -> Map.of("databaseAuthentication", getProperties());
    }
}
