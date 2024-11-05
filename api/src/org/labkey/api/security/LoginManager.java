package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.springframework.dao.DataIntegrityViolationException;

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
    private static final int MAX_HISTORY = 10;
    private static final CoreSchema CORE = CoreSchema.getInstance();
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    // Create record for database login, saving UserId and hashed password. Return verification token.
    public static String createLogin(int userId, ValidEmail email /* Just for logging errors */) throws SecurityManager.UserManagementException
    {
        // Create a placeholder password hash and a separate email verification key that will get emailed to the new user
        String tempPassword = createTempPassword();
        String verification = createTempPassword();

        String crypt = Crypt.MD5.digestWithPrefix(tempPassword);

        try
        {
            // TODO: Stop inserting Email after removing that column
            // Don't need to set LastChanged -- it defaults to current date/time.
            int rowCount = new SqlExecutor(CORE.getSchema()).execute("INSERT INTO " + CORE.getTableInfoLogins() +
                    " (UserId, Email, Crypt, LastChanged, Verification, PreviousCrypts) VALUES (?, ?, ?, ?, ?, ?)",
                    userId, email.getEmailAddress(), crypt, new Date(), verification, crypt);
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

    // Look up email in Logins table and return the corresponding password hash
    @Deprecated
    private static String getPasswordHash(ValidEmail email)
    {
        SqlSelector selector = new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT Crypt FROM " + CORE.getTableInfoLogins() + " WHERE Email = ?", email));
        return selector.getObject(String.class);
    }

    public static @Nullable String getPasswordHash(@Nullable User user)
    {
        return null != user ? new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT Crypt FROM " + CORE.getTableInfoLogins() + " WHERE UserId = ?", user.getUserId()))
            .getObject(String.class) : null;
    }

    @Deprecated
    public static boolean loginExists(ValidEmail email)
    {
        return (null != getPasswordHash(email));
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
        StringBuilder tempPassword = new StringBuilder(TEMP_PASSWORD_LENGTH);

        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++)
            tempPassword.append(PASSWORD_CHARS.charAt((int) Math.floor((Math.random() * PASSWORD_CHARS.length()))));

        return tempPassword.toString();
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
        verificationUrl.addParameter("user", user.getUserId());

        if (null != extraParameters)
            verificationUrl.addParameters(extraParameters);

        return verificationUrl;
    }

    // Test if user has been verified for database authentication. Use only when email address could be invalid ().
    public static boolean isVerified(User user)
    {
        return (null == getVerification(user));
    }

    public static boolean verify(User user, String verification)
    {
        String dbVerification = getVerification(user);
        return (dbVerification != null && dbVerification.equals(verification));
    }

    public static void setVerification(User user, @Nullable String verification) throws SecurityManager.UserManagementException
    {
        int rows = new SqlExecutor(CORE.getSchema()).execute("UPDATE " + CORE.getTableInfoLogins() + " SET Verification = ? WHERE UserId = ?", verification, user.getUserId());
        if (1 != rows)
            throw new SecurityManager.UserManagementException(user.getEmail(), "Unexpected number of rows returned when setting verification: " + rows);
    }

    public static String getVerification(User user)
    {
        return new SqlSelector(CORE.getSchema(), "SELECT Verification FROM " + CORE.getTableInfoLogins() + " WHERE UserId = ?", user.getUserId()).getObject(String.class);
    }

    public record VerifyEmail(String requestedEmail, String verification, Date verificationTimeout)
    {
        public boolean isVerified(String userProvidedToken)
        {
            return userProvidedToken != null && userProvidedToken.equals(verification);
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
                        requestedEmail.getEmailAddress(), verificationToken, Date.from(timeoutDate), userToChange.getUserId());
                if (1 != rows)
                    throw new SecurityManager.UserManagementException(requestedEmail, "Unexpected number of rows returned when setting verification: " + rows);
                UserManager.addToUserHistory(userToChange, currentUser + " requested email address change from " + userToChange.getEmail() + " to " + requestedEmail +
                        " with token '" + verificationToken + "' and timeout date '" + Date.from(timeoutDate) + "'.");
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
}
