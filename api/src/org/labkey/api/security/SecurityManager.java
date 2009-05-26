/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.data.Filter;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.*;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.collections.Cache;
import org.labkey.api.util.Pair;
import org.labkey.api.security.roles.*;
import org.labkey.api.security.permissions.Permission;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Note should consider implementing a Tomcat REALM, but we've tried to avoid
 * being tomcat specific.
 */

public class SecurityManager
{
    private static Logger _log = Logger.getLogger(SecurityManager.class);
    private static CoreSchema core = CoreSchema.getInstance();
    private static final String TERMS_OF_USE_WIKI_NAME = "_termsOfUse";
    private static List<ViewFactory> _viewFactories = new ArrayList<ViewFactory>();
    private static final String GROUP_CACHE_PREFIX = "Groups/MetaData=";

    static
    {
        EmailTemplateService.get().registerTemplate(RegistrationEmailTemplate.class);
        EmailTemplateService.get().registerTemplate(RegistrationAdminEmailTemplate.class);
        EmailTemplateService.get().registerTemplate(PasswordResetEmailTemplate.class);
        EmailTemplateService.get().registerTemplate(PasswordResetAdminEmailTemplate.class);
    }

    public enum PermissionSet
    {
        ADMIN("Admin (all permissions)", ACL.PERM_ALLOWALL),
        EDITOR("Editor", ACL.PERM_READ | ACL.PERM_DELETE | ACL.PERM_UPDATE | ACL.PERM_INSERT),
        AUTHOR("Author", ACL.PERM_READ | ACL.PERM_DELETEOWN | ACL.PERM_UPDATEOWN | ACL.PERM_INSERT),
        READER("Reader", ACL.PERM_READ),
        RESTRICTED_READER("Restricted Reader", ACL.PERM_READOWN),
        SUBMITTER("Submitter", ACL.PERM_INSERT),
        NO_PERMISSIONS("No Permissions", 0);

        private static List<Pair<Integer, String>> _allPerms;
        static {
            _allPerms = new ArrayList<Pair<Integer, String>>();
            _allPerms.add(new Pair<Integer, String>(ACL.PERM_READ, "READ"));
            _allPerms.add(new Pair<Integer, String>(ACL.PERM_INSERT, "INSERT"));
            _allPerms.add(new Pair<Integer, String>(ACL.PERM_UPDATE, "UPDATE"));
            _allPerms.add(new Pair<Integer, String>(ACL.PERM_DELETE, "DELETE"));
            _allPerms.add(new Pair<Integer, String>(ACL.PERM_READOWN, "READ-OWN"));
            _allPerms.add(new Pair<Integer, String>(ACL.PERM_UPDATEOWN, "UPDATE-OWN"));
            _allPerms.add(new Pair<Integer, String>(ACL.PERM_DELETEOWN, "DELETE-OWN"));
            _allPerms.add(new Pair<Integer, String>(ACL.PERM_ADMIN, "ADMIN"));
        }
        private int _permissions;
        private String _label;
        private PermissionSet(String label, int permissions)
        {
            // the following must be true for normalization to work:
            assert ACL.PERM_READOWN == ACL.PERM_READ << 4;
            assert ACL.PERM_UPDATEOWN == ACL.PERM_UPDATE << 4;
            assert ACL.PERM_DELETEOWN == ACL.PERM_DELETE << 4;
            _permissions = permissions;
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }

        /**
         * Returns a label enumerating all the individual ACL perms
         * that the specified permission posseses.
         */
        public static String getLabel(int permissions)
        {
            StringBuffer sb = new StringBuffer();
            String concat = "";

            for (Pair<Integer, String> pair : _allPerms)
            {
                if ((pair.getKey() & permissions) != 0)
                {
                    sb.append(concat);
                    sb.append(pair.getValue());
                    concat = "|";
                }
            }
            return sb.toString();
        }

        public int getPermissions()
        {
            return _permissions;
        }

        public static boolean isPredefinedPermission(int permissions)
        {
            return findPermissionSet(permissions) != null;
        }

        private static int normalizePermissions(int permissions)
        {
            permissions |= (permissions & (ACL.PERM_READ | ACL.PERM_UPDATE | ACL.PERM_DELETE)) << 4;
            return permissions;
        }

        public static PermissionSet findPermissionSet(int permissions)
        {
            for (PermissionSet set : values())
            {
                // we try normalizing because a permissions value with just reader set is equivalent
                // to a permissions value with reader and read_own set.
                if (set.getPermissions() == permissions || normalizePermissions(set.getPermissions()) == permissions)
                    return set;
            }
            return null;
        }
    }


    private SecurityManager()
    {
    }


    private static boolean init = false;

    public static void init()
    {
        if (init)
            return;
        init = true;

        // HACK: I really want to make sure we don't have orphaned Groups.typeProject groups
        //
        // either because
        //  a) the container is non-existant or
        //  b) the container is not longer a project

        scrubTables();

        ContainerManager.addContainerListener(new SecurityContainerListener());
    }


    //
    // GroupListener
    //

    public interface GroupListener extends PropertyChangeListener
    {
        void principalAddedToGroup(Group group, UserPrincipal principal);

        void principalDeletedFromGroup(Group group, UserPrincipal principal);
    }

    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    private static final List<GroupListener> _listeners = new CopyOnWriteArrayList<GroupListener>();

    public static void addGroupListener(GroupListener listener)
    {
        _listeners.add(listener);
    }

    private static List<GroupListener> getListeners()
    {
        return _listeners;
    }

    protected static void fireAddPrincipalToGroup(Group group, UserPrincipal user)
    {
        if (user == null)
            return;
        List<GroupListener> list = getListeners();
        for (GroupListener GroupListener : list)
        {
            try
            {
                GroupListener.principalAddedToGroup(group, user);
            }
            catch (Throwable t)
            {
                _log.error("fireAddPrincipalToGroup", t);
            }
        }
    }

    protected static List<Throwable> fireDeletePrincipalFromGroup(int groupId, UserPrincipal user)
    {
        List<Throwable> errors = new ArrayList<Throwable>();
        if (user == null)
            return errors;

        Group group = getGroup(groupId);

        List<GroupListener> list = getListeners();
        for (GroupListener gl : list)
        {
            try
            {
                gl.principalDeletedFromGroup(group, user);
            }
            catch (Throwable t)
            {
                _log.error("fireDeletePrincipalFromGroup", t);
                errors.add(t);
            }
        }
        return errors;
    }

    private static void scrubTables()
    {
        try
        {
            Container root = ContainerManager.getRoot();

            // missing container
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoPrincipals() + "\n" +
                    "WHERE Container NOT IN (SELECT EntityId FROM " + core.getTableInfoContainers() + ")", null);

            // container is not a project (but should be)
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoPrincipals() + "\n" +
                    "WHERE Type='g' AND Container NOT IN (SELECT EntityId FROM " + core.getTableInfoContainers() + "\n" +
                    "\tWHERE Parent=? OR Parent IS NULL)", new Object[] {root});

            // missing group
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoMembers() + "\n" +
                    "WHERE GroupId NOT IN (SELECT UserId FROM " + core.getTableInfoPrincipals() + ")", null);

            // missing user
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoMembers() + "\n" +
                    "WHERE UserId NOT IN (SELECT UserId FROM " + core.getTableInfoPrincipals() + ")", null);
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
    }


    private static class SecurityContainerListener implements ContainerManager.ContainerListener
    {
        //void wantsToDelete(Container c, List<String> messages);
        public void containerCreated(Container c)
        {
        }

        public void containerDeleted(Container c, User user)
        {
            deleteGroups(c, null);
        }

        public void propertyChange(PropertyChangeEvent evt)
        {
            /* NOTE move is handled by direct call from ContainerManager into SecurityManager */
        }
    }



    // Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
    public static User authenticateBasic(String basic)
    {
        try
        {
            byte[] decode = Base64.decodeBase64(basic.getBytes());
            String auth = new String(decode);
            int colon = auth.indexOf(':');
            if (-1 == colon)
                return null;
            String rawEmail = auth.substring(0, colon);
            String password = auth.substring(colon+1);
            new ValidEmail(rawEmail);  // validate email address
            User u = AuthenticationManager.authenticate(rawEmail, password);
            return u;
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            return null;  // Invalid email means failed auth
        }
    }


    // This user has been authenticated, but may not exist (if user was added to the database and is visiting for the first
    //  time or user authenticated using LDAP, SSO, etc.)
    public static User createUserIfNecessary(ValidEmail email)
    {
        User u = UserManager.getUser(email);

        // If user is authenticated but doesn't exist in our system then
        // add user to the database... must be an LDAP or SSO user's first visit
        if (null == u)
        {
            try
            {
                u = UserManager.createUser(email);
                UserManager.addToUserHistory(u, u.getEmail() + " authenticated successfully and was added to the system automatically.");
            }
            catch (SQLException e)
            {
                // do nothing; we'll fall through and return null.
            }
        }

        if (null != u)
            UserManager.updateLogin(u);

        return u;
    }


    public static final String AUTHENTICATION_METHOD = "SecurityManager.authenticationMethod";

    public static User getAuthenticatedUser(HttpServletRequest request)
    {
        User u = (User) request.getUserPrincipal();
        if (null == u)
        {
            User sessionUser = null;
            HttpSession session = request.getSession(true);

            Integer userId = (Integer) session.getAttribute(USER_ID_KEY);
            if (null != userId)
                sessionUser = UserManager.getUser(userId.intValue());
            if (null != sessionUser)
            {
                Integer impersonatingUserId = (Integer) session.getAttribute(IMPERSONATING_USER_ID_KEY);

                if (null != impersonatingUserId)
                {
                    sessionUser.setImpersonatingUser(UserManager.getUser(impersonatingUserId.intValue()));

                    String projectId = (String) session.getAttribute(IMPERSONATION_PROJECT_KEY);

                    if (null != projectId)
                        sessionUser.setImpersonationProject(ContainerManager.getForId(projectId));
                }

                // We want groups membership to be calculated on every request (but just once)
                // the cloned User will calculate groups exactly once
                // NOTE: getUser() returns a cloned object
                // u = sessionUser.cloneUser();
                assert sessionUser._groups == null;
                sessionUser._groups = null;
                u = sessionUser;
            }
        }
        if (null == u)
        {
            // Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
            String authorization = request.getHeader("Authorization");
            if (null != authorization && authorization.startsWith("Basic"))
            {
                u = authenticateBasic(authorization.substring("Basic".length()).trim());
                if (null != u)
                {
                    request.setAttribute(AUTHENTICATION_METHOD, "Basic");
                    SecurityManager.setAuthenticatedUser(request, u, null, null, null);
                }
            }
        }
        return null == u || u.isGuest() ? null : u;
    }


    public static final String IMPERSONATION_RETURN_URL_KEY = User.class.getName() + "$impersonationReturnURL";
    private static final String IMPERSONATION_PROJECT_KEY = User.class.getName() + "$impersonationProject";
    private static final String IMPERSONATING_USER_ID_KEY = User.class.getName() + "$impersonatingUserId";
    private static final String USER_ID_KEY = User.class.getName() + "$userId";
    private static final String IMPERSONATORS_SESSION_MAP_KEY = "ImpersonatorsSessionMapKey";

    public static void setAuthenticatedUser(HttpServletRequest request, User user, User impersonatingUser, Container project, ActionURL returnURL)
    {
        invalidateSession(request);      // Clear out terms-of-use and other session info that guest / previous user may have

        HttpSession newSession = request.getSession(true);
        newSession.setAttribute(USER_ID_KEY, user.getUserId());
        newSession.setAttribute("LABKEY.username", user.getName());

        if (null != impersonatingUser)
            newSession.setAttribute(IMPERSONATING_USER_ID_KEY, impersonatingUser.getUserId());

        if (null != project)
            newSession.setAttribute(IMPERSONATION_PROJECT_KEY, project.getId());

        if (null != returnURL)
            newSession.setAttribute(IMPERSONATION_RETURN_URL_KEY, returnURL);
    }


    public static void logoutUser(HttpServletRequest request, User user)
    {
        AuthenticationManager.logout(user, request);   // Let AuthenticationProvider clean up auth-specific cookies, etc.
        invalidateSession(request);
    }


    public static void impersonate(ViewContext viewContext, User impersonatedUser, Container project, ActionURL returnURL)
    {
        HttpServletRequest request = viewContext.getRequest();
        User adminUser = viewContext.getUser();

        // We clear the session when we impersonate; we stash the admin's session attributes in the new
        // session so we can reinstate them after impersonation is over.
        Map<String, Object> impersonatorSessionAttributes = new HashMap<String, Object>();
        HttpSession impersonatorSession = request.getSession(true);
        Enumeration names = impersonatorSession.getAttributeNames();

        while (names.hasMoreElements())
        {
            String name = (String) names.nextElement();
            impersonatorSessionAttributes.put(name, impersonatorSession.getAttribute(name));
        }

        SecurityManager.setAuthenticatedUser(request, impersonatedUser, adminUser, project, returnURL);
        HttpSession userSession = request.getSession(true);
        userSession.setAttribute(IMPERSONATORS_SESSION_MAP_KEY, impersonatorSessionAttributes);

        AuditLogService.get().addEvent(viewContext, UserManager.USER_AUDIT_EVENT, adminUser.getUserId(),
                adminUser.getEmail() + " impersonated " + impersonatedUser.getEmail());
        AuditLogService.get().addEvent(viewContext, UserManager.USER_AUDIT_EVENT, impersonatedUser.getUserId(),
                impersonatedUser.getEmail() + " was impersonated by " + adminUser.getEmail());
    }


    public static void stopImpersonating(ViewContext viewContext, User impersonatedUser)
    {
        assert impersonatedUser.isImpersonated();
        HttpServletRequest request = viewContext.getRequest();

        if (impersonatedUser.isImpersonated())
        {
            User adminUser = impersonatedUser.getImpersonatingUser();

            HttpSession userSession = request.getSession(true);
            Map<String, Object> impersonatorSessionAttributes = (Map<String, Object>)userSession.getAttribute(IMPERSONATORS_SESSION_MAP_KEY);

            assert null != impersonatorSessionAttributes;

            if (null != impersonatorSessionAttributes)
            {
                invalidateSession(request);
                HttpSession impersonatorSession = request.getSession(true);

                for (Map.Entry<String, Object> entry : impersonatorSessionAttributes.entrySet())
                    impersonatorSession.setAttribute(entry.getKey(), entry.getValue());
            }
            else
            {
                // Just in case
                setAuthenticatedUser(request, adminUser, null, null, null);
            }

            AuditLogService.get().addEvent(viewContext, UserManager.USER_AUDIT_EVENT, impersonatedUser.getUserId(),
                impersonatedUser.getEmail() + " was no longer impersonated by " + adminUser.getEmail());
            AuditLogService.get().addEvent(viewContext, UserManager.USER_AUDIT_EVENT, adminUser.getUserId(),
                adminUser.getEmail() + " stopped impersonating " + impersonatedUser.getEmail());
        }
        else
        {
            invalidateSession(request);
        }
    }


    private static void invalidateSession(HttpServletRequest request)
    {
        HttpSession s = request.getSession();
        if (null != s)
            s.invalidate();
    }


    private static final String passwordChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static final int tempPasswordLength = 32;

    public static String createTempPassword()
    {
        StringBuilder tempPassword = new StringBuilder(tempPasswordLength);

        for (int i = 0; i < tempPasswordLength; i++)
            tempPassword.append(passwordChars.charAt((int) Math.floor((Math.random() * passwordChars.length()))));

        return tempPassword.toString();
    }


    public static ActionURL createVerificationURL(Container c, String email, String verification, Pair<String, String>[] extraParameters)
    {
        return PageFlowUtil.urlProvider(LoginUrls.class).getVerificationURL(c, email, verification, extraParameters);
    }


    // Test if non-LDAP email has been verified
    public static boolean isVerified(ValidEmail email) throws UserManagementException
    {
        return (null == getVerification(email));
    }


    public static boolean verify(ValidEmail email, String verification) throws UserManagementException
    {
        String dbVerification = getVerification(email);
        return (dbVerification != null && dbVerification.equals(verification));
    }


    public static void setVerification(ValidEmail email, String verification) throws UserManagementException
    {
        try
        {
            int rows = Table.execute(core.getSchema(), "UPDATE " + core.getTableInfoLogins() + " SET Verification=? WHERE email=?", new Object[]{verification, email.getEmailAddress()});
            if (1 != rows)
                throw new UserManagementException(email, "Unexpected number of rows returned when setting verification: " + rows);
        }
        catch (SQLException e)
        {
            _log.error("setVerification: ", e);
            throw new UserManagementException(email, e);
        }
    }


    public static String getVerification(ValidEmail email) throws UserManagementException
    {
        try
        {
            return Table.executeSingleton(core.getSchema(), "SELECT Verification FROM " + core.getTableInfoLogins() + " WHERE email=?", new Object[]{email.getEmailAddress()}, String.class);
        }
        catch (SQLException e)
        {
            _log.error("verify: ", e);
            throw new UserManagementException(email, e);
        }
    }


    public static class NewUserBean
    {
        private String email;
        private String verification;
        private boolean ldap;
        private User user;

        public NewUserBean(String email)
        {
            setEmail(email);
        }

        public String getEmail()
        {
            return email;
        }

        public void setEmail(String email)
        {
            this.email = email;
        }

        public boolean isLdap()
        {
            return ldap;
        }

        public void setLdap(boolean ldap)
        {
            this.ldap = ldap;
        }

        public String getVerification()
        {
            return verification;
        }

        public void setVerification(String verification)
        {
            this.verification = verification;
        }

        public User getUser()
        {
            return user;
        }

        public void setUser(User user)
        {
            this.user = user;
        }
    }


    public static class UserManagementException extends Exception
    {
        private String _email;

        public UserManagementException(ValidEmail email, String message)
        {
            super(message);
            _email = email.getEmailAddress();
        }

        public UserManagementException(String email, String message)
        {
            super(message);
            _email = email;
        }

        public UserManagementException(ValidEmail email, String message, Exception cause)
        {
            super(message, cause);
            _email = email.getEmailAddress();
        }

        public UserManagementException(ValidEmail email, Exception cause)
        {
            super(cause);
            _email = email.getEmailAddress();
        }

        public UserManagementException(String email, Exception cause)
        {
            super(cause);
            _email = email;
        }

        public String getEmail()
        {
            return _email;
        }
    }

    public static class UserAlreadyExistsException extends UserManagementException
    {
        public UserAlreadyExistsException(String email)
        {
            super(email, "User already exists");
        }
    }

    public static NewUserBean addUser(ValidEmail email) throws UserManagementException
    {
        NewUserBean newUserBean = new NewUserBean(email.getEmailAddress());

        if (UserManager.userExists(email))
            throw new UserAlreadyExistsException(email.getEmailAddress());

        if (!SecurityManager.isLdapEmail(email))
        {
            // Create a placeholder password that's hard to guess and a separate email verification
            // key that gets emailed.
            newUserBean.setLdap(false);

            String tempPassword = SecurityManager.createTempPassword();
            String verification = SecurityManager.createTempPassword();

            SecurityManager.createLogin(email, tempPassword, verification);

            newUserBean.setVerification(verification);
        }
        else
        {
            newUserBean.setLdap(true);
        }

        User newUser;
        try
        {
            newUser = UserManager.createUser(email);
        }
        catch (SQLException e)
        {
            throw new UserManagementException(email, "Unable to create user.", e);
        }

        if (null == newUser)
            throw new UserManagementException(email, "Couldn't create user.");

        newUserBean.setUser(newUser);
        return newUserBean;
    }


    public static void sendEmail(Container c, User user, SecurityMessage message, String to, ActionURL verificationURL) throws MessagingException
    {
        MimeMessage m = createMessage(c, user, message, to, verificationURL);
        MailHelper.send(m);
    }

    public static void renderEmail(Container c, User user, SecurityMessage message, String to, ActionURL verificationURL, Writer out) throws MessagingException
    {
        MimeMessage m = createMessage(c, user, message, to, verificationURL);
        MailHelper.renderHtml(m, message.getType(), out);
    }

    private static MimeMessage createMessage(Container c, User user, SecurityMessage message, String to, ActionURL verificationURL) throws MessagingException
    {
        try
        {
            message.setVerificationURL(verificationURL.getURIString());
            message.setFrom(user.getEmail());
            if (message.getTo() == null)
                message.setTo(to);

            MimeMessage m = message.createMailMessage(c);

            m.addFrom(new Address[]{new InternetAddress(user.getEmail(), user.getFullName())});
            m.addRecipients(Message.RecipientType.TO, to);

            return m;
        }
        catch (UnsupportedEncodingException e)
        {
            throw new MessagingException("Failed to create InternetAddress.", e);
        }
        catch (Exception e)
        {
            throw new MessagingException("Failed to set template context.", e);
        }
    }

    // Create record for non-LDAP login, saving email address and hashed password
    public static void createLogin(ValidEmail email, String password, String verification) throws UserManagementException
    {
        try
        {
            int rowCount = Table.execute(core.getSchema(), "INSERT INTO " + core.getTableInfoLogins() + " (Email, Crypt, Verification) VALUES (?, ?, ?)", new Object[]{email.getEmailAddress(), Crypt.digest(password), verification});
            if (1 != rowCount)
                throw new UserManagementException(email, "Login creation statement affected " + rowCount + " rows.");
        }
        catch (SQLException e)
        {
            _log.error("createLogin", e);
            throw new UserManagementException(email, e);
        }
    }


    public static void setPassword(ValidEmail email, String password) throws UserManagementException
    {
        try
        {
            int rows = Table.execute(core.getSchema(), "UPDATE " + core.getTableInfoLogins() + " SET Crypt=? WHERE Email=?", new Object[]{Crypt.digest(password), email.getEmailAddress()});
            if (1 != rows)
                throw new UserManagementException(email, "Password update statement affected " + rows + " rows.");
        }
        catch (SQLException e)
        {
            _log.error("setPassword", e);
            throw new UserManagementException(email, e);
        }
    }


    // Look up email in Logins table and return the corresponding password hash
    public static String getPasswordHash(ValidEmail email)
    {
        String hash = null;

        try
        {
            hash = Table.executeSingleton(core.getSchema(), "SELECT Crypt FROM " + core.getTableInfoLogins() + " WHERE Email=?", new Object[]{email.getEmailAddress()}, String.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return hash;
    }


    public static boolean loginExists(ValidEmail email)
    {
        return (null != getPasswordHash(email));
    }


    public static Group createGroup(Container c, String name)
    {
        return createGroup(c, name, Group.typeProject);
    }


    public static Group createGroup(Container c, String name, String type)
    {
        String defaultOwnerId = (null == c || c.isRoot()) ? null : c.getId();
        return createGroup(c, name, type, defaultOwnerId);
    }


    public static Group createGroup(Container c, String name, String type, String ownerId)
    {
        String containerId = (null == c || c.isRoot()) ? null : c.getId();
        Group group = new Group();
        group.setName(StringUtils.trimToNull(name));
        group.setOwnerId(ownerId);
        group.setContainer(containerId);
        group.setType(type);

        if (null == group.getName())
            throw new IllegalArgumentException("Group can not have blank name");

        String valid = UserManager.validGroupName(group.getName(), group.getType());
        if (null != valid)
            throw new IllegalArgumentException(valid);

        if (groupExists(c, group.getName(), group.getOwnerId()))
            throw new IllegalArgumentException("Group already exists");

        try
        {
            Table.insert(null, core.getTableInfoPrincipals(), group);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return group;
    }


    // Case-insensitive existence check -- disallows groups that differ only by case
    private static boolean groupExists(Container c, String groupName, String ownerId)
    {
        return null != getGroupId(c, groupName, ownerId, false, true);
    }


    public static void deleteGroup(String groupPath)
    {
        Integer groupId = getGroupId(groupPath);
        if (groupId == null)
            return;
        deleteGroup(groupId);
    }


    public static void deleteGroup(Group group)
    {
        deleteGroup(group.getUserId());
    }


    static void deleteGroup(int groupId)
    {
        if (groupId == Group.groupAdministrators ||
                groupId == Group.groupGuests ||
                groupId == Group.groupUsers)
            throw new IllegalArgumentException("The global groups cannot be deleted.");

        try
        {
            removeGroupFromCache(groupId);

            Table.delete(core.getTableInfoRoleAssignments(), new SimpleFilter("UserId", groupId));

            Filter groupFilter = new SimpleFilter("GroupId", groupId);
            Table.delete(core.getTableInfoMembers(), groupFilter);

            Filter principalsFilter = new SimpleFilter("UserId", groupId);
            Table.delete(core.getTableInfoPrincipals(), principalsFilter);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static void deleteGroups(Container c, String type)
    {
        if (!(null == type || type.equals(Group.typeProject) || type.equals(Group.typeModule) ))
            throw new IllegalArgumentException("Illegal group type: " + type);

        if (null == type)
            type = "%";

        try
        {
            removeAllGroupsFromCache();

            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoRoleAssignments() + "\n"+
                    "WHERE UserId in (SELECT UserId FROM " + core.getTableInfoPrincipals() +
                    "\tWHERE Container=? and Type LIKE ?)", new Object[] {c, type});
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoMembers() + "\n"+
                    "WHERE GroupId in (SELECT UserId FROM " + core.getTableInfoPrincipals() +
                    "\tWHERE Container=? and Type LIKE ?)", new Object[] {c, type});
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoPrincipals() +
                    "\tWHERE Container=? AND Type LIKE ?", new Object[] {c, type});
        }
        catch (SQLException x)
        {
            _log.error("Delete group", x);
            throw new RuntimeSQLException(x);
        }
    }


    public static void deleteMembers(Group group, List<ValidEmail> emailsToDelete)
    {
        int groupId = group.getUserId();

        if (emailsToDelete != null && !emailsToDelete.isEmpty())
        {

            SQLFragment sql = new SQLFragment(
            "DELETE FROM " + core.getTableInfoMembers() + "\n" +
                    "WHERE GroupId = ? AND UserId IN\n" +
                    "(SELECT M.UserId\n" +
                    "FROM " + core.getTableInfoMembers() + " M JOIN " + core.getTableInfoPrincipals() + " P ON M.UserId = P.UserId\n" +
                    "WHERE GroupId = ? AND Name IN (");
            sql.add(groupId);
            sql.add(groupId);
            Iterator<ValidEmail> it = emailsToDelete.iterator();
            String comma = "";
            while (it.hasNext())
            {
                ValidEmail email = it.next();
                sql.append(comma).append("?");
                comma = ",";
                sql.add(email.getEmailAddress());
            }
            sql.append("))");

            try
            {
                Table.execute(core.getSchema(), sql);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            for (ValidEmail email : emailsToDelete)
                fireDeletePrincipalFromGroup(groupId, UserManager.getUser(email));
        }
    }


    public static void deleteMember(Group group, UserPrincipal principal)
    {
        int groupId = group.getUserId();

        try
        {
            Table.execute(
                    core.getSchema(),
                    "DELETE FROM " + core.getTableInfoMembers() + "\n" +
                            "WHERE GroupId = ? AND UserId = ?",
                    new Object[]{groupId, principal.getUserId()});
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        fireDeletePrincipalFromGroup(groupId, principal);
    }


    public static void addMembers(Group group, List<ValidEmail> emailsToAdd) throws SQLException
    {
        int groupId = group.getUserId();

        if (emailsToAdd != null && !emailsToAdd.isEmpty())
        {
            Iterator<ValidEmail> it = emailsToAdd.iterator();
            StringBuilder addString = new StringBuilder();
            while (it.hasNext())
            {
                ValidEmail email = it.next();
                addString.append("'").append(StringEscapeUtils.escapeSql(email.getEmailAddress())).append("'");

                if (it.hasNext())
                    addString.append(", ");
            }

            Table.execute(
                    core.getSchema(),
                    "INSERT INTO " + core.getTableInfoMembers() +
                            "\nSELECT UserId, ?\n" +
                            "FROM " + core.getTableInfoPrincipals() +
                            "\nWHERE Name IN (" + addString.toString() + ") AND Name NOT IN\n" +
                            "  (SELECT Name FROM " + core.getTableInfoMembers() + " _Members JOIN " + core.getTableInfoPrincipals() + " _Principals ON _Members.UserId = _Principals.UserId\n" +
                            "   WHERE GroupId = ?)",
                    new Object[]{groupId, groupId});
        }

        if (null != emailsToAdd)
            for (ValidEmail email : emailsToAdd)
                fireAddPrincipalToGroup(group, UserManager.getUser(email));
    }


    /** @deprecated */
    public static void addMember(Integer groupId, User user)
    {
        Group group = getGroup(groupId);
        addMember(group, user);
    }

    // Add a single user to a single group
    public static void addMember(Group group, UserPrincipal user)
    {
        try
        {
            Table.execute(
                    core.getSchema(),
                    "INSERT INTO " + core.getTableInfoMembers() + " (UserId, GroupId) VALUES (?, ?)",
                    new Object[]{user.getUserId(), group.getUserId()});
            fireAddPrincipalToGroup(group, user);
        }
        catch (SQLException e)
        {
            _log.error("addMember", e);
        }
    }


    public static Group[] getGroups(Container project, boolean includeGlobalGroups)
    {
        try
        {
            if (null == project)
            {
                return Table.executeQuery(
                        core.getSchema(),
                        "SELECT Name, UserId, Container FROM " + core.getTableInfoPrincipals() + " WHERE Type='g' AND Container IS NULL ORDER BY LOWER(Name)",  // Force case-insensitve order for consistency
                        null,
                        Group.class);
            }
            else
            {
                String projectClause = (includeGlobalGroups ? "(Container = ? OR Container IS NULL)" : "Container = ?");

                // Postgres and SQLServer disagree on how to sort null, so we need to handle
                // null Container values as the first ORDER BY criteria
                return Table.executeQuery(
                        core.getSchema(),
                        "SELECT Name, UserId, Container FROM " + core.getTableInfoPrincipals() + "\n" +
                                "WHERE Type='g' AND " + projectClause + "\n" +
                                "ORDER BY CASE WHEN ( Container IS NULL ) THEN 1 ELSE 2 END, Container, LOWER(Name)",  // Force case-insensitve order for consistency
                        new Object[]{project.getId()},
                        Group.class);
            }
        }
        catch (SQLException e)
        {
            _log.error("unexpected exception", e);
            throw new RuntimeSQLException(e);
        }
    }


    public static Group getGroup(int groupId)
    {
        Group group = (Group) Cache.getShared().get(GROUP_CACHE_PREFIX + groupId);

        if (null == group)
        {
            try
            {
                Group[] groups = Table.executeQuery(
                        core.getSchema(),
                        "SELECT Name, UserId, Container FROM " + core.getTableInfoPrincipals() + " WHERE type <> 'u' AND userId=?",
                        new Object[] {groupId},
                        Group.class);
                assert groups.length <= 1;
                group = groups.length == 0 ? null : groups[0];
                Cache.getShared().put(GROUP_CACHE_PREFIX + groupId, group);
            }
            catch (SQLException e)
            {
                _log.error("unexpected exception", e);
                throw new RuntimeSQLException(e);
            }
        }

        return group;
    }

    public static UserPrincipal getPrincipal(int id)
    {
        UserPrincipal principal = UserManager.getUser(id);
        return null != principal ? principal : getGroup(id);
    }

    public static UserPrincipal getPrincipal(String name, Container container)
    {
        Integer id = getGroupId(container, name, false);
        if(null != id)
            return getGroup(id.intValue());
        else
        {
            try
            {
                return UserManager.getUser(new ValidEmail(name));
            }
            catch(ValidEmail.InvalidEmailException e)
            {
                return null;
            }
        }
    }

    private static void removeGroupFromCache(int groupId)
    {
        Cache.getShared().remove(GROUP_CACHE_PREFIX + groupId);
    }


    private static void removeAllGroupsFromCache()
    {
        Cache.getShared().removeUsingPrefix(GROUP_CACHE_PREFIX);
    }


    public static List<User> getProjectMembers(Container c)
    {
        return getProjectMembers(c, false);
    }

    /**
     * Returns a list of Group object to which the user belongs in the specified container.
     * @param c The container
     * @param u The user
     * @return The list of groups that u belong to in container c
     */
    public static List<Group> getGroups(Container c, User u)
    {
        Container proj = null != c.getProject() ? c.getProject() : c;
        int[] groupIds = u.getGroups();

        List<Group> groupList = new ArrayList<Group>();
        for (int groupId : groupIds)
        {
            //ignore user as group
            if (groupId != u.getUserId())
            {
                Group g = SecurityManager.getGroup(groupId);

                // Only global groups or groups in this project
                if (null == g.getContainer() || g.getContainer().equals(proj.getId()))
                    groupList.add(g);
            }
        }

        return groupList;
    }

    // Returns comma-separated list of group names this user belongs to in this container
    public static String getGroupList(Container c, User u)
    {
        Container proj = c.getProject();

        if (null == proj)
            return "";

        int[] groupIds = u.getGroups();

        StringBuilder groupList = new StringBuilder();
        String sep = "";

        for (int groupId : groupIds)
        {
            // Ignore Guest, Users, Admins, and user's own id
            if (groupId > 0 && groupId != u.getUserId())
            {
                Group g = SecurityManager.getGroup(groupId);

                if (null == g)
                    continue;

                // Only groups in this project
                if (g.getContainer().equals(proj.getId()))
                {
                    groupList.append(sep);
                    groupList.append(g.getName());
                    sep = ", ";
                }
            }
        }

        return groupList.toString();
    }

    public static List<User> getGroupMembers(Group group) throws SQLException, ValidEmail.InvalidEmailException
    {
        String[] emails = getGroupMemberNames(group.getUserId());
        List<User> users = new ArrayList<User>(emails.length);
        for(String email : emails)
            users.add(UserManager.getUser(new ValidEmail(email)));
        return users;
    }

    public static enum GroupMemberType
    {
        Users,
        Groups,
        Both
    }

    @NotNull
    public static List<UserPrincipal> getGroupMembers(Group group, GroupMemberType memberType) throws SQLException
    {
        List<UserPrincipal> principals = new ArrayList<UserPrincipal>();
        Integer[] ids = getGroupMemberIds(ContainerManager.getForId(group.getContainer()), group.getName());
        for(Integer id : ids)
        {
            UserPrincipal principal = getPrincipal(id.intValue());
            if(null != principal && (GroupMemberType.Both == memberType
                    || (GroupMemberType.Users == memberType && principal instanceof User)
                    || (GroupMemberType.Groups == memberType && principal instanceof Group)))
                principals.add(principal);
        }
        return principals;
    }


    // TODO: Redundant with getProjectMembers() -- this approach should be more efficient for simple cases
    // TODO: Also redundant with getProjectUserids()
    // TODO: Cache this set
    public static Set<Integer> getProjectMembersIds(Container c)
    {
        SQLFragment sql = SecurityManager.getProjectMembersSQL(c.getProject());
        sql.insert(0, "SELECT DISTINCT members.UserId ");

        Integer[] projectMembers;

        try
        {
            projectMembers = Table.executeArray(core.getSchema(), sql, Integer.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return PageFlowUtil.set(projectMembers);
    }


    // True fragment -- need to prepend SELECT DISTINCT() or IN () for this to be valid SQL
    public static SQLFragment getProjectMembersSQL(Container c)
    {
        return new SQLFragment("FROM " + core.getTableInfoMembers() + " members INNER JOIN " + core.getTableInfoUsers() + " users ON members.UserId = users.UserId\n" +
                                    "INNER JOIN " + core.getTableInfoPrincipals() + " groups ON members.GroupId = groups.UserId\n" +
                                    "WHERE (groups.Container = ?)", c);
    }

    // TODO: Should return a set
    public static List<User> getProjectMembers(Container c, boolean includeGlobal)
    {
        if (c != null && !c.isProject())
            c = c.getProject();

        Group[] groups = getGroups(c, includeGlobal);
        Set<String> emails = new HashSet<String>();

       //get members for each group
        ArrayList<User> projectMembers = new ArrayList<User>();
        String[] members;

        try
        {
            for(Group g : groups)
            {
                if (g.isGuests() || g.isUsers())
                    continue;

                if (g.isProjectGroup())
                    members = getGroupMemberNames(getGroupId(c, g.getName()));
                else
                    members = getGroupMemberNames(getGroupId(null, g.getName()));

                //add this group's members to hashset
                if (members != null)
                {
                    //get list of users from email
                    for (String member : members)
                    {
                        if (emails.add(member))
                            projectMembers.add(UserManager.getUser(new ValidEmail(member)));
                    }
                }
            }
            return projectMembers;
        }
        catch (SQLException e)
        {
            _log.error("unexpected error", e);
            throw new RuntimeSQLException(e);
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            _log.error("unexpected error", e);
            throw new RuntimeException(e);
        }
    }


    public static List<Integer> getProjectUserids(Container c)
    {
        try
        {
            Integer[] a =Table.executeArray(CoreSchema.getInstance().getSchema(),
                    "SELECT U.userid\n" +
                    "FROM core.principals U\n" +
                    "WHERE U.type='u' AND U.userid IN \n" +
                    "  (SELECT M.userid from core.members M INNER JOIN core.principals G ON M.groupid = G.userid WHERE G.type = 'g' AND G.container = ?)",
                    new Object[] {c}, Integer.class);
            return Arrays.asList(a);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static List<User> getUsersWithPermissions(Container c, Set<Class<? extends Permission>> perms) throws SQLException
    {
        // No cache right now, but performance seems fine.  After the user list and acl is cached, no other queries occur.
        User[] allUsers = UserManager.getActiveUsers();
        List<User> users = new ArrayList<User>(allUsers.length);
        SecurityPolicy policy = c.getPolicy();

        for (User user : allUsers)
            if (policy.hasPermissions(user, perms))
                users.add(user);

        return users;
    }


    public static List<Pair<Integer, String>> getGroupMemberNamesAndIds(String path)
    {
        Integer groupId = SecurityManager.getGroupId(path);
        if (groupId == null)
            return Collections.emptyList();
        else
            return getGroupMemberNamesAndIds(groupId);
    }
    

    public static String[] getGroupMemberNames(String path)
    {
        try
        {
            Integer groupId = SecurityManager.getGroupId(path);
            if (groupId == null)
                return new String[0];
            else
                return getGroupMemberNames(groupId);
        }
        catch (SQLException e)
        {
            _log.error(e);
            throw new RuntimeSQLException(e);
        }
    }

    public static List<Pair<Integer, String>> getGroupMemberNamesAndIds(Integer groupId)
    {
        ResultSet rs = null;
        try
        {
             rs = Table.executeQuery(
                    core.getSchema(),
                    "SELECT Users.UserId, Users.Name\n" +
                            "FROM " + core.getTableInfoMembers() + " JOIN " + core.getTableInfoPrincipals() + " Users ON " + core.getTableInfoMembers() + ".UserId = Users.UserId\n" +
                            "WHERE GroupId = ? AND Active=?\n" +
                            "ORDER BY Users.Name",
                    new Object[]{groupId, true});
            List<Pair<Integer,String>> members = new ArrayList<Pair<Integer, String>>();
            while (rs.next())
                members.add(new Pair<Integer,String>(rs.getInt(1), rs.getString(2)));
            return members;
        }
        catch (SQLException e)
        {
            _log.error(e);
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (rs != null)
            {
                try { rs.close(); }
                catch (SQLException e)
                {
                    //ignore
                }
            }
        }
    }

    public static String[] getGroupMemberNames(Integer groupId) throws SQLException
    {
        List<Pair<Integer, String>> members = getGroupMemberNamesAndIds(groupId);
        String[] names = new String[members.size()];
        int i = 0;
        for (Pair<Integer, String> member : members)
            names[i++] = member.getValue();
        return names;
    }


    public static Integer[] getGroupMemberIds(Container c, String groupName)
    {
        try
        {
            Integer groupId = SecurityManager.getGroupId(c, groupName);
            return Table.executeArray(core.getSchema(), "SELECT UserId FROM " + core.getTableInfoMembers() + " WHERE GroupId = ?", new Object[]{groupId}, Integer.class);
        }
        catch (SQLException e)
        {
            _log.error(e);
        }

        return new Integer[ 0 ];
    }


    // Takes string such as "/test/subfolder/Users" and returns groupId
    public static Integer getGroupId(String extraPath)
    {
        if (extraPath.startsWith("/"))
            extraPath = extraPath.substring(1);

        int slash = extraPath.lastIndexOf('/');
        String group = extraPath.substring(slash + 1);
        Container c = null;
        if (slash != -1)
        {
            String path = extraPath.substring(0, slash);
            c = ContainerManager.getForPath(path);
            if (null == c)
                HttpView.throwNotFound();
        }

        return getGroupId(c, group);
    }


    // Takes Container (or null for root) and group name; returns groupId
    public static Integer getGroupId(Container c, String group)
    {
        return getGroupId(c, group, null, true);
    }


    // Takes Container (or null for root) and group name; returns groupId
    public static Integer getGroupId(Container c, String group, boolean throwOnFailure)
    {
        return getGroupId(c, group, null, throwOnFailure);
    }


    // Takes Container (or null for root) and group name; returns groupId
    public static Integer getGroupId(Container c, String group, String ownerId)
    {
        return getGroupId(c, group, ownerId, true);
    }


    public static Integer getGroupId(Container c, String groupName, String ownerId, boolean throwOnFailure)
    {
        return getGroupId(c, groupName, ownerId, throwOnFailure, false);
    }


    // This is temporary... in CPAS 1.5 on PostgreSQL it was possible to create two groups in the same container that differed only
    // by case (this was not possible on SQL Server).  In CPAS 1.6 we disallow this on PostgreSQL... but we still need to be able to
    // retrieve group IDs in a case-sensitive manner.
    // TODO: For CPAS 1.7: this should always be case-insensitive (we will clean up the database by renaming duplicate groups)
    private static Integer getGroupId(Container c, String groupName, String ownerId, boolean throwOnFailure, boolean caseInsensitive)
    {
        List<String> params = new ArrayList<String>();
        params.add(caseInsensitive ? groupName.toLowerCase() : groupName);
        String sql = "SELECT UserId FROM " + core.getTableInfoPrincipals() + " WHERE " + (caseInsensitive ? "LOWER(Name)" : "Name") + " = ? AND Container ";
        if (c == null || c.isRoot())
            sql += "IS NULL";
        else
        {
            sql += "= ?";
            params.add(c.getId());
            if (ownerId == null)
                ownerId = c.isRoot() ? null : c.getId();
        }

        if (ownerId == null)
            sql += " AND OwnerId IS NULL";
        else
        {
            sql += " AND OwnerId = ?";
            params.add(ownerId);
        }

        Integer groupId;
        try
        {
            groupId = Table.executeSingleton(core.getSchema(), sql,
                    params.toArray(new Object[params.size()]), Integer.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        if (groupId == null && throwOnFailure)
            HttpView.throwNotFound();

        return groupId;
    }


    public static boolean isTermsOfUseRequired(Project project)
    {
        //TODO: Should do this more efficiently, but no efficient public wiki api for this yet
        return null != getTermsOfUseHtml(project);
    }


    public static String getTermsOfUseHtml(Project project)
    {
        if (null == project)
            return null;

        if (!ModuleLoader.getInstance().isStartupComplete())
            return null;

        WikiService service = ServiceRegistry.get().getService(WikiService.class);
        //No wiki service. Must be in weird state. Don't do terms here...
        if(null == service)
            return null;

        return service.getHtml(project.getContainer(), TERMS_OF_USE_WIKI_NAME, false);
    }


    public static boolean isTermsOfUseRequired(ViewContext ctx)
    {
        Container c = ctx.getContainer();
        if (null == c)
            return false;

        Container proj = c.getProject();
        if (null == proj)
            return false;

        Project project = new Project(proj);

        if ("Basic".equals(ctx.getRequest().getAttribute(AUTHENTICATION_METHOD)) || isTermsOfUseApproved(ctx, project))
            return false;

        boolean required = isTermsOfUseRequired(project);

        //stash result so that this is faster next time.
        if (!required)
            setTermsOfUseApproved(ctx, project, true);

        return required;
    }


    private static final String TERMS_APPROVED_KEY = "TERMS_APPROVED_KEY";
    private static final Object TERMS_APPROVED_LOCK = new Object();

    public static boolean isTermsOfUseApproved(ViewContext ctx, Project project)
    {
        if (null == project)
            return true;

        synchronized (TERMS_APPROVED_LOCK)
        {
            HttpSession session = ctx.getRequest().getSession(true);
            Set<Project> termsApproved = (Set<Project>) session.getAttribute(TERMS_APPROVED_KEY);
            return null != termsApproved && termsApproved.contains(project);
        }
    }


    public static void setTermsOfUseApproved(ViewContext ctx, Project project, boolean approved)
    {
        if (null == project)
            return;

        synchronized (TERMS_APPROVED_LOCK)
        {
            HttpSession session = ctx.getRequest().getSession(true);
            Set<Project> termsApproved = (Set<Project>) session.getAttribute(TERMS_APPROVED_KEY);
            if (null == termsApproved)
            {
                termsApproved = new HashSet<Project>();
                session.setAttribute(TERMS_APPROVED_KEY, termsApproved);
            }
            if (approved)
                termsApproved.add(project);
            else
                termsApproved.remove(project);
        }
    }


    // CONSIDER: Support multiple LDAP domains?
    public static boolean isLdapEmail(ValidEmail email)
    {
        String ldapDomain = AuthenticationManager.getLdapDomain();
        return ldapDomain != null && email.getEmailAddress().endsWith("@" + ldapDomain.toLowerCase());
    }


    // Password rule: regular expression and English language version for error messages
    // TODO: Add these to AppProps
    public static final String passwordRule = "Passwords must be six characters or more and can't match your email address.";
    private static Pattern passwordPattern = Pattern.compile("^\\S{6,}$");  // At least six, non-whitespace characters

    // Make sure password is strong enough.
    public static boolean isValidPassword(@NotNull String password, @NotNull ValidEmail email)
    {
        if (null != password)
            return passwordPattern.matcher(password).matches() && !email.getEmailAddress().equalsIgnoreCase(password);  // Passes rule and doesn't match email address
        else
            return false;
    }


    //
    // Permissions, ACL cache and Permission testing
    //

    private static String _aclPrefix = "ACL/";


    //manage SecurityPolicy
    private static String _policyPrefix = "Policy/";

    @NotNull
    public static SecurityPolicy getPolicy(@NotNull SecurableResource resource)
    {
        return getPolicy(resource, true);
    }

    @NotNull
    public static SecurityPolicy getPolicy(@NotNull SecurableResource resource, boolean findNearest)
    {
        String cacheName = cacheName(resource);
        SecurityPolicy policy = (SecurityPolicy) DbCache.get(core.getTableInfoRoleAssignments(), cacheName);
        if(null == policy)
        {
            try
            {
                SimpleFilter filter = new SimpleFilter("ResourceId", resource.getResourceId());

                SecurityPolicyBean policyBean = Table.selectObject(core.getTableInfoPolicies(), resource.getResourceId(),
                        SecurityPolicyBean.class);

                TableInfo table = core.getTableInfoRoleAssignments();

                RoleAssignment[] assignments = Table.select(table, Table.ALL_COLUMNS, filter,
                        new Sort("UserId"), RoleAssignment.class);

                policy = new SecurityPolicy(resource, assignments, null != policyBean ? policyBean.getModified() : new Date());
                DbCache.put(core.getTableInfoRoleAssignments(), cacheName, policy);
            }
            catch(SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        if(findNearest && policy.isEmpty() && resource.mayInheritPolicy())
        {
            SecurableResource parent = resource.getParentResource();
            if(null != parent)
                return getPolicy(parent, findNearest);
        }

        return policy;
    }

    public static void savePolicy(@NotNull SecurityPolicy policy)
    {
        DbScope scope = core.getSchema().getScope();
        boolean startedTran = false;
        try
        {
            //start a transaction
            if(!scope.isTransactionActive())
            {
                scope.beginTransaction();
                startedTran = true;
            }

            //if the policy to save has a version, check to see if it's the current one
            //(note that this may be a new policy so there might not be an existing one)
            SecurityPolicyBean currentPolicyBean = Table.selectObject(core.getTableInfoPolicies(),
                    policy.getResource().getResourceId(), SecurityPolicyBean.class);

            if(null != currentPolicyBean && null != policy.getModified() &&
                    0 != policy.getModified().compareTo(currentPolicyBean.getModified()))
            {
                throw new Table.OptimisticConflictException("The security policy you are attempting to save" +
                " has been altered by someone else since you selected it.", Table.SQLSTATE_TRANSACTION_STATE, 0);
            }

            //save to policies table
            if(null == currentPolicyBean)
            {
                SecurityPolicyBean newPolicyBean = new SecurityPolicyBean(policy.getResource(), policy.getModified()); 
                Table.insert(null, core.getTableInfoPolicies(), newPolicyBean);
            }
            else
            {
                Table.update(null, core.getTableInfoPolicies(), policy.getBean(), policy.getResource().getResourceId(), null);
            }

            TableInfo table = core.getTableInfoRoleAssignments();

            //delete all rows where resourceid = resource.getId()
            Table.delete(table, new SimpleFilter("ResourceId", policy.getResource().getResourceId()));

            //insert rows for the policy entries
            for(RoleAssignment assignment : policy.getAssignments())
            {
                Table.insert(null, table, assignment);
            }

            //commit transaction
            if(startedTran)
                scope.commitTransaction();

            //remove the resource-oriented policy from cache
            DbCache.remove(table, cacheName(policy.getResource()));
            notifyPolicyChange(policy.getResource().getResourceId());
        }
        catch(SQLException e)
        {
            //rollback transaction if started
            if(startedTran)
                scope.rollbackTransaction();
            throw new RuntimeSQLException(e);
        }
    }

    public static void deletePolicy(@NotNull SecurableResource resource)
    {
        DbScope scope = core.getSchema().getScope();
        boolean startedTran = false;
        try
        {
            //start a transaction
            if(!scope.isTransactionActive())
            {
                scope.beginTransaction();
                startedTran = true;
            }

            TableInfo table = core.getTableInfoRoleAssignments();

            //delete all rows where resourceid = resource.getResourceId()
            SimpleFilter filter = new SimpleFilter("ResourceId", resource.getResourceId());
            Table.delete(table, filter);
            Table.delete(core.getTableInfoPolicies(), filter);

            //commit transaction
            if(startedTran)
                scope.commitTransaction();

            //remove the resource-oriented policy from cache
            DbCache.remove(table, cacheName(resource));

            //clear the cache for the role assignments table,
            //since we don't know which users might be affected
            DbCache.clear(table);
            notifyPolicyChange(resource.getResourceId());
        }
        catch(SQLException e)
        {
            //rollback transaction if started
            if(startedTran)
                scope.rollbackTransaction();
            throw new RuntimeSQLException(e);
        }

    }

    /**
     * Clears all role assignments for the specified principals for the specified resources.
     * After this call completes, all the specified principals will no longer have any role
     * assignments for the specified resources.
     * @param resources The resources
     * @param principals The principals
     */
    public static void clearRoleAssignments(@NotNull Set<SecurableResource> resources, @NotNull Set<UserPrincipal> principals)
    {
        if(resources.size() == 0 || principals.size() == 0)
            return;

        try
        {
            TableInfo table = core.getTableInfoRoleAssignments();

            SQLFragment sql = new SQLFragment("delete from ");
            sql.append(core.getTableInfoRoleAssignments());
            sql.append(" where ResourceId in(");

            String sep = "";
            SQLFragment resourcesList = new SQLFragment();
            for(SecurableResource resource : resources)
            {
                resourcesList.append(sep);
                resourcesList.append("?");
                resourcesList.add(resource.getResourceId());
                sep = ",";
            }
            sql.append(resourcesList);
            sql.append(") and UserId in(");

            sep = "";
            SQLFragment principalsList = new SQLFragment();
            for(UserPrincipal principal : principals)
            {
                principalsList.append(sep);
                principalsList.append("?");
                principalsList.add(principal.getUserId());
                sep = ",";
            }
            sql.append(principalsList);
            sql.append(")");

            Table.execute(core.getSchema(), sql);

            DbCache.clear(table);
            
            for(SecurableResource resource : resources)
            {
                notifyPolicyChange(resource.getResourceId());
            }
        }
        catch(SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    // Modules register a factory to add module-specific ui to the permissions page
    public static void addViewFactory(ViewFactory vf)
    {
        _viewFactories.add(vf);
    }


    public static List<ViewFactory> getViewFactories()
    {
        return _viewFactories;
    }


    public interface ViewFactory
    {
        public HttpView createView(ViewContext context);
    }


    private static String cacheName(String c, String objectId)
    {
        return _aclPrefix + c + "/" + objectId;
    }

    private static String cacheName(SecurableResource resource)
    {
        return cacheNameForResourceId(resource.getResourceId());
    }

    private static String cacheNameForResourceId(String resourceId)
    {
        return _policyPrefix + "resource/" + resourceId;
    }

    private static String cacheName(UserPrincipal principal)
    {
        return cacheNameForUserId(principal.getUserId());
    }

    private static String cacheNameForUserId(int userId)
    {
        return _policyPrefix + "principal/" + userId;
    }


    public static void removeAll(Container c)
    {
        try
        {
        Table.execute(
                core.getSchema(),
                "DELETE FROM " + core.getTableInfoRoleAssignments() + " WHERE ResourceId IN(SELECT ResourceId FROM " +
                core.getTableInfoPolicies() + " WHERE Container=?)",
                new Object[]{c.getId()});
            Table.execute(
                    core.getSchema(),
                    "DELETE FROM " + core.getTableInfoPolicies() + " WHERE Container=?",
                    new Object[]{c.getId()});
        DbCache.clear(core.getTableInfoRoleAssignments());
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testCreateUser() throws Exception
        {
            ValidEmail email;
            String rawEmail;

            // Just in case, loop until we find one that doesn't exist already
            while (true)
            {
                rawEmail = "test_" + Math.round(Math.random() * 10000) + "@localhost.xyz";
                email = new ValidEmail(rawEmail);
                if (!SecurityManager.loginExists(email)) break;
            }

            String password = createTempPassword();
            String verification = createTempPassword();
            int id = -1;

            // Test create login, create user, verify, login, and delete
            try
            {
                SecurityManager.createLogin(email, password, verification);

                id = UserManager.createUser(email).getUserId();
                assertTrue("createUser", id != 0);

                boolean success = SecurityManager.verify(email, verification);
                assertTrue("verify", success);

                SecurityManager.setVerification(email, null);

                password = createTempPassword();
                SecurityManager.setPassword(email, password);

                User user = AuthenticationManager.authenticate(rawEmail, password);
                assertNotNull("login", user);
                assertEquals("login", user.getUserId(), id);
            }
            finally
            {
                UserManager.deleteUser(id);
            }
        }


        public void testACLS() throws NamingException
        {
            ACL acl = new ACL();

            // User,Guest
            User user = TestContext.get().getUser();
            assertFalse("no permission check", acl.hasPermission(user, ACL.PERM_READ));

            acl.setPermission(user.getUserId(), ACL.PERM_READ);
            assertTrue("read permission", acl.hasPermission(user, ACL.PERM_READ));
            assertFalse("no write permission", acl.hasPermission(user, ACL.PERM_UPDATE));

            acl = new ACL();
            acl.setPermission(Group.groupGuests, ACL.PERM_READ);
            assertTrue("read permission", acl.hasPermission(user, ACL.PERM_READ));
            assertFalse("no write permission", acl.hasPermission(user, ACL.PERM_UPDATE));

            acl.setPermission(Group.groupUsers, ACL.PERM_UPDATE);
            assertTrue("write permission", acl.hasPermission(user, ACL.PERM_UPDATE));
            assertEquals(acl.getPermissions(user), ACL.PERM_READ | ACL.PERM_READOWN | ACL.PERM_UPDATE | ACL.PERM_UPDATEOWN );

            // Guest
            assertTrue("read permission", acl.hasPermission(User.guest, ACL.PERM_READ));
            assertFalse("no write permission", acl.hasPermission(User.guest, ACL.PERM_UPDATE));
            assertEquals(acl.getPermissions(User.guest), ACL.PERM_READ | ACL.PERM_READOWN);
        }


//        public void testEmailValidation()
//        {
//            testEmail("this@that.com", true);
//            testEmail("foo@fhcrc.org", true);
//            testEmail("dots.dots@dots.co.uk", true);
//            testEmail("funny_chars#that%are^allowed&in*email!addresses@that.com", true);
//
//            String displayName = "Personal Name";
//            ValidEmail email = testEmail(displayName + " <personal@name.com>", true);
//            assertTrue("Display name: expected '" + displayName + "' but was '" + email.getPersonal() + "'", displayName.equals(email.getPersonal()));
//
//            String defaultDomain = ValidEmail.getDefaultDomain();
//            // If default domain is defined this should succeed; if it's not defined, this should fail.
//            testEmail("foo", defaultDomain != null && defaultDomain.length() > 0);
//
//            testEmail("~()@bar.com", false);
//            testEmail("this@that.com@con", false);
//            testEmail(null, false);
//            testEmail("", false);
//            testEmail("<@bar.com", false);
//            testEmail(displayName + " <personal>", false);  // Can't combine personal name with default domain
//        }


        private ValidEmail testEmail(String rawEmail, boolean valid)
        {
            ValidEmail email = null;

            try
            {
                email = new ValidEmail(rawEmail);
                assertTrue(rawEmail, valid);
            }
            catch(ValidEmail.InvalidEmailException e)
            {
                assertFalse(rawEmail, valid);
            }

            return email;
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }


    protected static void notifyPolicyChange(String objectID)
    {
        // UNDONE: generalize cross manager/module notifications
        ContainerManager.notifyContainerChange(objectID);
    }

    public static List<ValidEmail> normalizeEmails(String[] rawEmails, List<String> invalidEmails)
    {
        if (rawEmails == null || rawEmails.length == 0)
            return Collections.emptyList();
        return normalizeEmails(Arrays.asList(rawEmails), invalidEmails);
    }

    public static List<ValidEmail> normalizeEmails(List<String> rawEmails, List<String> invalidEmails)
    {
        if (rawEmails == null || rawEmails.size() == 0)
            return Collections.emptyList();

        List<ValidEmail> emails = new ArrayList<ValidEmail>(rawEmails.size());

        for (String rawEmail : rawEmails)
        {
            try
            {
                emails.add(new ValidEmail(rawEmail));
            }
            catch(ValidEmail.InvalidEmailException e)
            {
                invalidEmails.add(rawEmail);
            }
        }

        return emails;
    }

    public static SecurityMessage getRegistrationMessage(String mailPrefix, boolean isAdminCopy) throws Exception
    {
        SecurityMessage sm = new SecurityMessage();

        EmailTemplate et = EmailTemplateService.get().getEmailTemplate(
                isAdminCopy ? RegistrationAdminEmailTemplate.class.getName()
                            : RegistrationEmailTemplate.class.getName());
        sm.setMessagePrefix(mailPrefix);
        sm.setEmailTemplate((SecurityEmailTemplate)et);
        sm.setType("User Registration Email");

        return sm;
    }

    public static SecurityMessage getResetMessage(boolean isAdminCopy) throws Exception
    {
        SecurityMessage sm = new SecurityMessage();

        EmailTemplate et = EmailTemplateService.get().getEmailTemplate(
                isAdminCopy ? PasswordResetAdminEmailTemplate.class.getName()
                            : PasswordResetEmailTemplate.class.getName());
        sm.setEmailTemplate((SecurityEmailTemplate)et);
        sm.setType("Reset Password Email");
        return sm;
    }

    /**
     * @return null if the user already existed, or a message indicating success/failure
     */
    public static String addUser(ViewContext context, ValidEmail email, boolean sendMail, String mailPrefix, Pair<String, String>[] extraParameters) throws Exception
    {
        if (SecurityManager.loginExists(email))
        {
            return null;
        }

        StringBuilder message = new StringBuilder();
        NewUserBean newUserBean;

        ActionURL messageContentsURL = null;
        boolean appendClickToSeeMail = false;
        User currentUser = context.getUser();

        try
        {
            newUserBean = SecurityManager.addUser(email);

            if (!newUserBean.isLdap() && sendMail)
            {
                Container c = context.getContainer();
                messageContentsURL = PageFlowUtil.urlProvider(SecurityUrls.class).getShowRegistrationEmailURL(c, email.getEmailAddress(), mailPrefix);

                ActionURL verificationURL = createVerificationURL(context.getContainer(), email.getEmailAddress(),
                        newUserBean.getVerification(), extraParameters);

                SecurityManager.sendEmail(c, currentUser, getRegistrationMessage(mailPrefix, false), email.getEmailAddress(), verificationURL);
                if (!currentUser.getEmail().equals(email.getEmailAddress()))
                {
                    SecurityMessage msg = getRegistrationMessage(mailPrefix, true);
                    msg.setTo(email.getEmailAddress());
                    SecurityManager.sendEmail(c, currentUser, msg, currentUser.getEmail(), verificationURL);
                }
                appendClickToSeeMail = true;
            }

            User newUser = newUserBean.getUser();

            if (newUserBean.isLdap())
            {
                message.append(newUser.getEmail()).append(" added as a new user to the system.  This user will be authenticated via LDAP.");
                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system.  This user will be authenticated via LDAP.");
            }
            else if (sendMail)
            {
                message.append(email.getEmailAddress()).append(" added as a new user to the system and emailed successfully.");
                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system.  Verification email was sent successfully.");
            }
            else
            {
                String href = "<a href=\"" + PageFlowUtil.filter(createVerificationURL(context.getContainer(),
                        email.getEmailAddress(), newUserBean.getVerification(), extraParameters)) + "\" target=\"" + email.getEmailAddress() + "\">here</a>";
                message.append(email.getEmailAddress()).append(" added as a new user to the sytem, but no email was sent.  Click ");
                message.append(href).append(" to change the password from the random one that was assigned.");
                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system and the administrator chose not to send a verification email.");
            }
        }
        catch (MessagingException e)
        {
            message.append("<br>");
            message.append(email.getEmailAddress());
            message.append(" was added successfully, but could not be emailed due to a failure:<br><pre>");
            message.append(e.getMessage());
            message.append("</pre>");
            appendMailHelpText(message, messageContentsURL);

            User newUser = UserManager.getUser(email);

            if (null != newUser)
                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system.  Sending the verification email failed.");
        }
        catch (SecurityManager.UserAlreadyExistsException e)
        {
            return null;
        }
        catch (SecurityManager.UserManagementException e)
        {
            message.append("Failed to create user ").append(email).append(": ").append(e.getMessage());
        }

        if (appendClickToSeeMail && messageContentsURL != null)
        {
            String href = "<a href=" + PageFlowUtil.filter(messageContentsURL) + " target=\"_blank\">here</a>";
            message.append(" Click ").append(href).append(" to see the email.");
        }

        return message.toString();
    }

    private static void appendMailHelpText(StringBuilder sb, ActionURL messageContentsURL)
    {
        sb.append("You can attempt to resend this mail later by going to the Site Users link, clicking on the appropriate user from the list, and resetting their password.");
        if (messageContentsURL != null)
        {
            sb.append(" Alternatively, you can copy the <a href=\"");
            sb.append(PageFlowUtil.filter(messageContentsURL));
            sb.append("\" target=\"_blank\">contents of the message</a> into an email client and send it to the user manually.");
        }
        sb.append("</p>");
        sb.append("<p>For help on fixing your mail server settings, please consult the SMTP section of the <a href=\"");
        sb.append((new HelpTopic("cpasxml", HelpTopic.Area.SERVER)).getHelpTopicLink());
        sb.append("\" target=\"_blank\">LabKey documentation on modifying your configuration file</a>.<br>");
    }


    public static void createNewProjectGroups(Container project)
    {
        /*
        this check doesn't work well when moving container
        if (!project.isProject())
            throw new IllegalArgumentException("Must be a top level container");
        */

        // Create default groups
        //Note: we are no longer creating the project-level Administrators group
        Group userGroup = SecurityManager.createGroup(project, "Users");

        // Set default permissions
        // CONSIDER: get/set permissions on Container, rather than going behind its back
        Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
        SecurityPolicy policy = new SecurityPolicy(project);
        policy.addRoleAssignment(userGroup, noPermsRole);

        //users and guests have no perms by default
        policy.addRoleAssignment(getGroup(Group.groupUsers), noPermsRole);
        policy.addRoleAssignment(getGroup(Group.groupGuests), noPermsRole);
        
        savePolicy(policy);
    }

    public static void setAdminOnlyPermissions(Container c)
    {
        SecurityPolicy policy = new SecurityPolicy(c);

        //assign all principals who are project admins at the project level to the folder admin role in the container
        SecurityPolicy projectPolicy = c.getProject().getPolicy();
        Role projAdminRole = RoleManager.getRole(ProjectAdminRole.class);
        Role folderAdminRole = RoleManager.getRole(FolderAdminRole.class);
        for(RoleAssignment ra : projectPolicy.getAssignments())
        {
            if (ra.getRole().equals(projAdminRole))
            {
                UserPrincipal principal = getPrincipal(ra.getUserId());
                if(null != principal)
                    policy.addRoleAssignment(principal, folderAdminRole);
            }
        }

        savePolicy(policy);
    }

    public static boolean isAdminOnlyPermissions(Container c)
    {
        Set<Role> adminRoles = new HashSet<Role>();
        adminRoles.add(RoleManager.getRole(SiteAdminRole.class));
        adminRoles.add(RoleManager.getRole(ProjectAdminRole.class));
        adminRoles.add(RoleManager.getRole(FolderAdminRole.class));

        SecurityPolicy policy = c.getPolicy();
        for(RoleAssignment ra : policy.getAssignments())
        {
            if(!adminRoles.contains(ra.getRole()))
                return false;
        }
        return true;
    }

    public static void setInheritPermissions(Container c)
    {
        deletePolicy(c);
    }

    private static final String SUBFOLDERS_INHERIT_PERMISSIONS_NAME = "SubfoldersInheritPermissions";
    
    public static boolean shouldNewSubfoldersInheritPermissions(Container project)
    {
        Map<String, String> props = PropertyManager.getProperties(project.getId(), SUBFOLDERS_INHERIT_PERMISSIONS_NAME, false);
        boolean subfoldersInherit = props != null && "true".equals(props.get(SUBFOLDERS_INHERIT_PERMISSIONS_NAME));
        return subfoldersInherit;
    }

    public static void setNewSubfoldersInheritPermissions(Container project, boolean inherit)
    {
        Map<String, String> props = PropertyManager.getWritableProperties(project.getId(), SUBFOLDERS_INHERIT_PERMISSIONS_NAME, true);
        props.put(SUBFOLDERS_INHERIT_PERMISSIONS_NAME, Boolean.toString(inherit));
        PropertyManager.saveProperties(props);
    }


    public static void changeProject(Container c, Container oldProject, Container newProject)
            throws SQLException
    {
        assert core.getSchema().getScope().isTransactionActive();

        if (oldProject.getId().equals(newProject.getId()))
            return;

        /* when demoting a project to a regular folder, delete the project groups */
        if (oldProject == c)
        {
            org.labkey.api.security.SecurityManager.deleteGroups(c,Group.typeProject);
        }

        /*
         * Clear all ACLS for folders that changed project!
         */
        Container[] subtrees = ContainerManager.getAllChildren(c);
        StringBuilder sb = new StringBuilder();
        String comma = "";
        for (Container sub : subtrees)
        {
            sb.append(comma);
            sb.append("'");
            sb.append(sub.getId());
            sb.append("'");
            comma = ",";
        }
        Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoRoleAssignments() + "\n" +
            "WHERE ResourceId IN (SELECT ResourceId FROM " + core.getTableInfoPolicies() + " WHERE Container IN (" +
                sb.toString() + "))", null);
        Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoPolicies() + "\n" +
            "WHERE Container IN (" + sb.toString() + ")", null);
        DbCache.clear(core.getTableInfoRoleAssignments());

        /* when promoting a folder to a project, create default project groups */
        if (newProject == c)
        {
            createNewProjectGroups(c);
        }
    }

    public abstract static class SecurityEmailTemplate extends EmailTemplate
    {
        protected String _optionalPrefix;
        private String _verificationUrl = "";
        private String _emailAddress = "";
        private String _recipient = "";
        protected boolean _verificationUrlRequired = true;
        private List<ReplacementParam> _replacements = new ArrayList<ReplacementParam>();

        protected SecurityEmailTemplate(String name)
        {
            super(name);

            _replacements.add(new ReplacementParam("verificationURL", "Link for a user to set a password"){
                public String getValue(Container c) {return _verificationUrl;}
            });
            _replacements.add(new ReplacementParam("emailAddress", "The email address of the user performing the operation"){
                public String getValue(Container c) {return _emailAddress;}
            });
            _replacements.add(new ReplacementParam("recipient", "The email address on the 'to:' line"){
                public String getValue(Container c) {return _recipient;}
            });
            _replacements.addAll(super.getValidReplacements());
        }

        public void setOptionPrefix(String optionalPrefix){_optionalPrefix = optionalPrefix;}
        public void setVerificationUrl(String verificationUrl){_verificationUrl = verificationUrl;}
        public void setEmailAddress(String emailAddress){_emailAddress = emailAddress;}
        public void setRecipient(String recipient){_recipient = recipient;}
        public List<ReplacementParam> getValidReplacements(){return _replacements;}

        public boolean isValid(String[] error)
        {
            if (super.isValid(error))
            {
                // add an additional requirement for the verification url
                if (!_verificationUrlRequired || getBody().indexOf("%verificationURL%") != -1)
                {
                    return true;
                }
                error[0] = "The substitution param: %verificationURL% is required to be somewhere in the body of the message";
            }
            return false;
        }
    }

    public static class RegistrationEmailTemplate extends SecurityEmailTemplate
    {
        protected static final String DEFAULT_SUBJECT =
                "Welcome to the %organizationName% %siteShortName% Web Site new user registration";
        protected static final String DEFAULT_BODY =
                "You now have an account on the %organizationName% %siteShortName% web site.  We are sending " +
                "you this message to verify your email address and to allow you to create a password that will provide secure " +
                "access to your data on the web site.  To complete the registration process, simply click the link below or " +
                "copy it to your browser's address bar.  You will then be asked to choose a password.\n\n" +
                "%verificationURL%\n\n" +
                "Note: The link above should appear on one line, starting with 'http' and ending with your email address.  Some " +
                "email systems may break this link into multiple lines, causing the verification to fail.  If this happens, " +
                "you'll need to paste the parts into the address bar of your browser to form the full link.\n\n" +
                "The %siteShortName% home page is %homePageURL%.  When you visit the home page " +
                "and log in with your new password you will see a list of projects on the left side of the page.  Click those " +
                "links to visit your projects.\n\n" +
                "If you have any questions don't hesitate to contact the %siteShortName% team at %emailAddress%.";

        public RegistrationEmailTemplate()
        {
            super("Register new user");
            setSubject(DEFAULT_SUBJECT);
            setBody(DEFAULT_BODY);
            setDescription("Sent to the new user and administrator when a user is added to the site.");
            setPriority(1);
        }

        public String renderBody(Container c)
        {
            StringBuffer sb = new StringBuffer();

            if (_optionalPrefix != null)
            {
                sb.append(_optionalPrefix);
                sb.append("\n\n");
            }
            return sb.append(super.renderBody(c)).toString();
        }
    }

    public static class RegistrationAdminEmailTemplate extends RegistrationEmailTemplate
    {
        public RegistrationAdminEmailTemplate()
        {
            super();
            setName("Register new user (bcc to admin)");
            setSubject("%recipient% : " + DEFAULT_SUBJECT);
            setBody("The following message was sent to %recipient% :\n\n" + DEFAULT_BODY);
            setPriority(2);
            _verificationUrlRequired = false;
        }
    }

    public static class PasswordResetEmailTemplate extends SecurityEmailTemplate
    {
        protected static final String DEFAULT_SUBJECT =
                "Reset Password Notification from the %siteShortName% Web Site";
        protected static final String DEFAULT_BODY =
                "We have reset your password on the %organizationName% %siteShortName% web site. " +
                "To sign in to the system you will need " +
                "to specify a new password.  Click the link below or copy it to your browser's address bar.  You will then be " +
                "asked to enter a new password.\n\n" +
                "%verificationURL%\n\n" +
                "The %siteShortName% home page is %homePageURL%.  When you visit the home page and log " +
                "in with your new password you will see a list of projects on the left side of the page.  Click those links to " +
                "visit your projects.";

        public PasswordResetEmailTemplate()
        {
            super("Reset password");
            setSubject(DEFAULT_SUBJECT);
            setBody(DEFAULT_BODY);
            setDescription("Sent to the user and administrator when the password of a user is reset.");
            setPriority(3);
        }
    }

    public static class PasswordResetAdminEmailTemplate extends PasswordResetEmailTemplate
    {
        public PasswordResetAdminEmailTemplate()
        {
            super();
            setName("Reset password (bcc to admin)");
            setSubject("%recipient% : " + DEFAULT_SUBJECT);
            setBody("The following message was sent to %recipient% :\n\n" + DEFAULT_BODY);
            setPriority(4);
            _verificationUrlRequired = false;
        }
    }

    /**
     * Returns a group name that disambiguates between site/project Administrators
     * and site/project Users
     * @param group the group
     * @return The disambiguated name of the group
     */
    public static String getDisambiguatedGroupName(Group group)
    {
        int id = group.getUserId();
        switch(id)
        {
            case Group.groupAdministrators:
                return "Site Administrators";
            case Group.groupUsers:
                return "All Site Users";
            default:
                return group.getName();
        }
    }
}
