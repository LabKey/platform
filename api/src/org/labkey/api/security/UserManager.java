/*
 * Copyright (c) 2005-2010 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.beans.PropertyChangeListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class UserManager
{
    private static Logger _log = Logger.getLogger(UserManager.class);
    private static CoreSchema _core = CoreSchema.getInstance();

    // NOTE: This static map will slowly grow, since user IDs & timestamps are added and never removed.  It's a trivial amount of data, though.
    private static final Map<Integer, Long> _activeUsers = new HashMap<Integer, Long>(100);

    private final static String _userListLookup = "UserList";
    private final static String _userObjectListLookup = "UserObjectList";
    private static final String USER_PREF_MAP = "UserPreferencesMap";
    private static final String USER_REQUIRED_FIELDS = "UserInfoRequiredFields";
    public static final String USER_AUDIT_EVENT = "UserAuditEvent";

    private static Long _userCount = null;

    //
    // UserListener
    //

    public interface UserListener extends PropertyChangeListener
    {
        void userAddedToSite(User user);

        void userDeletedFromSite(User user);

        void userAccountDisabled(User user);

        void userAccountEnabled(User user);
    }

    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    private static final List<UserListener> _listeners = new CopyOnWriteArrayList<UserListener>();

    public static void addUserListener(UserListener listener)
    {
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
                _log.error("fireAddPrincipalToGroup", t);
            }
        }
    }

    protected static List<Throwable> fireDeleteUser(User user)
    {
        List<UserListener> list = getListeners();
        List<Throwable> errors = new ArrayList<Throwable>();

        for (UserListener userListener : list)
        {
            try
            {
                userListener.userDeletedFromSite(user);
            }
            catch (Throwable t)
            {
                _log.error("fireDeletePrincipalFromGroup", t);
                errors.add(t);
            }
        }
        return errors;
    }

    protected static List<Throwable> fireUserDisabled(User user)
    {
        List<UserListener> list = getListeners();
        List<Throwable> errors = new ArrayList<Throwable>();

        for (UserListener userListener : list)
        {
            try
            {
                userListener.userAccountDisabled(user);
            }
            catch (Throwable t)
            {
                _log.error("fireUserDisabled", t);
                errors.add(t);
            }
        }
        return errors;
    }

    protected static List<Throwable> fireUserEnabled(User user)
    {
        List<UserListener> list = getListeners();
        List<Throwable> errors = new ArrayList<Throwable>();

        for (UserListener userListener : list)
        {
            try
            {
                userListener.userAccountEnabled(user);
            }
            catch (Throwable t)
            {
                _log.error("fireUserEnabled", t);
                errors.add(t);
            }
        }
        return errors;
    }

    public static User getUser(int userId)
    {
        if (userId == User.guest.getUserId())
            return User.guest;

        User user = (User)DbCache.get(_core.getTableInfoUsers(), "" + userId);
        if (null == user)
            user = Table.selectObject(_core.getTableInfoUsers(), userId, User.class);

        if (null == user)
            return null;

        DbCache.put(_core.getTableInfoUsers(), "" + userId, user, CacheManager.HOUR);

        // these should really be readonly
        return user.cloneUser();
    }


    public static User getUser(ValidEmail email)
    {
        User user = null;

        try
        {
            // TODO: Index on Principals.Name?
            User[] users = Table.executeQuery(_core.getSchema(), "SELECT * FROM " + _core.getTableInfoUsers() + " WHERE Email=?", new Object[]{email.getEmailAddress()}, User.class);

            if (0 < users.length)
                user = users[0];
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return user;
    }


    public static User getUserByDisplayName(String displayName)
    {
        User user = null;

        try
        {
            User[] users = Table.select(
                    _core.getTableInfoUsers(),
                    Table.ALL_COLUMNS,
                    new SimpleFilter("DisplayName", displayName),
                    null,
                    User.class
            );

            if (0 < users.length)
                user = users[0];
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return user;
    }


    public static void updateActiveUser(User user)
    {
        synchronized(_activeUsers)
        {
            _activeUsers.put(user.getUserId(), System.currentTimeMillis());
        }
    }


    private static void removeActiveUser(User user)
    {
        synchronized(_activeUsers)
        {
            _activeUsers.remove(user.getUserId());
        }
    }


    /** Includes users that have logged in during any server session */
    public static int getActiveUserCount(Date since)
    {
        try
        {
            return Table.executeSingleton(_core.getSchema(), "SELECT COUNT(*) FROM " + _core.getTableInfoUsersData() + " WHERE LastLogin >= ?", new Object[] { since }, Integer.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    /** Returns users that have logged in during this server session */
    public static List<Pair<String, Long>> getActiveUsers(long since)
    {
        synchronized(_activeUsers)
        {
            long now = System.currentTimeMillis();
            List<Pair<String, Long>> recentUsers = new ArrayList<Pair<String, Long>>(_activeUsers.size());

            for (int id : _activeUsers.keySet())
            {
                long lastActivity = _activeUsers.get(id);

                if (lastActivity >= since)
                {
                    User user = getUser(id);
                    String display = user != null ? user.getEmail() : "" + id;
                    recentUsers.add(new Pair<String, Long>(display, (now - lastActivity)/60000));
                }
            }

            // Sort by number of minutes
            Collections.sort(recentUsers, new Comparator<Pair<String, Long>>()
                {
                    public int compare(Pair<String, Long> o1, Pair<String, Long> o2)
                    {
                        return (o1.second).compareTo(o2.second);
                    }
                }
            );

            return recentUsers;
        }
    }


    public static User getGuestUser()
    {
        return User.guest;
    }


    // Return display name if user id != null and user exists, otherwise return null
    public static String getDisplayName(Integer userId, ViewContext context)
    {
        return getDisplayName(userId, false, context);
    }


    // If userIdIfDeleted = true, then return "<userId>" if user doesn't exist
    public static String getDisplayNameOrUserId(Integer userId, ViewContext context)
    {
        return getDisplayName(userId, true, context);        
    }


    private static String getDisplayName(Integer userId, boolean userIdIfDeleted, ViewContext context)
    {
        if (userId == null)
            return null;

        if(User.guest.getUserId() == userId.intValue())
            return "Guest";

        User user = getUser(userId.intValue());
        if (user == null)
        {
            if (userIdIfDeleted)
                return "<" + userId + ">";
            else
                return null;
        }

        return user.getDisplayNameOld(context);
    }


    public static String getEmailForId(Integer userId)
    {
        if (userId == null)
            return null;

        if(User.guest.getUserId() == userId.intValue())
            return "Guest";

        User user = getUser(userId);
        return null != user ? user.getEmail() : null;
    }


    public static User[] getActiveUsers() throws SQLException
    {
        User[] userList = (User[]) DbCache.get(_core.getTableInfoActiveUsers(),_userObjectListLookup);
        if (userList != null)
            return userList;
        userList = Table.select(_core.getTableInfoActiveUsers(), Table.ALL_COLUMNS, null, new Sort("Email"), User.class);
        DbCache.put(_core.getTableInfoActiveUsers(),_userObjectListLookup, userList, CacheManager.HOUR);
        return userList;
    }


    private static Map<Integer, UserName> getUserEmailDisplayNameMap()
    {
        Map<Integer, UserName> userList = (Map<Integer, UserName>) DbCache.get(_core.getTableInfoActiveUsers(), _userListLookup);

        if (null == userList)
        {
            userList = new HashMap<Integer, UserName>((int) (getUserCount() * 1.333));

            ResultSet rs = null;

            try
            {
                rs = Table.executeQuery(_core.getSchema(), "SELECT UserId, Email, DisplayName FROM " + _core.getTableInfoActiveUsers(), new Object[]{});

                while (rs.next())
                {
                    UserName userName = new UserName(rs.getString("Email"), rs.getString("DisplayName"));
                    userList.put(rs.getInt("UserId"), userName);
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            finally
            {
                if (rs != null)
                {
                    try
                    {
                        rs.close();
                    }
                    catch(SQLException e)
                    {
                        _log.error("Error closing ResultSet", e);
                    }
                }
            }

            DbCache.put(_core.getTableInfoActiveUsers(), _userListLookup, userList, CacheManager.HOUR);
        }

        return userList;
    }


    private static class UserName
    {
        private String _email;
        private String _displayName;

        public UserName(String email, String displayName)
        {
            _email = email;
            _displayName = displayName;
        }

        /**
         * Returns the display name of this user. Requires a ViewContext
         * in order to check if the current web browser is logged in.
         * We then filter the display name for guests, stripping out the @domain.com
         * if it is an email address.
         *
         * @param context
         * @return The diplay name, possibly sanitized
         */
        public String getDisplayName(ViewContext context)
        {
            if (context == null || context.getUser() != null && context.getUser().isGuest())
            {
                return sanitizeEmailAddress(_displayName);
            }
            return _displayName;
        }

        public String getEmail()
        {
            return _email;
        }
    }


    public static List<String> getUserEmailList()
    {
        Map<Integer, UserName> m = getUserEmailDisplayNameMap();
        List<String> list = new ArrayList<String>();
        for (UserName userName : m.values())
            list.add(userName.getEmail());

        Collections.sort(list);
        return list;
    }


    static void clearUserList(int userId)
    {
        DbCache.remove(_core.getTableInfoActiveUsers(),_userListLookup);
        DbCache.remove(_core.getTableInfoActiveUsers(),_userObjectListLookup);
        if (0 != userId)
            DbCache.remove(_core.getTableInfoUsers(), "" + userId);
        _userCount = null;
    }


    public static boolean userExists(ValidEmail email)
    {
        User user = getUser(email);
        return (null != user);
    }


    public static String sanitizeEmailAddress(String email)
    {
        if (email == null)
            return email;
        int index = email.indexOf('@');
        if (index != -1)
        {
            email = email.substring(0,index);
        }
        return email;
    }


    public static void addToUserHistory(User principal, String message) throws SQLException
    {
        User user = UserManager.getGuestUser();
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
        catch (RuntimeException e){}

        AuditLogService.get().addEvent(user, c, UserManager.USER_AUDIT_EVENT, principal.getUserId(), message);
    }


    public static boolean hasNoUsers()
    {
        return 0 == getUserCount();
    }

    public static int getUserCount(Date registeredBefore) throws SQLException
    {
        return Table.executeSingleton(_core.getSchema(), "SELECT COUNT(*) FROM " + _core.getTableInfoUsersData() + " WHERE Created <= ?", new Object[] { registeredBefore }, Integer.class);
    }

    public static int getUserCount()
    {
        if (null == _userCount)
        {
            try
            {
                _userCount = Table.rowCount(_core.getTableInfoActiveUsers());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        return _userCount.intValue();
    }


    public static String validGroupName(String name, String type)
    {
        if (null == name || name.length() == 0)
            return "Name cannot be empty";
        if (!name.trim().equals(name))
            return "Name should not start or end with whitespace";

        GroupManager.PrincipalType pt = GroupManager.PrincipalType.forChar(type.charAt(0));
        if (null == pt)
            throw new IllegalArgumentException("Unknown principal type: '" + type + "'");
        
        switch (pt)
        {
            // USER
            case USER:
                throw new IllegalArgumentException("User names are not allowed");

            // GROUP (regular project or global)
            case ROLE:
            case GROUP:
                // see renameGroup.jsp if you change this
                if (!StringUtils.containsNone(name, "@./\\-&~_"))
                    return "Group name should not contain punctuation.";
                break;

            // MODULE MANAGED
            case MODULE:
                // no validation, HOWEVER must be UNIQUE
                // recommended start with @ or look like a GUID
                // must contain punctuation, but not look like email
                break;

            default:
                throw new IllegalArgumentException("Unknown principal type: '" + type + "'");
        }
        return null;
    }


    public static void updateLogin(User user)
    {
        try
        {
            Table.execute(_core.getSchema(), "UPDATE " + _core.getTableInfoUsersData() + " SET LastLogin=? WHERE UserId=?", new Object[]{new Date(), new Integer(user.getUserId())});
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    public static void updateUser(User currentUser, Map<String, Object> typedValues, Object pkVal) throws SQLException
    {
        typedValues.put("phone", PageFlowUtil.formatPhoneNo((String) typedValues.get("phone")));
        typedValues.put("mobile", PageFlowUtil.formatPhoneNo((String) typedValues.get("mobile")));
        typedValues.put("pager", PageFlowUtil.formatPhoneNo((String) typedValues.get("pager")));
        Table.update(currentUser, _core.getTableInfoUsers(), typedValues, pkVal);
        clearUserList(currentUser.getUserId());

        User principal = UserManager.getUser((Integer)pkVal);
        if (principal != null)
        {
            addToUserHistory(principal, "Contact information for " + principal.getEmail() + " was updated");
        }
    }


    public static String changeEmail(int userId, ValidEmail oldEmail, ValidEmail newEmail, User currentUser)
    {
        if (null != getUser(newEmail))
            return newEmail + " already exists.";

        if (SecurityManager.isLdapEmail(oldEmail) != SecurityManager.isLdapEmail(newEmail))
            return "Can't switch between LDAP and non-LDAP users.";

        try
        {
            Table.execute(_core.getSchema(), "UPDATE " + _core.getTableInfoPrincipals() + " SET Name=? WHERE UserId=?", new Object[]{newEmail.getEmailAddress(), userId});

            if (SecurityManager.loginExists(oldEmail))
                Table.execute(_core.getSchema(), "UPDATE " + _core.getTableInfoLogins() + " SET Email=? WHERE Email=?", new Object[]{newEmail.getEmailAddress(), oldEmail.getEmailAddress()});

            UserManager.addToUserHistory(UserManager.getUser(userId), currentUser + " changed email from " + oldEmail.getEmailAddress() + " to " + newEmail.getEmailAddress() + ".");
            clearUserList(userId);
        }
        catch (SQLException e)
        {
            _log.error("changeEmail: " + e);
            return (e.getMessage());
        }

        return null;
    }


    public static void deleteUser(int userId) throws SecurityManager.UserManagementException
    {
        User user = getUser(userId);
        if (null == user)
            return;

        removeActiveUser(user);

        List<Throwable> errors = fireDeleteUser(user);

        if (errors.size() != 0)
        {
            Throwable first = errors.get(0);
            if (first instanceof RuntimeException)
                throw (RuntimeException)first;
            else
                throw new RuntimeException(first);
        }

        try
        {
            Table.execute(_core.getSchema(), "DELETE FROM " + _core.getTableInfoRoleAssignments() + " WHERE UserId=?", new Object[]{userId});            
            Table.execute(_core.getSchema(), "DELETE FROM " + _core.getTableInfoMembers() + " WHERE UserId=?", new Object[]{userId});
            Table.execute(_core.getSchema(), "DELETE FROM " + _core.getTableInfoUserHistory() + " WHERE UserId=?", new Object[]{userId});
            // TODO: now that user history is managed by the audit service, should we allow audit records to be deleted?
            UserManager.addToUserHistory(user, user.getEmail() + " was deleted from the system");

            Table.execute(_core.getSchema(), "DELETE FROM " + _core.getTableInfoUsersData() + " WHERE UserId=?", new Object[]{userId});
            Table.execute(_core.getSchema(), "DELETE FROM " + _core.getTableInfoLogins() + " WHERE Email=?", new Object[]{user.getEmail()});
            Table.execute(_core.getSchema(), "DELETE FROM " + _core.getTableInfoPrincipals() + " WHERE UserId=?", new Object[]{userId});
        }
        catch (SQLException e)
        {
            _log.error("deleteUser: " + e);
            throw new SecurityManager.UserManagementException(user.getEmail(), e);
        }
        finally
        {
            clearUserList(user.getUserId());
        }
    }

    public static void setUserActive(User currentUser, int userIdToAdjust, boolean active) throws SecurityManager.UserManagementException
    {
        setUserActive(currentUser, getUser(userIdToAdjust), active);
    }

    public static void setUserActive(User currentUser, User userToAdjust, boolean active) throws SecurityManager.UserManagementException
    {
        if (null == userToAdjust)
            return;

        //no-op if active state is not actually changed
        if(userToAdjust.isActive() == active)
            return;

        removeActiveUser(userToAdjust);

        Integer userId = new Integer(userToAdjust.getUserId());

        List<Throwable> errors = active ? fireUserEnabled(userToAdjust) : fireUserDisabled(userToAdjust);

        if (errors.size() != 0)
        {
            Throwable first = errors.get(0);
            if (first instanceof RuntimeException)
                throw (RuntimeException)first;
            else
                throw new RuntimeException(first);
        }
        try
        {
            Table.update(currentUser, CoreSchema.getInstance().getTableInfoPrincipals(),
                    Collections.singletonMap("Active", active), userId);
            addToUserHistory(userToAdjust, "User account " + userToAdjust.getEmail() + " was " + 
                    (active ? "re-enabled" : "disabled"));
        }
        catch(SQLException e)
        {
            _log.error("setUserActive: " + e);
            throw new SecurityManager.UserManagementException(userToAdjust.getEmail(), e);
        }
        finally
        {
            clearUserList(userId);
        }
    }

    public static String getRequiredUserFields()
    {
        Map<String, String> map = getUserPreferences(false);
        return map.get(USER_REQUIRED_FIELDS);
    }

    public static void setRequiredUserFields(String requiredFields) throws SQLException
    {
        Map<String, String> map = getUserPreferences(true);
        map.put(USER_REQUIRED_FIELDS, requiredFields);
        PropertyManager.saveProperties(map);
    }

    public static @NotNull Map<String, String> getUserPreferences(boolean writable)
    {
        if (writable)
            return PropertyManager.getWritableProperties(USER_PREF_MAP, true);
        else
            return PropertyManager.getProperties(USER_PREF_MAP);
    }

    // Get completions from list of all site users
    public static List<AjaxCompletion> getAjaxCompletions(String prefix, ViewContext context) throws SQLException
    {
        return UserManager.getAjaxCompletions(prefix, Arrays.asList(UserManager.getActiveUsers()), context);
    }

    // Get completions from specified list of users
    public static List<AjaxCompletion> getAjaxCompletions(String prefix, Collection<User> users, ViewContext context)
    {
        List<AjaxCompletion> completions = new ArrayList<AjaxCompletion>();

        if (prefix != null && prefix.length() != 0)
        {
            String lowerPrefix = prefix.toLowerCase();
            for (User user : users)
            {
                final String fullName = StringUtils.defaultString(user.getFirstName()) + " " + StringUtils.defaultString(user.getLastName());
                if (fullName.toLowerCase().startsWith(lowerPrefix) ||
                    user.getLastName() != null && user.getLastName().toLowerCase().startsWith(lowerPrefix))
                {
                    String display;
                    if (user.getFirstName() != null || user.getLastName() != null)
                    {
                        StringBuilder builder = new StringBuilder();
                        builder.append(StringUtils.trimToEmpty(user.getFirstName())).append(" ").
                                append(StringUtils.trimToEmpty(user.getLastName()));
                        builder.append(" (").append(user.getEmail()).append(")");
                        display = builder.toString();
                    }
                    else
                        display = user.getEmail();
                    completions.add(new AjaxCompletion(display, user.getEmail()));
                }
                else if (user.getDisplayNameOld(context).compareToIgnoreCase(user.getEmail()) != 0 &&
                        user.getDisplayNameOld(context).toLowerCase().startsWith(lowerPrefix))
                {
                    StringBuilder builder = new StringBuilder();
                    builder.append(user.getDisplayNameOld(context)).append(" ");
                    builder.append(" (").append(user.getEmail()).append(")");
                    completions.add(new AjaxCompletion(builder.toString(), user.getEmail()));
                }
                else if (user.getEmail() != null && user.getEmail().toLowerCase().startsWith(lowerPrefix))
                {
                    completions.add(new AjaxCompletion(user.getEmail(), user.getEmail()));
                }
            }
        }

        return completions;
    }

    public static boolean mayWriteScript(User user)
    {
        //user may write script if user is a site admin or in the developers group
        return user.isAdministrator() || user.isDeveloper();
    }
}
