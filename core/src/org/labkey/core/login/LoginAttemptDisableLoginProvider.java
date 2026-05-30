/*
 * Copyright (c) 2026 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationProvider;
import org.labkey.api.security.LoginDisabledException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.util.CountLimiter;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

import static org.labkey.api.security.AuthenticationManager.getEmailCacheKey;

public class LoginAttemptDisableLoginProvider implements AuthenticationProvider.DisableLoginProvider
{
    private static final Logger _log = LogHelper.getLogger(LoginAttemptDisableLoginProvider.class, "Warnings about disabled logins due to too many failures");

    private static final String NAME        = "loginAttemptDisableLogin";
    private static final String DESCRIPTION = "Disable unsuccessful login provider";
    private static final Cache<String, CountLimiter> userLimiter = CacheManager.getCache(10000, CacheManager.DAY, "User login attempt limiter");

    private static volatile CacheLoader<String, CountLimiter> userLoader;

    static
    {
        reloadCache();
    }

    @NotNull
    @Override
    public String getName()
    {
        return NAME;
    }

    @NotNull
    @Override
    public String getDescription()
    {
        return DESCRIPTION;
    }

    @Override
    public boolean isEnabledForUser(String id)
    {
        User user = getUserFromEmailStr(id);
        if (user == null || user.hasRootAdminPermission())
            return false;

        return AuthenticationManager.isLoginAttemptControlEnabled();
    }

    @Override
    public long getUserDelay(String id) throws LoginDisabledException
    {
        CountLimiter rl = userLimiter.get(getEmailCacheKey(id));
        if (rl != null && isLoginDisabled(rl.getLimitReachedTimeStamp()))
        {
            int resetTime = AuthenticationManager.getLoginAttemptResetTime();
            User user = getUserFromEmailStr(id);
            if (user != null)
            {
                String errorMessage = getUserLoginDisabledMsg(user.getEmail());
                AuthenticationManager.addAuditEvent(user, null, errorMessage);
                _log.warn(errorMessage);
                throw new LoginDisabledException("Your login has been disabled. Please try again in " + StringUtilsLabKey.pluralize(resetTime, "minute") + ".");
            }
        }
        return 0;
    }

    public boolean isLoginDisabled(long lastLimitReachedTimestamp)
    {
        long resetMinutes = AuthenticationManager.getLoginAttemptResetTime();
        long now = System.currentTimeMillis();
        return lastLimitReachedTimestamp > 0 && (now - lastLimitReachedTimestamp) < TimeUnit.MINUTES.toMillis(resetMinutes);
    }

    @Override
    public void addUserDelay(HttpServletRequest request, String id, int add)
    {
        CountLimiter rl = userLimiter.get(getEmailCacheKey(id), request, userLoader);
        rl.add(add);
    }

    @Override
    public void resetUserDelay(String id)
    {
        CountLimiter rl = userLimiter.get(getEmailCacheKey(id));
        if (rl != null)
            rl.reset();
    }

    public static void reloadCache()
    {
        if (!AuthenticationManager.isLoginAttemptControlEnabled())
        {
            userLimiter.clear();
            return;
        }

        long attemptSeconds = AuthenticationManager.getLoginAttemptPeriod();
        long attemptCount = AuthenticationManager.getLoginAttemptLimit();

        userLimiter.clear();
        userLoader = (key, _) -> new CountLimiter("User login attempt limiter: " + key, TimeUnit.SECONDS.toMillis(attemptSeconds), 0, attemptCount);
    }

    private User getUserFromEmailStr(String emailStr)
    {
        if (StringUtils.isBlank(emailStr))
            return null;
        ValidEmail email = null;

        try
        {
            email = new ValidEmail(emailStr);
        }
        catch (InvalidEmailException _)
        {
        }

        if (null == email)
            return null;

        return UserManager.getUser(email);
    }

    private String getUserLoginDisabledMsg(String email)
    {
        int resetTime = AuthenticationManager.getLoginAttemptResetTime();
        int attemptLimit = AuthenticationManager.getLoginAttemptLimit();
        int attemptPeriod = AuthenticationManager.getLoginAttemptPeriod();

        return email +
            " disabled from login for " + StringUtilsLabKey.pluralize(resetTime, "minute") + ": " +
            "incorrect password entered " + StringUtilsLabKey.pluralize(attemptLimit, "time") + " in " +
            StringUtilsLabKey.pluralize(attemptPeriod, "second") + ".";
    }
}
