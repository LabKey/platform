/*
 * Copyright (c) 2024-2026 LabKey Corporation
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

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.api.view.ActionURL;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

// Handles all CRUD operations on the core.Logins table
public class LoginManager
{
    public static final int TEMP_PASSWORD_LENGTH = 32;
    public static final Duration SETUP_LINK_TIMEOUT = Duration.ofDays(7);
    public static final Duration RESET_LINK_TIMEOUT = Duration.ofDays(1);
    private static final int MAX_HISTORY = 10;
    private static final CoreSchema CORE = CoreSchema.getInstance();

    // Create record for database login, saving UserId and hashed password. Return verification token.
    public static String createLogin(int userId, String email /* Just for logging errors */) throws SecurityManager.UserManagementException
    {
        // Create a placeholder password hash and a separate email verification key that will get emailed to the new user
        String tempPassword = createTempPassword();
        String verification = createTempPassword();

        String crypt = Crypt.MD5.digestWithPrefix(tempPassword);

        try
        {
            int rowCount = new SqlExecutor(CORE.getSchema()).execute("INSERT INTO " + CORE.getTableInfoLogins() +
                " (UserId, Crypt, LastChanged, Verification, VerificationTimeout, PreviousCrypts) VALUES (?, ?, ?, ?, ?, ?)",
                userId, crypt, new Date(), hashVerification(verification), Date.from(Instant.now().plus(SETUP_LINK_TIMEOUT)), crypt);
            if (1 != rowCount)
                throw new SecurityManager.UserManagementException(email, "Login creation statement affected " + rowCount + " rows.");
        }
        catch (DataIntegrityViolationException e)
        {
            throw new SecurityManager.UserAlreadyExistsException(email);
        }

        return verification;
    }

    public static void setPassword(User user, String password) throws SecurityManager.UserManagementException
    {
        String crypt = Crypt.BCrypt.digestWithPrefix(password);
        List<String> history = new ArrayList<>(getCryptHistory(user));
        history.add(crypt);

        // Remember only the last 10 password hashes
        int itemsToDelete = Math.max(0, history.size() - MAX_HISTORY);
        if (itemsToDelete > 0)
        {
            history.subList(0, itemsToDelete).clear();
        }
        String cryptHistory = StringUtils.join(history, ",");

        int rows = new SqlExecutor(CORE.getSchema()).execute("UPDATE " + CORE.getTableInfoLogins() + " SET Crypt=?, LastChanged=?, PreviousCrypts=? WHERE UserId=?", crypt, new Date(), cryptHistory, user.getUserId());
        if (1 != rows)
            throw new SecurityManager.UserManagementException(user.getEmail(), "Password update statement affected " + rows + " rows.");
    }

    private static List<String> getCryptHistory(User user)
    {
        Selector selector = new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT PreviousCrypts FROM " + CORE.getTableInfoLogins() + " WHERE UserId=?", user.getUserId()));
        String cryptHistory = selector.getObject(String.class);

        if (null == cryptHistory)
        {
            return Collections.emptyList();
        }
        else
        {
            List<String> cryptList = Arrays.asList(cryptHistory.split(","));
            assert cryptList.size() <= MAX_HISTORY;

            return cryptList;
        }
    }

    public static boolean matchesPreviousPassword(String password, User user)
    {
        List<String> history = getCryptHistory(user);

        for (String hash : history)
        {
            if (matchPassword(password, hash))
                return true;
        }

        return false;
    }

    public static Date getLastChanged(User user)
    {
        SqlSelector selector = new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT LastChanged FROM " + CORE.getTableInfoLogins() + " WHERE UserId = ?", user.getUserId()));
        return selector.getObject(Date.class);
    }

    // Look up user ID in core.Logins table and return the corresponding password hash
    public static @Nullable String getPasswordHash(@Nullable User user)
    {
        return null != user ? new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT Crypt FROM " + CORE.getTableInfoLogins() + " WHERE UserId = ?", user.getUserId()))
            .getObject(String.class) : null;
    }

    public static boolean loginExists(User user)
    {
        return (null != getPasswordHash(user));
    }

    public static boolean matchPassword(String password, String hash)
    {
        if (StringUtils.isEmpty(hash) || hash.startsWith("disabled:"))
            return false;
        else if (Crypt.BCrypt.acceptPrefix(hash))
            return Crypt.BCrypt.matchesWithPrefix(password, hash);
        else if (Crypt.SaltMD5.acceptPrefix(hash))
            return Crypt.SaltMD5.matchesWithPrefix(password, hash);
        else if (Crypt.MD5.acceptPrefix(hash))
            return Crypt.MD5.matchesWithPrefix(password, hash);
        else
            return Crypt.MD5.matches(password, hash);
    }

    public static String createTempPassword()
    {
        return RandomStringUtils.secureStrong().nextAlphanumeric(TEMP_PASSWORD_LENGTH);
    }

    public static ActionURL createVerificationURL(Container c, User user, String verification, @Nullable List<Pair<String, String>> extraParameters)
    {
        return PageFlowUtil.urlProvider(LoginUrls.class).getVerificationURL(c, user, verification, extraParameters);
    }

    public static ActionURL createModuleVerificationURL(Container c, User user, String verification, @Nullable List<Pair<String, String>> extraParameters, String provider, boolean isAddUser)
    {
        ActionURL defaultUrl = createVerificationURL(c, user, verification, extraParameters);
        if (provider == null)
            return defaultUrl;

        AuthenticationProvider.ResetPasswordProvider urlProvider = AuthenticationManager.getResetPasswordProvider(provider);
        if (urlProvider == null)
            return defaultUrl;

        ActionURL verificationUrl = urlProvider.getAPIVerificationURL(c, isAddUser);
        verificationUrl.addParameter("verification", verification);

        if (null != extraParameters)
            verificationUrl.addParameters(extraParameters);

        return verificationUrl;
    }

    // Test if user has been verified for database authentication
    public static boolean isVerified(User user)
    {
        return null == new SqlSelector(CORE.getSchema(), "SELECT Verification FROM " + CORE.getTableInfoLogins() + " WHERE UserId = ?", user.getUserId()).getObject(String.class);
    }

    // Look up a user based on an unexpired verification token
    public static @Nullable User verify(String verification)
    {
        Integer userId = null;
        if (StringUtils.length(verification) == TEMP_PASSWORD_LENGTH)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Verification"), hashVerification(verification));
            filter.addCondition(FieldKey.fromParts("VerificationTimeout"), new Date(), CompareType.GT);
            userId = new TableSelector(CORE.getTableInfoLogins(), Collections.singleton("UserId"), filter, null)
                .getObject(Integer.class);
        }
        return userId != null ? UserManager.getUser(userId) : null;
    }

    // Store the hash of a new verification token and return the token, which can't be retrieved later
    public static String createVerification(User user, Duration timeout) throws SecurityManager.UserManagementException
    {
        String verification = createTempPassword();
        updateVerification(user, new SQLFragment("Verification = ?, VerificationTimeout = ?", hashVerification(verification), Date.from(Instant.now().plus(timeout))));
        return verification;
    }

    public static void clearVerification(User user) throws SecurityManager.UserManagementException
    {
        // Literal NULLs: a null Date parameter binds as varchar, which PostgreSQL rejects for a timestamp column
        updateVerification(user, new SQLFragment("Verification = NULL, VerificationTimeout = NULL"));
    }

    private static void updateVerification(User user, SQLFragment assignments) throws SecurityManager.UserManagementException
    {
        SQLFragment sql = new SQLFragment("UPDATE " + CORE.getTableInfoLogins() + " SET ").append(assignments).append(" WHERE UserId = ?").add(user.getUserId());
        int rows = new SqlExecutor(CORE.getSchema()).execute(sql);
        if (1 != rows)
            throw new SecurityManager.UserManagementException(user.getEmail(), "Unexpected number of rows returned when setting verification: " + rows);
    }

    // Tokens are long and random, so a fast unsalted hash suffices; core-26.008-26.009.sql hashes legacy rows identically
    private static String hashVerification(String verification)
    {
        return Crypt.SHA256.digestWithPrefix(verification);
    }

    // verification holds the token's hash
    public record VerifyEmail(String requestedEmail, String verification, Date verificationTimeout)
    {
        public boolean isVerified(String userProvidedToken)
        {
            return userProvidedToken != null && verification != null &&
                MessageDigest.isEqual(hashVerification(userProvidedToken).getBytes(StandardCharsets.US_ASCII), verification.getBytes(StandardCharsets.US_ASCII));
        }
    }

    public static VerifyEmail getVerifyEmail(User user)
    {
        SqlSelector sqlSelector = new SqlSelector(CORE.getSchema(), "SELECT RequestedEmail, Verification, VerificationTimeout FROM " + CORE.getTableInfoLogins()
                + " WHERE UserId = ?", user.getUserId());
        return sqlSelector.getObject(VerifyEmail.class);
    }

    public static void requestEmailChange(User userToChange, ValidEmail requestedEmail, String verificationToken, User currentUser) throws SecurityManager.UserManagementException
    {
        if (loginExists(userToChange))
        {
            DbScope scope = CORE.getSchema().getScope();
            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                Instant timeoutDate = Instant.now().plus(UserManager.VERIFICATION_EMAIL_TIMEOUT, ChronoUnit.MINUTES);
                SqlExecutor executor = new SqlExecutor(CORE.getSchema());
                int rows = executor.execute("UPDATE " + CORE.getTableInfoLogins() + " SET RequestedEmail = ?, Verification = ?, VerificationTimeout = ? WHERE UserId = ?",
                        requestedEmail.getEmailAddress(), hashVerification(verificationToken), Date.from(timeoutDate), userToChange.getUserId());
                if (1 != rows)
                    throw new SecurityManager.UserManagementException(requestedEmail, "Unexpected number of rows returned when setting verification: " + rows);
                UserManager.addToUserHistory(userToChange, currentUser + " requested email address change from " + userToChange.getEmail() + " to " + requestedEmail +
                        " with timeout date '" + Date.from(timeoutDate) + "'.");
                transaction.commit();
            }
        }
    }

    // Admins can delete passwords (i.e., entries in the core.Logins table), see #42691
    public static void deleteLoginsRow(@Nullable User userToChange, @Nullable User adminUser)
    {
        if (null != userToChange)
        {
            new SqlExecutor(CoreSchema.getInstance().getScope()).execute("DELETE FROM " + CoreSchema.getInstance().getTableInfoLogins() + " WHERE UserId = ?", userToChange.getUserId());

            if (adminUser != null)
                UserManager.addToUserHistory(userToChange, adminUser.getEmail() + " deleted the password.");
        }
    }

    public static class VerificationMaintenanceTask implements MaintenanceTask
    {
        @Override
        public String getDescription()
        {
            return "Purge expired verification tokens";
        }

        @Override
        public String getName()
        {
            return "PurgeExpiredVerificationTokens";
        }

        @Override
        public void run(Logger log)
        {
            new SqlExecutor(CORE.getSchema()).execute("UPDATE " + CORE.getTableInfoLogins() + " SET Verification = NULL, VerificationTimeout = NULL WHERE VerificationTimeout < ?", new Date());
        }

        @Override
        public boolean canDisable()
        {
            return false;
        }

        @Override
        public boolean hideFromAdminPage()
        {
            return true;
        }
    }
}
