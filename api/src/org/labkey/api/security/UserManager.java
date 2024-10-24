/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.CommitTaskOption;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.security.SecurityManager.UserManagementException;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.ApplicationAdminPermission;
import org.labkey.api.security.permissions.CanImpersonatePrivilegedSiteRolesPermission;
import org.labkey.api.security.permissions.SiteAdminPermission;
import org.labkey.api.security.roles.ApplicationAdminRole;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Link;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.labkey.api.security.SecurityManager.USER_ID_KEY;
import static org.labkey.api.security.permissions.AbstractActionPermissionTest.APPLICATION_ADMIN_EMAIL;
import static org.labkey.api.security.permissions.AbstractActionPermissionTest.SITE_ADMIN_EMAIL;

public class UserManager
{
    private static final Logger LOG = LogHelper.getLogger(UserManager.class, "User management operations");
    private static final CoreSchema CORE = CoreSchema.getInstance();

    // NOTE: This static map will slowly grow, since user IDs & timestamps are added and never removed. It's a trivial amount of data, though.
    private static final Map<Integer, Long> RECENT_USERS = new HashMap<>(100);
    public static final String GROUP_NAME_CHAR_EXCLUSION_LIST = "@/\\&~";  // see renameGroup.jsp if you change this

    public static final String USER_AUDIT_EVENT = "UserAuditEvent";
    public static final int VALID_GROUP_NAME_LENGTH = 64;
    public static final int VERIFICATION_EMAIL_TIMEOUT = 60 * 24;  // in minutes, one day currently

    public static final Comparator<User> USER_DISPLAY_NAME_COMPARATOR = Comparator.comparing(User::getFriendlyName, String.CASE_INSENSITIVE_ORDER);


    /**
     * Listener for user account related notifications. Typically registered during a module's startup via a call to
     * {@link #addUserListener(UserListener)}
     */
    public interface UserListener extends PropertyChangeListener
    {
        /** Fires when a user account is first created */
        default void userAddedToSite(User user) {}

        /** Fires when a user account is being completely deleted from the server */
        default void userDeletedFromSite(User user) {}

        /** Fires when a user account is being disabled, which prevents them from logging in but retains information associated with their account */
        default void userAccountDisabled(User user) {}

        /** Fires when a user account is being enabled, which allows them to log in again */
        default void userAccountEnabled(User user) {}

        /** Tell the user what side effects will happen if the user account is deactivated */
        default List<HtmlString> previewUserAccountDeactivated(User user) { return Collections.emptyList(); }

        /** Tell the user what side effects will happen if the user account is completely deleted */
        default List<HtmlString> previewUserAccountDeleted(User user) { return Collections.emptyList(); }

        /** Fires when a user's information has been changed, such as their first name */
        default void userPropertiesUpdated(int userid) {}

        @Override
        default void propertyChange(PropertyChangeEvent evt) {}
    }

    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    private static final List<UserListener> _listeners = new CopyOnWriteArrayList<>();

    /** Adds a listener to be notified when user account actions happen */
    public static void addUserListener(UserListener listener)
    {
        addUserListener(listener, false);
    }

    /** Adds a listener with option to specify that it needs to be executed before the other listeners */
    public static void addUserListener(UserListener listener, boolean meFirst)
    {
        if (meFirst)
            _listeners.add(0, listener);
        else
            _listeners.add(listener);
    }

    private static List<UserListener> getListeners()
    {
        return _listeners;
    }

    protected static void fireAddUser(User user)
    {
        List<UserListener> list = getListeners();
        for (UserListener userListener : list)
        {
            try
            {
                userListener.userAddedToSite(user);
            }
            catch (Throwable t)
            {
                LOG.error("fireAddPrincipalToGroup", t);
            }
        }
    }

    protected static List<Throwable> fireDeleteUser(User user)
    {
        List<UserListener> list = getListeners();
        List<Throwable> errors = new ArrayList<>();

        for (UserListener userListener : list)
        {
            try
            {
                userListener.userDeletedFromSite(user);
            }
            catch (Throwable t)
            {
                LOG.error("fireDeletePrincipalFromGroup", t);
                errors.add(t);
            }
        }
        return errors;
    }

    public static List<HtmlString> previewUserAccountDeleted(User user)
    {
        List<HtmlString> result = new ArrayList<>();
        for (UserListener listener : _listeners)
        {
            result.addAll(listener.previewUserAccountDeleted(user));
        }
        return result;
    }

    public static List<HtmlString> previewUserAccountDeactivated(User user)
    {
        List<HtmlString> result = new ArrayList<>();
        for (UserListener listener : _listeners)
        {
            result.addAll(listener.previewUserAccountDeactivated(user));
        }
        return result;
    }

    protected static List<Throwable> fireUserDisabled(User user)
    {
        List<UserListener> list = getListeners();
        List<Throwable> errors = new ArrayList<>();

        for (UserListener userListener : list)
        {
            try
            {
                userListener.userAccountDisabled(user);
            }
            catch (Throwable t)
            {
                LOG.error("fireUserDisabled", t);
                errors.add(t);
            }
        }
        return errors;
    }

    protected static List<Throwable> fireUserEnabled(User user)
    {
        List<UserListener> list = getListeners();
        List<Throwable> errors = new ArrayList<>();

        for (UserListener userListener : list)
        {
            try
            {
                userListener.userAccountEnabled(user);
            }
            catch (Throwable t)
            {
                LOG.error("fireUserEnabled", t);
                errors.add(t);
            }
        }
        return errors;
    }

    public static List<Throwable> fireUserPropertiesChanged(int userid)
    {
        List<UserListener> list = getListeners();
        List<Throwable> errors = new ArrayList<>();

        for (UserListener userListener : list)
        {
            try
            {
                userListener.userPropertiesUpdated(userid);
            }
            catch (Throwable t)
            {
                LOG.error("fireUserUpdated", t);
                errors.add(t);
            }
        }
        return errors;
    }

    public interface UserDetailsButtonProvider
    {
        void addButton(ButtonBar bb, Container c, User currentUser, User detailsUser, ActionURL returnUrl);
    }

    public enum UserDetailsButtonCategory
    {
        Authentication, // Place custom button after the built-in authentication buttons ("Reset Password", "Delete Password", "Change Password", etc.)
        Account,        // Place custom button after the built-in account buttons ("Change Email", "Deactivate", etc.)
        Permissions     // Place custom button after the built-in permissions buttons ("View Permissions", "Clone Permissions", etc.)
    }

    private static final Map<UserDetailsButtonCategory, List<UserDetailsButtonProvider>> USER_DETAILS_BUTTON_PROVIDERS = new EnumMap<>(UserDetailsButtonCategory.class);

    static
    {
        // Map each button category to a list of providers. This approach should be both thread-safe and performant,
        // particularly for read operations (which is what we care about).
        Arrays.stream(UserDetailsButtonCategory.values())
            .forEach(cat -> USER_DETAILS_BUTTON_PROVIDERS.put(cat, new CopyOnWriteArrayList<>()));
    }

    /**
     * Register a provider that can choose to add a button to the User Details button bar.
     * @param category A UserDetailsButtonCategory that determines where on the button bar the button will appear
     * @param provider A UserDetailsButtonProvider that's invoked with appropriate context every time a User Details
     *                 button bar is rendered. The provider must perform its own permissions checks on the current user
     *                 as well as other checks to determine if showing the button is appropriate.
     */
    public static void registerUserDetailsButtonProvider(UserDetailsButtonCategory category, UserDetailsButtonProvider provider)
    {
        USER_DETAILS_BUTTON_PROVIDERS.get(category).add(provider);
    }

    public static void addCustomButtons(UserDetailsButtonCategory category, ButtonBar bb, Container c, User currentUser, User detailsUser, ActionURL returnUrl)
    {
        USER_DETAILS_BUTTON_PROVIDERS.get(category).forEach(provider -> provider.addButton(bb, c, currentUser, detailsUser, returnUrl));
    }

    public static @Nullable User getUser(int userId)
    {
        if (userId == User.guest.getUserId())
            return User.guest;

        return UserCache.getUser(userId);
    }

    public static @Nullable User getUser(ValidEmail email)
    {
        return UserCache.getUser(email);
    }

    public static @Nullable User getUserByDisplayName(String displayName)
    {
        return UserCache.getUser(displayName);
    }

    public static List<User> getSiteAdmins()
    {
        return SecurityManager.getUsersWithPermissions(ContainerManager.getRoot(), Set.of(SiteAdminPermission.class));
    }

    public static List<User> getAppAdmins()
    {
        return SecurityManager.getUsersWithPermissions(ContainerManager.getRoot(), Set.of(ApplicationAdminPermission.class));
    }

    public static void updateRecentUser(User user)
    {
        synchronized(RECENT_USERS)
        {
            RECENT_USERS.put(user.getUserId(), HeartBeat.currentTimeMillis());
        }
    }

    private static void removeRecentUser(User user)
    {
        synchronized(RECENT_USERS)
        {
            RECENT_USERS.remove(user.getUserId());
        }
    }

    // Includes users who have logged in during any server session
    public static int getRecentUserCount(Date since)
    {
        return new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT COUNT(*) FROM " + CORE.getTableInfoUsersData() + " WHERE LastLogin >= ?", since)).getObject(Integer.class);
    }

    private static final Comparator<Pair<String, Long>> RECENT_USER_COMPARATOR = Comparator.comparing(Pair<String, Long>::getValue).thenComparing(Pair::getKey);

    /** Returns all users who have logged in during this server session since the specified interval */
    public static List<Pair<String, Long>> getRecentUsers(long since)
    {
        synchronized(RECENT_USERS)
        {
            long now = System.currentTimeMillis();
            List<Pair<String, Long>> recentUsers = new ArrayList<>(RECENT_USERS.size());

            for (int id : RECENT_USERS.keySet())
            {
                long lastActivity = RECENT_USERS.get(id);

                if (lastActivity >= since)
                {
                    User user = getUser(id);
                    String display = user != null ? user.getEmail() : "" + id;
                    recentUsers.add(new Pair<>(display, (now - lastActivity)/60000));
                }
            }

            // Sort by number of minutes, then user email
            recentUsers.sort(RECENT_USER_COMPARATOR);

            return recentUsers;
        }
    }

    public static Long getMinutesSinceMostRecentUserActivity()
    {
        synchronized(RECENT_USERS)
        {
            Optional<Long> mostRecent = RECENT_USERS.values().stream().min(Comparator.naturalOrder());
            return mostRecent.isPresent() ?
                    TimeUnit.MILLISECONDS.toMinutes(HeartBeat.currentTimeMillis() - mostRecent.get().longValue()) :
                    null;
        }
    }

    private enum LoggedInOrOut {in, out}

    @NotNull
    private static TableSelector getRecentLoginOrOuts(LoggedInOrOut inOrOut, @Nullable Date since, @Nullable TableInfo userAuditTable, @Nullable Collection<ColumnInfo> cols)
    {
        SimpleFilter f = new SimpleFilter(FieldKey.fromParts("Comment"), "logged " + inOrOut.toString(), CompareType.CONTAINS);
        if (since != null)
        {
            f.addCondition(FieldKey.fromParts("Created"), since, CompareType.GTE);
        }
        if (null == userAuditTable)
            userAuditTable = getUserAuditSchemaTableInfo();
        if (null == cols)
            return new TableSelector(userAuditTable, f, null);
        else
            return new TableSelector(userAuditTable, cols, f, null);
    }

    @NotNull
    private static SchemaTableInfo getUserAuditSchemaTableInfo()
    {
        return StorageProvisioner.get().getSchemaTableInfo(AuditLogService.get().getAuditProvider(USER_AUDIT_EVENT).getDomain());
    }

    public static long getRecentLoginCount(Date since)
    {
        return getRecentLoginOrOuts(LoggedInOrOut.in, since, null, null).getRowCount();
    }

    @Nullable
    public static Date getMostRecentLogin()
    {
        TableInfo uat = getUserAuditSchemaTableInfo();
        FieldKey createdFk = FieldKey.fromParts("Created");
        ColumnInfo createdCol = uat.getColumn(createdFk);
        Aggregate maxLoginValue = new Aggregate(createdFk, Aggregate.BaseType.MAX, null, true);

        TableSelector logins = getRecentLoginOrOuts(LoggedInOrOut.in, null, uat, Collections.singleton(createdCol));
        Aggregate.Result result = logins.getAggregates(Collections.singletonList(maxLoginValue)).get(createdCol.getName()).get(0);
        return (Date) result.getValue();
    }

    public static long getRecentLogOutCount(Date since)
    {
        return getRecentLoginOrOuts(LoggedInOrOut.out, since, null, null).getRowCount();
    }

    public static int getActiveDaysCount(Date since)
    {
        TableInfo uat = getUserAuditSchemaTableInfo();
        FieldKey createdFk = FieldKey.fromParts("Created");
        ColumnInfo datePartCol = new ExprColumn(uat, createdFk, new SQLFragment("CAST(Created AS DATE)"), JdbcType.DATE, uat.getColumn(createdFk));
        Aggregate countDistinctDates = new Aggregate(datePartCol.getFieldKey(), Aggregate.BaseType.COUNT, null, true);

        TableSelector logins = getRecentLoginOrOuts(LoggedInOrOut.in, since, uat, Collections.singleton(datePartCol));
        Aggregate.Result result = logins.getAggregates(Collections.singletonList(countDistinctDates)).get(datePartCol.getName()).get(0);
        return Math.toIntExact((long) result.getValue());
    }

    /** @return the number of unique users who have authenticated since the provided date (or for all time if null).
     * Uses data from audit logs so archiving or truncating the audit data will reduce the counts reported */
    public static int getAuthCount(@Nullable Date since, boolean excludeSystemUsers, boolean apiKeyOnly, boolean distinct)
    {
        TableInfo uat = getUserAuditSchemaTableInfo();
        SQLFragment sql = new SQLFragment("SELECT COUNT(");
        sql.append(distinct ?  "DISTINCT uat.CreatedBy" : "*");
        sql.append(") FROM ");
        sql.append(uat, "uat");
        if (excludeSystemUsers)
        {
            sql.append(" INNER JOIN ");
            sql.append(CoreSchema.getInstance().getTableInfoUsersData(), "ud");
            sql.append(" ON uat.CreatedBy = ud.UserId ");
            sql.append(" WHERE ud.System = ? ");
            sql.add(false);
        }
        else
        {
            // Make string concat easy
            sql.append(" WHERE 1=1 ");
        }

        sql.append(" AND uat.Comment LIKE ");
        sql.appendStringLiteral("%" + UserAuditEvent.LOGGED_IN + "%", uat.getSqlDialect());


        if (apiKeyOnly)
        {
            sql.append(" AND uat.Comment LIKE ");
            sql.appendStringLiteral("%" + UserAuditEvent.API_KEY + "%", uat.getSqlDialect());
        }

        if (since != null)
        {
            sql.append(" AND uat.Created >= ?");
            sql.add(since);
        }

        return new SqlSelector(uat.getSchema(), sql).getObject(Integer.class);
    }

    /** Of authenticated users, tallied when their session ends */
    private static final AtomicLong _sessionCount = new AtomicLong();
    /** In minutes */
    private static final AtomicLong _totalSessionDuration = new AtomicLong();

    private static final Set<String> _activeSessions = Collections.synchronizedSet(new HashSet<>());

    public static void ensureSessionTracked(HttpSession s)
    {
        if (s != null)
        {
            Integer userId = (Integer)s.getAttribute(USER_ID_KEY);
            if (null != userId && getGuestUser().getUserId() != userId)
               _activeSessions.add(s.getId());
        }
    }

    public static class SessionListener implements HttpSessionListener
    {
        @Override
        public void sessionCreated(HttpSessionEvent event)
        {
            // We don't do anything with guest users, and we can rely on AuthFilter to call ensureSessionTracked().
        }

        @Override
        public void sessionDestroyed(HttpSessionEvent event)
        {
            _activeSessions.remove(event.getSession().getId());

            // Issue 44761 - track session duration for authenticated users
            User user = SecurityManager.getSessionUser(event.getSession());
            if (user != null)
            {
                long duration = TimeUnit.MILLISECONDS.toMinutes(event.getSession().getLastAccessedTime() - event.getSession().getCreationTime());
                LOG.debug("Adding session duration to tally for " + user.getEmail() + ", " + duration + " minutes");
                _sessionCount.incrementAndGet();
                _totalSessionDuration.addAndGet(duration);
            }
        }
    }

    public static int getActiveUserSessionCount()
    {
        return _activeSessions.size();
    }

    public static Integer getAverageSessionDuration()
    {
        return _sessionCount.get() == 0 ? null : (int)(_totalSessionDuration.get() / _sessionCount.get());
    }

    public static User getGuestUser()
    {
        return User.guest;
    }

    // Return display name if user id != null and user exists, otherwise return null
    public static String getDisplayName(Integer userId, User currentUser)
    {
        return getDisplayName(userId, false, currentUser);
    }

    // If userIdIfDeleted = true, then return "<userId>" if user doesn't exist
    public static String getDisplayNameOrUserId(Integer userId, User currentUser)
    {
        return getDisplayName(userId, true, currentUser);
    }

    private static String getDisplayName(Integer userId, boolean userIdIfDeleted, User currentUser)
    {
        if (userId == null)
            return null;

        if (User.guest.getUserId() == userId)
            return "Guest";

        User user = getUser(userId);

        if (user == null)
        {
            if (userIdIfDeleted)
                return "<" + userId + ">";
            else
                return null;
        }

        return user.getDisplayName(currentUser);
    }

    public static String getEmailForId(Integer userId)
    {
        if (userId == null)
            return null;

        if (User.guest.getUserId() == userId)
            return "Guest";

        User user = getUser(userId);
        return null != user ? user.getEmail() : null;
    }

    @NotNull
    public static Collection<User> getActiveUsers()
    {
        return getUsers(false);
    }

    @NotNull
    public static Collection<User> getUsers(boolean includeInactive)
    {
        return includeInactive ? UserCache.getActiveAndInactiveUsers() : UserCache.getActiveUsers() ;
    }

    public static List<Integer> getUserIds()
    {
        return UserCache.getUserIds();
    }

    public static Map<ValidEmail, User> getUserEmailMap()
    {
        return UserCache.getUserEmailMap();
    }

    public static void clearUserList()
    {
        UserCache.clear();
    }

    public static boolean userExists(ValidEmail email)
    {
        User user = getUser(email);
        return (null != user);
    }

    public static String sanitizeEmailAddress(String email)
    {
        if (email == null)
            return null;
        int index = email.indexOf('@');
        if (index != -1)
        {
            email = email.substring(0,index);
        }
        return email;
    }

    public static void addToUserHistory(User principal, String message)
    {
        User user = getGuestUser();
        Container c = ContainerManager.getRoot();

        try
        {
            ViewContext context = HttpView.currentContext();

            if (context != null)
            {
                user = context.getUser();
                c = context.getContainer();
            }
        }
        catch (RuntimeException ignored){}

        addAuditEvent(user, c, principal, message);
    }

    public static boolean hasUsers()
    {
        return getActiveUserCount() > 0;
    }

    public static boolean hasNoRealUsers()
    {
        return 0 == getActiveRealUserCount();
    }

    public static int getUserCount(Date registeredBefore)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Created"), registeredBefore, CompareType.LTE);
        return (int)new TableSelector(CORE.getTableInfoUsersData(), filter, null).getRowCount();
    }

    /** @return the number of user accounts, not including deactivated users */
    public static int getActiveUserCount()
    {
        return UserCache.getActiveUserCount();
    }

    public static int getActiveRealUserCount()
    {
        return UserCache.getActiveRealUserCount();
    }

    /** Active users who are marked as "system" users, i.e., excluded from user limits **/
    public static int getSystemUserCount()
    {
        return UserCache.getSystemUserCount();
    }

    public static String validGroupName(String name, @NotNull PrincipalType type)
    {
        if (null == name || name.isEmpty())
            return "Name cannot be empty";
        if (!name.trim().equals(name))
            return "Name should not start or end with whitespace";

        switch (type)
        {
            // USER
            case USER -> throw new IllegalArgumentException("User names are not allowed");

            // GROUP (regular project or global)
            case ROLE, GROUP ->
            {
                // see renameGroup.jsp if you change this
                if (!StringUtils.containsNone(name, GROUP_NAME_CHAR_EXCLUSION_LIST))
                    return "Group name should not contain punctuation.";
                if (name.length() > VALID_GROUP_NAME_LENGTH) // issue 14147
                    return "Name value is too long, maximum length is " + VALID_GROUP_NAME_LENGTH + " characters, but supplied value was " + name.length() + " characters.";
            }

            // MODULE MANAGED
            case MODULE ->
            {
            }
            // no validation, HOWEVER must be UNIQUE
            // recommended start with @ or look like a GUID
            // must contain punctuation, but not look like email
            default -> throw new IllegalArgumentException("Unknown principal type: '" + type + "'");
        }
        return null;
    }

    /** Record that a user logged in */
    public static void updateLogin(User user)
    {
        SQLFragment sql = new SQLFragment("UPDATE " + CORE.getTableInfoUsersData() + " SET LastLogin = ? WHERE UserId = ?", new Date(), user.getUserId());
        new SqlExecutor(CORE.getSchema()).execute(sql);
    }

    /** Clear the ExpirationDate field for the given user */
    public static void clearExpirationDate(User adminUser, User expiringUser)
    {
        Table.update(adminUser, CORE.getTableInfoUsersData(), new HashMap<>(){{put("ExpirationDate", null);}}, expiringUser.getUserId());
        clearUserList();
    }

    /**
     * Updates a user's basic account information
     * @param currentUser user to use to determine the display name for the user to be updated
     * @param toUpdate the user object that is being updated
     */
    public static void updateUser(@Nullable User currentUser, User toUpdate)
    {
        Map<String, Object> typedValues = new HashMap<>();
        typedValues.put("phone", PageFlowUtil.formatPhoneNo(toUpdate.getPhone()));
        typedValues.put("mobile", PageFlowUtil.formatPhoneNo(toUpdate.getMobile()));
        typedValues.put("pager", PageFlowUtil.formatPhoneNo(toUpdate.getPager()));
        typedValues.put("im", toUpdate.getIM());

        if (currentUser != null && !currentUser.isGuest())
            typedValues.put("displayName", toUpdate.getDisplayName(currentUser));

        typedValues.put("firstName", toUpdate.getFirstName());
        typedValues.put("lastName", toUpdate.getLastName());
        typedValues.put("description", toUpdate.getDescription());

        Table.update(currentUser, CORE.getTableInfoUsers(), typedValues, toUpdate.getUserId());
        clearUserList();

        addToUserHistory(toUpdate, "Contact information for " + toUpdate.getEmail() + " was updated");
    }

    public static void requestEmailChange(User userToChange, ValidEmail requestedEmail, String verificationToken, User currentUser) throws UserManagementException
    {
        if (SecurityManager.loginExists(userToChange.getEmail()))
        {
            DbScope scope = CORE.getSchema().getScope();
            try (Transaction transaction = scope.ensureTransaction())
            {
                Instant timeoutDate = Instant.now().plus(VERIFICATION_EMAIL_TIMEOUT, ChronoUnit.MINUTES);
                SqlExecutor executor = new SqlExecutor(CORE.getSchema());
                int rows = executor.execute("UPDATE " + CORE.getTableInfoLogins() + " SET RequestedEmail = ?, Verification = ?, VerificationTimeout = ? WHERE Email = ?",
                        requestedEmail.getEmailAddress(), verificationToken, Date.from(timeoutDate), userToChange.getEmail());
                if (1 != rows)
                    throw new UserManagementException(requestedEmail, "Unexpected number of rows returned when setting verification: " + rows);
                addToUserHistory(userToChange, currentUser + " requested email address change from " + userToChange.getEmail() + " to " + requestedEmail +
                        " with token '" + verificationToken + "' and timeout date '" + Date.from(timeoutDate) + "'.");
                transaction.commit();
            }
        }
    }

    public static void changeEmail(User currentUser, User userToChange, boolean isAdmin, String newEmail, String verificationToken)
            throws UserManagementException, ValidEmail.InvalidEmailException
    {
        // make sure these emails are valid, and also have been processed (like changing to lowercase)

        String oldEmail = userToChange.getEmail();
        newEmail = new ValidEmail(newEmail).getEmailAddress();

        DbScope scope = CORE.getSchema().getScope();
        try (Transaction transaction = scope.ensureTransaction())
        {
            if (!isAdmin)
            {
                if (!getVerifyEmail(oldEmail).isVerified(verificationToken))  // shouldn't happen! should be testing this earlier too
                {
                    throw new UserManagementException(oldEmail, "Verification token '" + verificationToken + "' is incorrect for email change for user " + oldEmail);
                }
            }

            SqlExecutor executor = new SqlExecutor(CORE.getSchema());
            int rows = executor.execute("UPDATE " + CORE.getTableInfoPrincipals() + " SET Name = ? WHERE UserId = ?", newEmail, userToChange.getUserId());
            if (1 != rows)
                throw new UserManagementException(oldEmail, "Unexpected number of rows returned when setting new name: " + rows);

            executor.execute("UPDATE " + CORE.getTableInfoLogins() + " SET Email = ? WHERE Email = ?", newEmail, oldEmail);  // won't update if non-LabKey-managed, because there is no data here
            if (isAdmin)
            {
                addToUserHistory(userToChange, "Admin " + currentUser + " changed an email address from " + oldEmail + " to " + newEmail + ".");
            }
            else
            {
                addToUserHistory(userToChange, currentUser + " changed their email address from " + oldEmail + " to " + newEmail + " with token '" + verificationToken + "'.");
            }

            if (userToChange.getDisplayName(userToChange).equals(oldEmail))
            {
                rows = executor.execute("UPDATE " + CORE.getTableInfoUsersData() + " SET DisplayName = ? WHERE UserId = ?", newEmail, userToChange.getUserId());
                if (1 != rows)
                    throw new UserManagementException(oldEmail, "Unexpected number of rows returned when setting new display name: " + rows);
            }

            ValidEmail validNewEmail = new ValidEmail(newEmail);
            if (SecurityManager.loginExists(validNewEmail))
            {
                SecurityManager.setVerification(validNewEmail, null);  // so we don't let user use this link again
            }

            transaction.commit();
        }

        clearUserList();
    }

    public static void auditEmailTimeout(int userId, String oldEmail, String newEmail, String verificationToken, User currentUser)
    {
        addToUserHistory(getUser(userId), currentUser + " tried to change an email address from " + oldEmail + " to " + newEmail +
                " with token '" + verificationToken + "', but the verification link was timed out.");
    }

    public static void auditBadVerificationToken(int userId, String oldEmail, String newEmail, String verificationToken, User currentUser)
    {
        addToUserHistory(getUser(userId), currentUser + " tried to change an email address from " + oldEmail + " to " + newEmail +
                " with token '" + verificationToken + "', but the verification token for that email address was not correct.");
    }

    public static VerifyEmail getVerifyEmail(String email)
    {
        SqlSelector sqlSelector = new SqlSelector(CORE.getSchema(), "SELECT Email, RequestedEmail, Verification, VerificationTimeout FROM " + CORE.getTableInfoLogins()
                + " WHERE Email = ?", email);
        return sqlSelector.getObject(VerifyEmail.class);
    }

    public static class VerifyEmail
    {
        private String _email;
        private String _requestedEmail;
        private String _verification;
        private Date _verificationTimeout;

        public String getEmail()
        {
            return _email;
        }

        @SuppressWarnings("unused")
        public void setEmail(String email)
        {
            _email = email;
        }

        public String getRequestedEmail()
        {
            return _requestedEmail;
        }

        @SuppressWarnings("unused")
        public void setRequestedEmail(String requestedEmail)
        {
            _requestedEmail = requestedEmail;
        }

        public String getVerification()
        {
            return _verification;
        }

        @SuppressWarnings("unused")
        public void setVerification(String verification)
        {
            _verification = verification;
        }

        public Date getVerificationTimeout()
        {
            return _verificationTimeout;
        }

        @SuppressWarnings("unused")
        public void setVerificationTimeout(Date verificationTimeout)
        {
            _verificationTimeout = verificationTimeout;
        }

        public boolean isVerified(String userProvidedToken)
        {
            return userProvidedToken != null && userProvidedToken.equals(_verification);
        }
    }

    public static void deleteUser(int userId) throws UserManagementException
    {
        User user = getUser(userId);
        if (null == user)
            return;

        removeRecentUser(user);

        List<Throwable> errors = fireDeleteUser(user);

        if (!errors.isEmpty())
        {
            Throwable first = errors.get(0);
            if (first instanceof RuntimeException)
                throw (RuntimeException)first;
            else
                throw new RuntimeException(first);
        }

        try (Transaction transaction = CORE.getScope().ensureTransaction())
        {
            boolean needToEnsureRootAdmins = SecurityManager.isRootAdmin(user);

            SqlExecutor executor = new SqlExecutor(CORE.getSchema());
            executor.execute("DELETE FROM " + CORE.getTableInfoRoleAssignments() + " WHERE UserId=?", userId);
            executor.execute("DELETE FROM " + CORE.getTableInfoMembers() + " WHERE UserId=?", userId);
            addToUserHistory(user, user.getEmail() + " was deleted from the system");

            executor.execute("DELETE FROM " + CORE.getTableInfoUsersData() + " WHERE UserId=?", userId);
            executor.execute("DELETE FROM " + CORE.getTableInfoLogins() + " WHERE Email=?", user.getEmail());
            executor.execute("DELETE FROM " + CORE.getTableInfoPrincipals() + " WHERE UserId=?", userId);
            executor.execute("DELETE FROM " + CORE.getTableAPIKeys() + " WHERE CreatedBy=?", userId);

            OntologyManager.deleteOntologyObject(user.getEntityId(), ContainerManager.getSharedContainer(), true);

            // Clear user list immediately (before the last root admin check) and again after commit/rollback
            transaction.addCommitTask(UserManager::clearUserList, CommitTaskOption.IMMEDIATE, CommitTaskOption.POSTCOMMIT, CommitTaskOption.POSTROLLBACK);

            if (needToEnsureRootAdmins)
                SecurityManager.ensureAtLeastOneRootAdminExists();

            transaction.commit();
        }
        catch (Exception e)
        {
            LOG.error("deleteUser: " + e);
            throw new UserManagementException(user.getEmail(), e);
        }

        //TODO: Delete User files
    }

    public static void setUserActive(User currentUser, int userIdToAdjust, boolean active) throws UserManagementException
    {
        setUserActive(currentUser, getUser(userIdToAdjust), active);
    }

    public static void setUserActive(User currentUser, User userToAdjust, boolean active) throws UserManagementException
    {
        setUserActive(currentUser, userToAdjust, active, "");
    }

    public static void setUserActive(User currentUser, User userToAdjust, boolean active, String extendedMessage) throws UserManagementException
    {
        if (null == userToAdjust)
            return;

        //no-op if active state is not actually changed
        if (userToAdjust.isActive() == active)
            return;

        if (active && LimitActiveUsersService.get().isUserLimitReached())
            throw new UserManagementException(userToAdjust.getEmail(), "User limit has been reached so no more users can be reactivated on this deployment.");

        Integer userId = userToAdjust.getUserId();

        List<Throwable> errors = active ? fireUserEnabled(userToAdjust) : fireUserDisabled(userToAdjust);

        if (!errors.isEmpty())
        {
            Throwable first = errors.get(0);
            if (first instanceof RuntimeException)
                throw (RuntimeException)first;
            else
                throw new RuntimeException(first);
        }

        try (Transaction transaction = CoreSchema.getInstance().getScope().ensureTransaction())
        {
            Table.update(currentUser, CoreSchema.getInstance().getTableInfoPrincipals(),
                    Collections.singletonMap("Active", active), userId);

            Map<String, Object> map = new HashMap<>();

            // Treat re-activation as an activity for the purpose of inactivity tracking, Issue 47471
            if (active)
            {
                Timestamp now = new Timestamp(System.currentTimeMillis());
                map.put("LastActivity", now);
            }

            // Call update unconditionally to ensure Modified & ModifiedBy are always updated
            Table.update(currentUser, CoreSchema.getInstance().getTableInfoUsers(), map, userId);

            // Clear user list immediately (before the last root admin check) and again after commit/rollback
            transaction.addCommitTask(UserManager::clearUserList, CommitTaskOption.IMMEDIATE, CommitTaskOption.POSTCOMMIT, CommitTaskOption.POSTROLLBACK);

            // If deactivating a root admin, ensure at least one root admin remains
            if (!active && SecurityManager.isRootAdmin(userToAdjust))
                SecurityManager.ensureAtLeastOneRootAdminExists();

            removeRecentUser(userToAdjust);

            addToUserHistory(userToAdjust, "User account " + userToAdjust.getEmail() + " was " +
                    (active ? "reactivated" : "deactivated") + " " + extendedMessage
            );

            transaction.commit();
        }
        catch(RuntimeSQLException e)
        {
            LOG.error("setUserActive: " + e);
            throw new UserManagementException(userToAdjust.getEmail(), e);
        }
    }

    /**
     * Returns the ajax completion objects for the specified groups and users.
     */
    public static List<AjaxCompletion> getAjaxCompletions(Collection<Group> groups, Collection<User> users, User currentUser, Container c)
    {
        List<AjaxCompletion> completions = new ArrayList<>();

        for (Group group : groups)
        {
            if (group.getName() != null)
            {
                String display = (group.getContainer() == null ? "Site: " : "") + group.getName();
                completions.add(new AjaxCompletion(display, group.getName()));
            }
        }

        if (!users.isEmpty())
            completions.addAll(getAjaxCompletions(users, currentUser, c));
        return completions;
    }

    /**
     * Returns the ajax completion objects for the specified users.
     */
    public static List<AjaxCompletion> getAjaxCompletions(Collection<User> users, User currentUser, Container c)
    {
        List<AjaxCompletion> completions = new ArrayList<>();

        boolean showEmailAddresses =  SecurityManager.canSeeUserDetails(c, currentUser);
        for (User user : users)
        {
            final String fullName = StringUtils.defaultString(user.getFirstName()) + " " + StringUtils.defaultString(user.getLastName());
            String completionValue = user.getAutocompleteName(c, currentUser);

            // Output the most human-friendly names possible for the pick list, and append the email addresses if the user has permission to see them

            if (!StringUtils.isBlank(fullName))
            {
                StringBuilder builder = new StringBuilder();
                builder.append(StringUtils.trimToEmpty(user.getFirstName())).append(" ").
                        append(StringUtils.trimToEmpty(user.getLastName()));

                if (showEmailAddresses)
                    builder.append(" (").append(user.getEmail()).append(")");

                completions.add(new AjaxCompletion(builder.toString(), completionValue));
            }
            else if (!user.getDisplayName(currentUser).equalsIgnoreCase(user.getEmail()))
            {
                StringBuilder builder = new StringBuilder();
                builder.append(user.getDisplayName(currentUser));

                if (showEmailAddresses)
                    builder.append(" (").append(user.getEmail()).append(")");

                completions.add(new AjaxCompletion(builder.toString(), completionValue));
            }
            else if (completionValue != null) // Note the only way to get here is if the displayName is the same as the email address
            {
                completions.add(new AjaxCompletion(completionValue));
            }
        }
        return completions;
    }

    public static class UserAuditEvent extends AuditTypeEvent
    {
        public static final String LOGGED_IN = "logged in";
        public static final String LOGGED_OUT = "logged out";
        public static final String API_KEY = "an API key";

        int _user;

        public UserAuditEvent()
        {
            super();
        }

        public UserAuditEvent(String container, String comment, User modifiedUser)
        {
            super(UserManager.USER_AUDIT_EVENT, container, comment);

            if (modifiedUser != null)
                _user = modifiedUser.getUserId();
        }

        public int getUser()
        {
            return _user;
        }

        public void setUser(int user)
        {
            _user = user;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("user", getUserMessageElement(getUser()));
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    /**
     *
     * @param modifiedUser the user id of the principal being modified
     */
    public static void addAuditEvent(User user, Container c,  @Nullable User modifiedUser, String msg)
    {
        UserAuditEvent event = new UserAuditEvent(c.getId(), msg, modifiedUser);
        AuditLogService.get().addEvent(user, event);
    }

    /**
     * Parse an array of email addresses and/or display names into a list of corresponding userIds.
     * Inputs which did not resolve to a current active user are preserved in the output.
     * @param theList Any combination of email addresses and display names
     * @return List of corresponding userIds. Unresolvable inputs are preserved.
     */
    public static List<String> parseUserListInput(String[] theList)
    {
        return parseUserListInput(new LinkedHashSet<>(Arrays.asList(theList)));
    }

    /**
     * Parse a string of delimited email addresses and/or display names into a delimited string of corresponding
     * userIds. Inputs which did not resolve to a current active user are preserved in the output.
     * @param theList Any combination of email addresses and display names, delimited with semicolons or new line characters
     * @return Semi-colon delimited string of corresponding userIds. Unresolvable inputs are preserved.
     */
    public static String parseUserListInput(String theList)
    {
        String[] names = StringUtils.split(StringUtils.trimToEmpty(theList), ";\n");
        return  StringUtils.join(parseUserListInput(names),";");
    }

    /**
     * Parse a set of email addresses and/or display names into a list of corresponding userIds.
     * Inputs which did not resolve to a current active user are preserved in the output.
     * @param theList Any combination of email addresses and display names
     * @return List of corresponding userIds. Unresolvable inputs are preserved.
     */
    public static List<String> parseUserListInput(Set<String> theList)
    {
        ArrayList<String> parsed = new ArrayList<>(theList.size());
        for (String name : theList)
        {
            if (null == (name = StringUtils.trimToNull(name)))
                continue;
            User u = null;
            try { u = getUser(new ValidEmail(name)); } catch (ValidEmail.InvalidEmailException ignored) {}
            if (null == u)
                u = getUserByDisplayName(name);
            parsed.add(null == u ? name : String.valueOf(u.getUserId()));
        }
        return parsed;
    }

    /**
     * Return the HTML tag for the user details page of the displayedUserId.
     * @param container The current container
     * @param currentUser The current logged-in user
     * @param displayedUserId The user id of the url we want to navigate to
     * @return The HTML string to navigate to the displayedUserId's user details page
     */
    public static HtmlString getUserDetailsHTMLLink(Container container, User currentUser, int displayedUserId)
    {
        User displayUser = getUser(displayedUserId);

        boolean isDeletedUser = displayUser == null;

        if (isDeletedUser)
            return HtmlString.of("<" + displayedUserId + ">");

        String displayName = displayUser.getDisplayName(currentUser);
        ActionURL url = getUserDetailsURL(container, currentUser, displayedUserId);

        // currentUser has permissions to see user details of the displayed user
        if (url != null)
        {
            return new Link.LinkBuilder(displayName).href(url).clearClasses().getHtmlString();
        }

        return HtmlString.of(displayName);
    }

    /**
     * Return the ActionURL for the user details page of the displayedUserId.
     * If the user does not have permissions to see the user details page or the displayed
     * user id is a guest, return the URL an empty URL.
     * @param container The current container
     * @param currentUser The current logged in user
     * @param displayedUserId The user id of the url we want to navigate to
     * @return A string URL to navigate to the displayedUserIds user details page.
     */
    public static ActionURL getUserDetailsURL(Container container, User currentUser, Integer displayedUserId)
    {
        if (SecurityManager.canSeeUserDetails(container, currentUser) && !UserIdRenderer.isGuestUserId(displayedUserId))
        {
            if (displayedUserId != null)
            {
                return PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(container, displayedUserId, null);
            }
        }

        return null;
    }

    public static class TestCase extends Assert
    {
        private final static String[] TEST_USERS = new String[]{APPLICATION_ADMIN_EMAIL, SITE_ADMIN_EMAIL};

        private static Map<String, User> _users = Map.of();

        @BeforeClass
        public static void initialize()
        {
            AbstractActionPermissionTest.cleanupUsers(TEST_USERS);
            _users = AbstractActionPermissionTest.createUsers(TEST_USERS);

            Container root = ContainerManager.getRoot();
            MutableSecurityPolicy policy = new MutableSecurityPolicy(root, root.getPolicy());
            policy.addRoleAssignment(_users.get(APPLICATION_ADMIN_EMAIL), ApplicationAdminRole.class);
            policy.addRoleAssignment(_users.get(SITE_ADMIN_EMAIL), SiteAdminRole.class);
            SecurityPolicyManager.savePolicyForTests(policy, TestContext.get().getUser());
        }

        @Test
        public void testPermissionsRetrieval()
        {
            List<User> appAdmins = getAppAdmins();
            assertTrue("Expected " + APPLICATION_ADMIN_EMAIL, appAdmins.contains(_users.get(APPLICATION_ADMIN_EMAIL)));
            assertTrue("Expected all AppAdmins to have application admin permissions",
                appAdmins.stream().allMatch(User::hasApplicationAdminPermission));
            assertTrue("Expected all AppAdmins to have root admin permissions",
                appAdmins.stream().allMatch(User::hasRootAdminPermission));
            assertTrue("Expected all AppAdmins to be active",
                appAdmins.stream().allMatch(User::isActive));

            List<User> siteAdmins = getSiteAdmins();
            assertTrue("Expected " + SITE_ADMIN_EMAIL, siteAdmins.contains(_users.get(SITE_ADMIN_EMAIL)));
            assertTrue("Expected all SiteAdmins to have site admin permissions",
                siteAdmins.stream().allMatch(User::hasSiteAdminPermission));
            assertTrue("Expected all SiteAdmins to have application admin permissions",
                siteAdmins.stream().allMatch(User::hasApplicationAdminPermission));
            assertTrue("Expected all SiteAdmins to have root admin permissions",
                siteAdmins.stream().allMatch(User::hasRootAdminPermission));
            assertTrue("Expected all SiteAdmins to have a privileged role",
                siteAdmins.stream().allMatch(UserPrincipal::hasPrivilegedRole));
            assertTrue("Expected all SiteAdmins to have application admin permissions",
                siteAdmins.stream().allMatch(User::isPlatformDeveloper));
            assertTrue("Expected all SiteAdmins to have trusted analyst permissions",
                siteAdmins.stream().allMatch(User::isTrustedAnalyst));
            assertTrue("Expected all SiteAdmins to have trusted browser dev permissions",
                siteAdmins.stream().allMatch(User::isTrustedBrowserDev));
            assertTrue("Expected all SiteAdmins to have analyst permissions",
                siteAdmins.stream().allMatch(User::isAnalyst));
            assertTrue("Expected all SiteAdmins to be active",
                siteAdmins.stream().allMatch(User::isActive));

            assertTrue("Expected all SiteAdmins to be in the AppAdmins list",
                appAdmins.containsAll(siteAdmins));

            List<User> privilegedUsers = SecurityManager.getUsersWithPermissions(ContainerManager.getRoot(), Set.of(CanImpersonatePrivilegedSiteRolesPermission.class));
            assertTrue("Expected all users with privileged role impersonation permissions to have a privileged role",
                privilegedUsers.stream().allMatch(User::hasPrivilegedRole));

            assertTrue("Expected all SiteAdmins to be in the privileged users list",
                appAdmins.containsAll(siteAdmins));
        }

        @AfterClass
        public static void tearDown()
        {
            AbstractActionPermissionTest.cleanupUsers(TEST_USERS);
        }
    }
}
