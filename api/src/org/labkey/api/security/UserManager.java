/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.util.Cache;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.common.util.Pair;

import java.beans.PropertyChangeListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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
    private static UserDetailsURLFactory _userDetailsURLFactory;

    public static SimpleFilter createSimpleFilter(String colName, Object value)
    {
        return new SimpleFilter(colName, value);
    }


    //
    // UserListener
    //

    public interface UserDetailsURLFactory
    {
        ActionURL getURL(int userId);
    }

    public static void registerUserDetailsURLFactory(UserDetailsURLFactory factory)
    {
        if (null != _userDetailsURLFactory)
            throw new IllegalStateException("User details URL factory has already been set");

        _userDetailsURLFactory = factory;
    }

    public static ActionURL getUserDetailsUrl(int userId)
    {
        if (null == _userDetailsURLFactory)
            throw new IllegalStateException("User details URL factory has not been set");

        return _userDetailsURLFactory.getURL(userId);
    }

    public interface UserListener extends PropertyChangeListener
    {
        void userAddedToSite(User user);

        void userDeletedFromSite(User user);
    }

    private static final ArrayList<UserListener> _listeners = new ArrayList<UserListener>();

    public static void addUserListener(UserListener listener)
    {
        synchronized (_listeners)
        {
            _listeners.add(listener);
        }
    }

    protected static UserListener[] getListeners()
    {
        synchronized (_listeners)
        {
            return _listeners.toArray(new UserListener[0]);
        }
    }

    protected static void fireAddUser(User user)
    {
        UserListener[] list = getListeners();
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
        UserListener[] list = getListeners();
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


    public static User getUser(int userId)
    {
        if (userId == User.guest.getUserId())
            return User.guest;

        User user = (User)DbCache.get(_core.getTableInfoUsers(), "" + userId);
        if (null == user)
            user = Table.selectObject(_core.getTableInfoUsers(), userId, User.class);

        if (null == user)
            return null;

        DbCache.put(_core.getTableInfoUsers(), "" + userId, user, Cache.HOUR);

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
            return Table.executeSingleton(_core.getSchema(), "SELECT COUNT(*) FROM " + _core.getTableInfoUsersData() + " WHERE LastLogin >= ?", new Object[] { since }, Integer.class, true);
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

        if(User.guest.getUserId() == userId)
            return "Guest";

        Map<Integer, UserName> userList = getUserEmailDisplayNameMap();

        UserName userName = userList.get(userId);

        if (userName == null)
        {
            if (userIdIfDeleted)
                return "<" + userId + ">";
            else
                return null;
        }

        return userName.getDisplayName(context);
    }


    public static String getEmailForId(Integer userId)
    {
        if (userId == null)
            return null;

        if(User.guest.getUserId() == userId.intValue())
            return "Guest";

        Map<Integer, UserName> userList = getUserEmailDisplayNameMap();

        UserName userName = userList.get(userId);
        if (userName == null)
            return null;
        else
            return userName.getEmail();
    }


    public static User[] getAllUsers() throws SQLException
    {
        User[] userList = (User[]) DbCache.get(_core.getTableInfoUsers(),_userObjectListLookup);
        if (userList != null)
            return userList;
        userList = Table.select(_core.getTableInfoUsers(), Table.ALL_COLUMNS, null, new Sort("Email"), User.class);
        DbCache.put(_core.getTableInfoUsers(),_userObjectListLookup, userList, Cache.HOUR);
        return userList;
    }


    private static Map<Integer, UserName> getUserEmailDisplayNameMap()
    {
        Map<Integer, UserName> userList = (Map<Integer, UserName>) DbCache.get(_core.getTableInfoUsers(), _userListLookup);

        if (null == userList)
        {
            userList = new HashMap<Integer, UserName>((int) (getUserCount() * 1.333));

            ResultSet rs = null;

            try
            {
                rs = Table.executeQuery(_core.getSchema(), "SELECT UserId, Email, DisplayName FROM " + _core.getTableInfoUsers(), new Object[]{});

                while (rs.next())
                {
                    UserName userName = new UserName(rs.getString("Email"), rs.getString("DisplayName"));
                    userList.put(rs.getInt("UserId"), userName);
                }
            }
            catch (SQLException e)
            {
                _log.error(e);
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

            DbCache.put(_core.getTableInfoUsers(), _userListLookup, userList, Cache.HOUR);
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
            if (context.getUser().isGuest())
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

        Collections.sort(list); // TODO: Use a sorted List?
        return list;
    }


    private static void clearUserList(int userId)
    {
        DbCache.remove(_core.getTableInfoUsers(),_userListLookup);
        DbCache.remove(_core.getTableInfoUsers(),_userObjectListLookup);
        if (0 != userId)
            DbCache.remove(_core.getTableInfoUsers(), "" + userId);
        _userCount = null;
    }


    public static boolean userExists(ValidEmail email)
    {
        User user = getUser(email);
        return (null != user);
    }


    // Create new rows for this user in Principals and UsersData tables
    // If rows already exist we'll simply return the existing user
    public static User createUser(ValidEmail email) throws SQLException
    {
        Integer userId = null;

        // Add row to Principals
        Map<String, Object> fieldsIn = new HashMap<String, Object>();
        fieldsIn.put("Name", email.getEmailAddress());
        fieldsIn.put("Type", "u");
        try
        {
            Map returnMap = Table.insert(null, _core.getTableInfoPrincipals(), fieldsIn);
            userId = (Integer) returnMap.get("UserId");
        }
        catch (SQLException e)
        {
            if (!"23000".equals(e.getSQLState()))
            {
                _log.debug("createUser: Something failed user: " + email, e);
                throw e;
            }
        }

        try
        {
            // If insert didn't return an id it must already exist... select it
            if (null == userId)
                userId = Table.executeSingleton(_core.getSchema(),
                        "SELECT UserId FROM " + _core.getTableInfoPrincipals() + " WHERE Name = ?",
                        new Object[]{email.getEmailAddress()}, Integer.class);
        }
        catch (SQLException x)
        {
            _log.debug("createUser: Something failed user: " + email, x);
            throw x;
        }

        if (null == userId)
        {
            assert false : "User should either exist or not; synchronization problem?";
            _log.debug("createUser: Something failed user: " + email);
            return null;
        }

        //
        // Add row to UsersData table
        //
        try
        {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("UserId", userId);
            // By default, use just the username portion of an email address
            // for the display name. Users can change it when they log in for
            // the first time.
            m.put("DisplayName", email.getEmailAddress());
            Table.insert(null, _core.getTableInfoUsersData(), m);
        }
        catch (SQLException x)
        {
            if (!"23000".equals(x.getSQLState()))
            {
                _log.debug("createUser: Something failed user: " + email, x);
                throw x;
            }
        }

        clearUserList(userId);

        User user = getUser(userId.intValue());
        fireAddUser(user);

        return user;
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
        try {
            ViewContext context = HttpView.currentContext();
            if (context != null)
            {
                user = context.getUser();
            }
        }
        catch (RuntimeException e){}
        AuditLogService.get().addEvent(user, null, UserManager.USER_AUDIT_EVENT, principal.getUserId(), message);
        //Table.insert(user, _core.getTableInfoUserHistory(), PageFlowUtil.map("Date", new Date(), "UserId", user.getUserId(), "Message", message));
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
                _userCount = Table.rowCount(_core.getTableInfoUsers());
            }
            catch (SQLException e)
            {
                _log.error(e);
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

        switch (type.charAt(0))
        {
            // USER
            case 'u':
                throw new IllegalArgumentException("User names are not allowed");

            // GROUP (regular project or global)
            case 'g':
                if (!StringUtils.containsNone(name, "@./\\-&~_"))
                    return "Group name should not contain punctuation.";
                break;

            // MODULE MANAGED
            case 'm':
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
            _log.error("Setting LastLogin for " + user + e);
        }
    }


    public static void updateUser(User currentUser, Map<String, Object> typedValues, Object pkVal) throws SQLException
    {
        typedValues.put("phone", PageFlowUtil.formatPhoneNo((String) typedValues.get("phone")));
        typedValues.put("mobile", PageFlowUtil.formatPhoneNo((String) typedValues.get("mobile")));
        typedValues.put("pager", PageFlowUtil.formatPhoneNo((String) typedValues.get("pager")));
        Table.update(currentUser, _core.getTableInfoUsers(), typedValues, pkVal, null);
        clearUserList(currentUser.getUserId());

        User principal = UserManager.getUser((Integer)pkVal);
        if (principal != null)
        {
            addToUserHistory(principal, "Contact information for: " + principal.getEmail() + " was updated");
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

            if (!SecurityManager.isLdapEmail(newEmail))
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
        deleteUser(getUser(userId));
    }


    public static void deleteUser(ValidEmail email) throws SecurityManager.UserManagementException
    {
        deleteUser(getUser(email));
    }


    private static void deleteUser(User user) throws SecurityManager.UserManagementException
    {
        if (null == user)
            return;

        removeActiveUser(user);

        Integer userId = new Integer(user.getUserId());

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

    public static String getRequiredUserFields()
    {
        Map<String, String> map = getUserPreferences(false);
        if (map != null)
            return (String)map.get(USER_REQUIRED_FIELDS);
        return null;
    }

    public static void setRequiredUserFields(String requiredFields) throws SQLException
    {
        Map<String, String> map = getUserPreferences(true);
        map.put(USER_REQUIRED_FIELDS, requiredFields);
        PropertyManager.saveProperties(map);
    }

    public static Map<String, String> getUserPreferences(boolean writable)
    {
        if (writable)
            return PropertyManager.getWritableProperties(0, ContainerManager.getRoot().getId(), USER_PREF_MAP, true);
        else
            return PropertyManager.getProperties(ContainerManager.getRoot().getId(), USER_PREF_MAP, false);
    }

    // Get completions from list of all site users
    public static List<AjaxCompletion> getAjaxCompletions(String prefix, ViewContext context) throws SQLException
    {
        return UserManager.getAjaxCompletions(prefix, UserManager.getAllUsers(), context);
    }

    // Get completions from specified list of users
    public static List<AjaxCompletion> getAjaxCompletions(String prefix, User[] users, ViewContext context)
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
                else if (user.getDisplayName(context).compareToIgnoreCase(user.getEmail()) != 0 &&
                        user.getDisplayName(context).toLowerCase().startsWith(lowerPrefix))
                {
                    StringBuilder builder = new StringBuilder();
                    builder.append(user.getDisplayName(context)).append(" ");
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

    public static boolean mayWriteScript(User user, Container c)
    {
        return user.isAdministrator();
//        return user.isAdministrator() || c.hasPermission(user, ACL.PERM_ADMIN);
//        return false;
    }
}
