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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.audit.provider.GroupAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.AuthenticationConfiguration.PrimaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationManager.AuthenticationValidator;
import org.labkey.api.security.AuthenticationProvider.AuthenticationResponse;
import org.labkey.api.security.AuthenticationProvider.ResetPasswordProvider;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.security.impersonation.DisallowGlobalRolesContext;
import org.labkey.api.security.impersonation.GroupImpersonationContextFactory;
import org.labkey.api.security.impersonation.ImpersonationContextFactory;
import org.labkey.api.security.impersonation.ReadOnlyImpersonatingContext;
import org.labkey.api.security.impersonation.RoleImpersonationContextFactory;
import org.labkey.api.security.impersonation.UserImpersonationContextFactory;
import org.labkey.api.security.permissions.AbstractPermission;
import org.labkey.api.security.permissions.AddUserPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.SeeUserDetailsPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.AbstractRole;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.util.emailTemplate.UserOriginatedEmailTemplate;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HasHttpRequest;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.webdav.permissions.SeeFilePathsPermission;
import org.labkey.api.writer.ContainerUser;
import org.labkey.security.xml.GroupEnumType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.server.HandshakeRequest;
import java.beans.PropertyChangeListener;
import java.io.Closeable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

/**
 * Responsible for user authentication, creating or modifying groups, and similar user/group operations.
 * Note should consider implementing a Tomcat REALM, but we've tried to avoid being tomcat specific.
 */

public class SecurityManager
{
    private static final Logger _log = LogHelper.getLogger(SecurityManager.class, "User and group creation and management");
    private static final CoreSchema core = CoreSchema.getInstance();
    private static final List<ViewFactory> VIEW_FACTORIES = new CopyOnWriteArrayList<>();
    private static final List<TermsOfUseProvider> TERMS_OF_USE_PROVIDERS = new CopyOnWriteArrayList<>();

    static final String NULL_GROUP_ERROR_MESSAGE = "Null group not allowed";
    static final String NULL_PRINCIPAL_ERROR_MESSAGE = "Null principal not allowed";
    static final String ALREADY_A_MEMBER_ERROR_MESSAGE = "Principal is already a member of this group";
    static final String ADD_GROUP_TO_ITSELF_ERROR_MESSAGE = "Can't add a group to itself";
    static final String ADD_TO_SYSTEM_GROUP_ERROR_MESSAGE = "Can't add a group to a system group";
    static final String ADD_SYSTEM_GROUP_ERROR_MESSAGE = "Can't add a system group to another group";
    static final String DIFFERENT_PROJECTS_ERROR_MESSAGE = "Can't add a project group to a group in a different project";
    static final String PROJECT_TO_SITE_ERROR_MESSAGE = "Can't add a project group to a site group";
    static final String CIRCULAR_GROUP_ERROR_MESSAGE = "Can't add a group that results in a circular group relation";

    public static final String TRANSFORM_SESSION_ID = "LabKeyTransformSessionId";  // issue 19748
    public static final String API_KEY = "apikey";

    public static final String USER_ID_KEY = User.class.getName() + "$userId";
    private static final String IMPERSONATION_CONTEXT_FACTORY_KEY = User.class.getName() + "$ImpersonationContextFactoryKey";
    private static final String AUTHENTICATION_VALIDATORS_KEY = SecurityManager.class.getName() + "$AuthenticationValidators";
    private static final String AUTHENTICATION_METHOD = "SecurityManager.authenticationMethod";

    public static final String PRIMARY_AUTHENTICATION_CONFIGURATION = PrimaryAuthenticationConfiguration.class.getName();
    public static final String AUTHENTICATION_ATTRIBUTES_KEY = User.class.getName() + "$AuthenticationAttributes";

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

        private final int _permissions;
        private final String _label;

        PermissionSet(String label, int permissions)
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

        public int getPermissions()
        {
            return _permissions;
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

    public enum PermissionTypes
    {
        READ(ReadPermission.class),
        INSERT(InsertPermission.class),
        UPDATE(UpdatePermission.class),
        DELETE(DeletePermission.class),
        ADMIN(AdminPermission.class);

        private final Class<? extends Permission> _permission;

        PermissionTypes(Class<? extends Permission> permission)
        {
            _permission = permission;
        }

        public Class<? extends Permission> getPermission()
        {
            return _permission;
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
        //  a) the container is non-existent or
        //  b) the container is not longer a project

        scrubTables();

        ContainerManager.addContainerListener(new SecurityContainerListener());
        UserManager.addUserListener(new SecurityUserListener());
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
    private static final List<GroupListener> _listeners = new CopyOnWriteArrayList<>();

    public static void addGroupListener(GroupListener listener)
    {
        addGroupListener(listener, false);
    }

    /** Adds a listener with option to specify that it needs to be executed before the other listeners */
    public static void addGroupListener(GroupListener listener, boolean meFirst)
    {
        if (meFirst)
            _listeners.add(0, listener);
        else
            _listeners.add(listener);
    }

    private static List<GroupListener> getListeners()
    {
        return _listeners;
    }

    private static void fireAddPrincipalToGroup(Group group, UserPrincipal user)
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
        List<Throwable> errors = new ArrayList<>();
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
        Container root = ContainerManager.getRoot();
        SqlExecutor executor = new SqlExecutor(core.getSchema());

        // missing container
        executor.execute("DELETE FROM " + core.getTableInfoPrincipals() + "\n" +
                "WHERE Container NOT IN (SELECT EntityId FROM " + core.getTableInfoContainers() + ")");

        // container is not a project (but should be)
        executor.execute("DELETE FROM " + core.getTableInfoPrincipals() + "\n" +
                "WHERE Type='g' AND Container NOT IN (SELECT EntityId FROM " + core.getTableInfoContainers() + "\n" +
                "\tWHERE Parent=? OR Parent IS NULL)", root);

        // missing group
        executor.execute("DELETE FROM " + core.getTableInfoMembers() + "\n" +
                "WHERE GroupId NOT IN (SELECT UserId FROM " + core.getTableInfoPrincipals() + ")");

        // missing user
        executor.execute("DELETE FROM " + core.getTableInfoMembers() + "\n" +
                "WHERE UserId NOT IN (SELECT UserId FROM " + core.getTableInfoPrincipals() + ")");
    }


    /** Move is handled by direct call from ContainerManager into SecurityManager */
    private static class SecurityContainerListener extends ContainerManager.AbstractContainerListener
    {
        @Override
        public void containerDeleted(Container c, User user)
        {
            deleteGroups(c, null);
        }
    }


    private static class SecurityUserListener implements UserManager.UserListener
    {
        @Override
        public void userDeletedFromSite(User user)
        {
            // This clears the cache of security policies. It does not remove the policies themselves.
            SecurityPolicyManager.removeAll();
        }

        @Override
        public void userAccountDisabled(User user)
        {
            // This clears the cache of security policies. It does not remove the policies themselves.
            SecurityPolicyManager.removeAll();
        }
    }

    private static @Nullable Pair<String, String> getBasicCredentials(HttpServletRequest request)
    {
        // Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
        String authorization = request.getHeader("Authorization");
        Pair<String, String> ret = null;

        if (null != authorization && authorization.startsWith("Basic"))
        {
            String basic = authorization.substring("Basic".length()).trim();
            byte[] decode = Base64.decodeBase64(basic.getBytes());
            String auth = new String(decode);
            int colon = auth.indexOf(':');
            if (-1 == colon)
                return null;
            String username = auth.substring(0, colon);
            String password = auth.substring(colon+1);

            ret = new Pair<>(username, password);
        }

        return ret;
    }

    // Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
    private static @Nullable User authenticateBasic(HttpServletRequest request, @NotNull Pair<String, String> basicCredentials)
    {
        try
        {
            String rawEmail = basicCredentials.getKey();
            String password = basicCredentials.getValue();
            if (rawEmail.equalsIgnoreCase("guest"))
                return User.guest;
            new ValidEmail(rawEmail);  // validate email address

            return AuthenticationManager.authenticate(request, rawEmail, password);
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            return null;  // Invalid email means failed auth
        }
    }

    public static boolean isBasicAuthentication(HttpServletRequest request)
    {
        return "Basic".equals(request.getAttribute(AUTHENTICATION_METHOD));
    }

    public static User getSessionUser(HttpServletRequest request)
    {
        User sessionUser = getSessionUser(request.getSession(false));
        if (sessionUser != null && !sessionUser.isActive())
        {
            SecurityManager.logoutUser(request, sessionUser, null);
            return null;
        }
        return sessionUser;
    }

    public static User getSessionUser(HandshakeRequest request)
    {
        HttpSession session = (HttpSession) request.getHttpSession();
        User sessionUser = getSessionUser(session);
        if (sessionUser != null && !sessionUser.isActive())
        {
            session.invalidate();
            return null;
        }
        return sessionUser;
    }

    private static User getSessionUser(HttpSession session)
    {
        User sessionUser = null;

        Integer userId = null == session ? null : (Integer) session.getAttribute(USER_ID_KEY);

        if (null != userId)
            sessionUser = UserManager.getUser(userId);

        return sessionUser;
    }

    public static Pair<User, HttpServletRequest> attemptAuthentication(HttpServletRequest request) throws UnsupportedEncodingException
    {
        @Nullable Pair<String, String> basicCredentials = getBasicCredentials(request);
        @Nullable String apiKey = getApiKey(basicCredentials, request);

        // Handle session API key early, if present and valid
        if (apiKey != null && apiKey.startsWith(SessionApiKeyManager.get().getKeyPrefix()))
        {
            HttpSession session = SessionApiKeyManager.get().getContext(apiKey);

            if (null != session)
            {
                request = new SessionReplacingRequest(request, session);
            }
        }

        assert null == request.getUserPrincipal();

        User u = null;
        HttpSession session = request.getSession(false);
        User sessionUser = getSessionUser(request);

        if (null != sessionUser)
        {
            // NOTE: UserCache.getUser() above returns a cloned object so _groups should be null. This is important to ensure
            // group memberships are calculated on every request (but just once)
            assert sessionUser._groups == null;

            ImpersonationContextFactory factory = (ImpersonationContextFactory)session.getAttribute(IMPERSONATION_CONTEXT_FACTORY_KEY);

            if (null != factory)
            {
                sessionUser.setImpersonationContext(factory.getImpersonationContext());
            }
            else if ("true".equalsIgnoreCase(request.getHeader("LabKey-Disallow-Global-Roles")))
            {
                sessionUser.setImpersonationContext(DisallowGlobalRolesContext.get());
            }

            List<AuthenticationValidator> validators = getValidators(session);

            // If we have validators, enumerate them to validate the session user's current login (e.g., smart card is still present)
            if (null != validators)
            {
                boolean valid = true;

                // Enumerate all validators on every request (no short circuit) in case the validators have internal state or side effects
                for (AuthenticationValidator validator : validators)
                {
                    valid &= validator.test(request);
                }

                if (!valid)
                {
                    // If impersonating, stop so it gets logged
                    if (sessionUser.isImpersonated())
                    {
                        SecurityManager.stopImpersonating(request, factory);
                        sessionUser = sessionUser.getImpersonatingUser(); // Need to logout the admin
                    }

                    // Now logout the session user
                    logoutUser(request, sessionUser, null);
                    sessionUser = null;
                }
            }

            u = sessionUser;
        }

        if (null == u && null != apiKey && apiKey.startsWith("apikey|"))
        {
            u = ApiKeyManager.get().authenticateFromApiKey(apiKey);

            if (null != u)
                request.setAttribute(AUTHENTICATION_METHOD, "Basic");
        }

        if (null == u && null != basicCredentials)
        {
            u = authenticateBasic(request, basicCredentials);
            if (null != u)
            {
                request.setAttribute(AUTHENTICATION_METHOD, "Basic");
                // accept Guest as valid credentials from authenticateBasic()
                return new Pair<>(u, request);
            }
        }

//  We don't register any RequestAuthenticationProvider implementations, so don't bother
//        if (null == u)
//        {
//            u = AuthenticationManager.attemptRequestAuthentication(request);
//        }

        return null == u || u.isGuest() ? null : new Pair<>(u, request);
    }

    /**
     * Determine if an API key is present, checking basic auth first, then "apikey" header, and then the special "transform"
     * cookie and parameters. Return the API key if it's present; otherwise return null.
     * @param basicCredentials Basic auth credentials
     * @param request Current request
     * @return First API key found or null if an apikey is not present.
     */
    private static @Nullable String getApiKey(@Nullable Pair<String, String> basicCredentials, HttpServletRequest request) throws UnsupportedEncodingException
    {
        String apiKey;

        // Prefer Basic auth
        if (null != basicCredentials && API_KEY.equals(basicCredentials.getKey()))
        {
            apiKey = basicCredentials.getValue();
        }
        else
        {
            // Support "apikey" header for backward compatibility. We might stop supporting this at some point.
            apiKey = request.getHeader(API_KEY);
            if (null == apiKey)
            {
                // Issue 40482: Deprecate using 'LabKeyTransformSessionId' in preference for 'apikey' authentication
                // issue 19748: need alternative to JSESSIONID for pipeline job transform script usage
                apiKey = PageFlowUtil.getCookieValue(request.getCookies(), TRANSFORM_SESSION_ID, null);
                if (null != apiKey)
                {
                    _log.warn("Using '" + TRANSFORM_SESSION_ID + "' cookie for authentication is deprecated; use 'apikey' instead");
                }
                else
                {
                    // Support as a GET parameter as well, not just as a cookie, to support authentication
                    // through SSRS which can't be made to use BasicAuth, pass cookies, or other HTTP headers.
                    // Do not use request.getParameter() since that will consume the POST body, #32711.
                    try
                    {
                        Map<String, String> params = PageFlowUtil.mapFromQueryString(request.getQueryString());
                        apiKey = params.get(TRANSFORM_SESSION_ID);
                    }
                    catch (IllegalArgumentException e)
                    {
                        throw new UnsupportedEncodingException(e.getMessage());
                    }
                }
            }
        }

        return apiKey;
    }

    public static final int SECONDS_PER_DAY = 60*60*24;

    public static abstract class TransformSession implements Closeable
    {
        private final String _apikey;

        protected TransformSession(String apiKey)
        {
            _apikey = apiKey;
        }

        public String getApiKey()
        {
            return _apikey;
        }

        @Override
        public abstract void close();
    }

    private static final class HttpSessionTransformSession extends TransformSession
    {
        /** Creates http session apiKey */
        private HttpSessionTransformSession(@NotNull HttpServletRequest req, @NotNull HttpSession session)
        {
            super(SessionApiKeyManager.get().createKey(req, session));
        }

        @Override
        public void close()
        {
            SessionApiKeyManager.get().invalidateKey(getApiKey());
        }
    }

    private static final class DatabaseTransformSession extends TransformSession
    {
        /** Creates database apiKey */
        private DatabaseTransformSession(@NotNull User user)
        {
            super(ApiKeyManager.get().createKey(user, SECONDS_PER_DAY));
        }

        @Override
        public void close()
        {
            ApiKeyManager.get().deleteKey(getApiKey());
        }
    }

    /**
     * Works like a standard HTTP session but intended for transform scripts and other API-style usage.
     * Callers must call this in a try-with-resources block to ensure the session is closed (e.g., API key is deleted).
     *
     * This form is intended for background jobs. Prefer using the other form of this method that accepts an
     * HttpSession instead to avoid these limitations:
     * <ul>
     *     <li>inserts the apikey into the database so requires a {@link MutatingApiAction} or
     *     {@link SpringActionController#ignoreSqlUpdates()}</li>
     *     <li>can't be used when impersonating -- see Issue 34580</li>
     * </ul>
     *
     * @return a TransformSession representing the newly created session
     */
    public static @NotNull TransformSession createTransformSession(@NotNull User user)
    {
        return new DatabaseTransformSession(user);
    }

    /**
     * Works like a standard HTTP session but intended for transform scripts and other API-style usage.
     * Callers must call this in a try-with-resources block to ensure the session is closed (e.g., API key is deleted).
     *
     * This form creates a apiKey tied to the user's http session.
     *
     * @return a TransformSession representing the newly created session
     */
    public static @NotNull TransformSession createTransformSession(@NotNull HttpServletRequest req, @NotNull HttpSession session)
    {
        return new HttpSessionTransformSession(req, session);
    }

    /**
     * Works like a standard HTTP session but intended for transform scripts and other API-style usage.
     * Callers must call this in a try-with-resources block to ensure the session is closed (e.g., API key is deleted).
     * @param context A ViewContext or AssayRunUploadContext
     * @return a TransformSession representing the newly created session
     */
    public static @NotNull <T extends ContainerUser & HasHttpRequest> TransformSession createTransformSession(@NotNull T context)
    {
        HttpServletRequest request = context.getRequest();
        if (request != null && !ViewServlet.isMockRequest(request) && request.getSession() != null)
            return createTransformSession(request, request.getSession());
        else
            return createTransformSession(context.getUser());
    }

    public static HttpSession setAuthenticatedUser(HttpServletRequest request, @Nullable AuthenticationResponse response, User user, boolean invalidate)
    {
        SessionHelper.clearSession(request, invalidate, PageFlowUtil.set(WikiTermsOfUseProvider.TERMS_APPROVED_KEY));
        if (!user.isGuest() && request instanceof AuthenticatedRequest)
            ((AuthenticatedRequest)request).convertToLoggedInSession();

        HttpSession newSession = request.getSession(true);
        newSession.setAttribute(USER_ID_KEY, user.getUserId());
        newSession.setAttribute("LABKEY.username", user.getName());

        if (null != response)
        {
            PrimaryAuthenticationConfiguration<?> configuration = response.getConfiguration();
            newSession.setAttribute(PRIMARY_AUTHENTICATION_CONFIGURATION, configuration.getRowId());
            newSession.setAttribute(AUTHENTICATION_ATTRIBUTES_KEY, response.getAttributeMap());
        }

        return newSession;
    }

    public static URLHelper logoutUser(HttpServletRequest request, User user, @Nullable URLHelper returnURL)
    {
        URLHelper ret = AuthenticationManager.logout(user, request, returnURL);   // Let AuthenticationProvider clean up auth-specific cookies, etc.
        SessionHelper.clearSession(request, true);
        return ret;
    }

    public static void impersonateUser(ViewContext viewContext, User impersonatedUser, ActionURL returnURL)
    {
        @Nullable Container project = viewContext.getContainer().getProject();
        User user = viewContext.getUser();

        if (user.hasRootAdminPermission())
            project = null;

        impersonate(viewContext, new UserImpersonationContextFactory(project, user, impersonatedUser, returnURL));
    }

    public static void impersonateGroup(ViewContext viewContext, Group group, ActionURL returnURL)
    {
        @Nullable Container project = viewContext.getContainer().getProject();
        impersonate(viewContext, new GroupImpersonationContextFactory(project, viewContext.getUser(), group, returnURL));
    }

    public static void impersonateRoles(ViewContext viewContext, Collection<Role> newImpersonationRoles, Set<Role> currentImpersonationRoles, ActionURL returnURL)
    {
        @Nullable Container project = viewContext.getContainer().getProject();
        User user = viewContext.getUser();

        if (user.hasRootAdminPermission())
            project = null;

        impersonate(viewContext, new RoleImpersonationContextFactory(project, user, newImpersonationRoles, currentImpersonationRoles, returnURL));
    }

    private static void impersonate(ViewContext viewContext, ImpersonationContextFactory factory)
    {
        // Tell the factory to start impersonating
        factory.startImpersonating(viewContext);

        // Stash the factory in session
        HttpServletRequest request = viewContext.getRequest();
        HttpSession session = request.getSession(true);
        session.setAttribute(IMPERSONATION_CONTEXT_FACTORY_KEY, factory);
    }

    public static void stopImpersonating(HttpServletRequest request, ImpersonationContextFactory factory)
    {
        factory.stopImpersonating(request);

        // Remove factory from session
        HttpSession session = request.getSession(true);
        session.removeAttribute(IMPERSONATION_CONTEXT_FACTORY_KEY);
    }

    public static void setValidators(HttpSession session, List<AuthenticationValidator> validators)
    {
        if (validators.isEmpty())
            session.removeAttribute(AUTHENTICATION_VALIDATORS_KEY);
        else
            session.setAttribute(AUTHENTICATION_VALIDATORS_KEY, validators);
    }

    public static @Nullable List<AuthenticationValidator> getValidators(HttpSession session)
    {
        return (List<AuthenticationValidator>)session.getAttribute(AUTHENTICATION_VALIDATORS_KEY);
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

    public static ActionURL createVerificationURL(Container c, ValidEmail email, String verification, @Nullable List<Pair<String, String>> extraParameters)
    {
        return PageFlowUtil.urlProvider(LoginUrls.class).getVerificationURL(c, email, verification, extraParameters);
    }

    public static ActionURL createModuleVerificationURL(Container c, ValidEmail email, String verification, @Nullable List<Pair<String, String>> extraParameters, String provider, boolean isAddUser)
    {
        ActionURL defaultUrl = createVerificationURL(c, email, verification, extraParameters);
        if (provider == null)
            return defaultUrl;

        ResetPasswordProvider urlProvider = AuthenticationManager.getResetPasswordProvider(provider);
        if (urlProvider == null)
            return defaultUrl;

        ActionURL verificationUrl = urlProvider.getAPIVerificationURL(c, isAddUser);
        verificationUrl.addParameter("verification", verification);
        verificationUrl.addParameter("email", email.getEmailAddress());

        if (null != extraParameters)
            verificationUrl.addParameters(extraParameters);

        return verificationUrl;
    }

    // Test if non-LDAP email has been verified
    public static boolean isVerified(ValidEmail email)
    {
        return (null == getVerification(email));
    }

    public static boolean verify(ValidEmail email, String verification)
    {
        String dbVerification = getVerification(email);
        return (dbVerification != null && dbVerification.equals(verification));
    }

    public static void setVerification(ValidEmail email, @Nullable String verification) throws UserManagementException
    {
        int rows = new SqlExecutor(core.getSchema()).execute("UPDATE " + core.getTableInfoLogins() + " SET Verification=? WHERE LOWER(email)=LOWER(?)", verification, email.getEmailAddress());
        if (1 != rows)
            throw new UserManagementException(email, "Unexpected number of rows returned when setting verification: " + rows);
    }

    public static String getVerification(ValidEmail email)
    {
        return new SqlSelector(core.getSchema(), "SELECT Verification FROM " + core.getTableInfoLogins() + " WHERE Email = ?", email.getEmailAddress()).getObject(String.class);
    }

    public static class NewUserStatus
    {
        private final ValidEmail _email;

        private String _verification;
        private User _user;

        public NewUserStatus(ValidEmail email)
        {
            _email = email;
        }

        public ValidEmail getEmail()
        {
            return _email;
        }

        @Deprecated // Left behind for backwards compatibility. Remove once mGAP adjusts usages.
        public boolean isLdapEmail()
        {
            return isLdapOrSsoEmail();
        }

        public boolean isLdapOrSsoEmail()
        {
            return AuthenticationManager.isLdapOrSsoEmail(_email);
        }

        public String getVerification()
        {
            return _verification;
        }

        public void setVerification(String verification)
        {
            _verification = verification;
        }

        public User getUser()
        {
            return _user;
        }

        public void setUser(@NotNull User user)
        {
            _user = user;
        }

        public boolean getHasLogin()
        {
            return null != _verification;
        }
    }


    public static class UserManagementException extends Exception
    {
        private final String _email;

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
        public UserAlreadyExistsException(ValidEmail email)
        {
            this(email, "User already exists");
        }

        public UserAlreadyExistsException(ValidEmail email, String message)
        {
            super(email, message);
        }
    }

    /** @param currentUser the user who is adding the new user. Used to set createdBy on the new user record */
    public static NewUserStatus addUser(ValidEmail email, @Nullable User currentUser) throws UserManagementException
    {
        return addUser(email, currentUser, true);
    }

    /** @param currentUser the user who is adding the new user. Used to set createdBy on the new user record
     * @param createLogin false in the case of a new LDAP or SSO user authenticating for the first time, or true
     *                    in any other case (e.g., manually added LDAP user), so we need an additional check below
     *                    to avoid sending verification emails to an LDAP user.
     */
    public static @NotNull NewUserStatus addUser(ValidEmail email, @Nullable User currentUser, boolean createLogin) throws UserManagementException
    {
        NewUserStatus status = new NewUserStatus(email);

        if (UserManager.userExists(email))
            throw new UserAlreadyExistsException(email);

        User newUser;
        DbScope scope = core.getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            if (createLogin && !status.isLdapOrSsoEmail())
            {
                String verification = SecurityManager.createLogin(email);
                status.setVerification(verification);
            }

            try
            {
                Integer userId = null;

                // Add row to Principals
                Map<String, Object> fieldsIn = new HashMap<>();
                fieldsIn.put("Name", email.getEmailAddress());
                fieldsIn.put("Type", PrincipalType.USER.getTypeChar());

                try
                {
                    Map<String, Object> returnMap = Table.insert(currentUser, core.getTableInfoPrincipals(), fieldsIn);
                    userId = (Integer) returnMap.get("UserId");
                }
                catch (RuntimeSQLException e)
                {
                    if (!"23000".equals(e.getSQLState()))
                    {
                        _log.debug("createUser: Something failed user: " + email, e);
                        throw e;
                    }
                }

                // If insert didn't return an id it must already exist... select it
                if (null == userId)
                    userId = new SqlSelector(core.getSchema(),
                            "SELECT UserId FROM " + core.getTableInfoPrincipals() + " WHERE Name = ?",
                           email.getEmailAddress()).getObject(Integer.class);

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
                    Map<String, Object> m = new HashMap<>();
                    m.put("UserId", userId);
                    String displayName = displayNameFromEmail(email, userId);
                    m.put("DisplayName", displayName);
                    Table.insert(currentUser, core.getTableInfoUsersData(), m);
                }
                catch (RuntimeSQLException x)
                {
                    if (!"23000".equals(x.getSQLState()))
                    {
                        _log.debug("createUser: Something failed user: " + email, x);
                        throw x;
                    }
                }

                UserManager.clearUserList();

                newUser = UserManager.getUser(userId);
                UserManager.fireAddUser(newUser);
            }
            catch (RuntimeSQLException e)
            {
                throw new UserManagementException(email, "Unable to create user.", e);
            }

            if (null == newUser)
                throw new UserManagementException(email, "Couldn't create user.");

            transaction.commit();

            status.setUser(newUser);

            return status;
        }
    }

    private static String displayNameFromEmail(ValidEmail email, Integer userId)
    {
        String displayName;

        if (null != email.getPersonal())
            displayName = email.getPersonal();
        else if (email.getEmailAddress().indexOf("@") > 0)
            displayName = email.getEmailAddress().substring(0,email.getEmailAddress().indexOf("@"));
        else
            displayName = email.getEmailAddress();

        displayName = displayName.replace("."," ");
        displayName = displayName.replace("_"," ");
        displayName = displayName.replace("@"," ");
        displayName = StringUtils.trimToEmpty(displayName);

        // Issue 25813: if two users are being inserted at the same time through the addUser method above, we can get deadlock on SQLServer so we
        // actively lock when doing this select to prevent it from promoting a lock up the chain.
        SQLFragment select = new SQLFragment("SELECT UserId FROM " + core.getTableInfoUsersData());
        if (core.getSchema().getScope().getSqlDialect().isSqlServer())
            select.append(" WITH (UPDLOCK)");
        select.append(" WHERE DisplayName = ? AND UserId != ?");
        select.add(displayName);
        select.add(userId);
        Integer existingUserId = new SqlSelector(core.getSchema(), select).getObject(Integer.class);
        if (existingUserId != null)
        {
            displayName += userId;
        }
        return displayName;
    }

    public static void sendEmail(Container c, User user, SecurityMessage message, String to, ActionURL verificationURL) throws ConfigurationException, MessagingException
    {
        MimeMessage m = createMessage(c, user, message, to, verificationURL);
        MailHelper.send(m, user, c);
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
            // Issue 33254: only allow Site Admins to see the verification token
            if (message.isMaskToken() && !user.hasSiteAdminPermission())
                verificationURL.replaceParameter("verification", "**********");

            message.setVerificationURL(verificationURL.getURIString());
            message.setOriginatingUser(user);
            if (message.getTo() == null)
                message.setTo(to);

            MimeMessage m = message.createMailMessage(c);
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

    // Create record for non-LDAP login, saving email address and hashed password. Return verification token.
    public static String createLogin(ValidEmail email) throws UserManagementException
    {
        // Create a placeholder password hash and a separate email verification key that will get emailed to the new user
        String tempPassword = SecurityManager.createTempPassword();
        String verification = SecurityManager.createTempPassword();

        String crypt = Crypt.MD5.digestWithPrefix(tempPassword);

        try
        {
            // Don't need to set LastChanged -- it defaults to current date/time.
            int rowCount = new SqlExecutor(core.getSchema()).execute("INSERT INTO " + core.getTableInfoLogins() +
                    " (Email, Crypt, LastChanged, Verification, PreviousCrypts) VALUES (?, ?, ?, ?, ?)",
                    email.getEmailAddress(), crypt, new Date(), verification, crypt);
            if (1 != rowCount)
                throw new UserManagementException(email, "Login creation statement affected " + rowCount + " rows.");
        }
        catch (DataIntegrityViolationException e)
        {
            throw new UserAlreadyExistsException(email);
        }

        return verification;
    }

    public static void setPassword(ValidEmail email, String password) throws UserManagementException
    {
        String crypt = Crypt.BCrypt.digestWithPrefix(password);
        List<String> history = new ArrayList<>(getCryptHistory(email.getEmailAddress()));
        history.add(crypt);

        // Remember only the last 10 password hashes
        int itemsToDelete = Math.max(0, history.size() - MAX_HISTORY);
        for (int i = 0; i < itemsToDelete; i++)
            history.remove(i);
        String cryptHistory = StringUtils.join(history, ",");

        int rows = new SqlExecutor(core.getSchema()).execute("UPDATE " + core.getTableInfoLogins() + " SET Crypt=?, LastChanged=?, PreviousCrypts=? WHERE Email=?", crypt, new Date(), cryptHistory, email.getEmailAddress());
        if (1 != rows)
            throw new UserManagementException(email, "Password update statement affected " + rows + " rows.");
    }

    private static final int MAX_HISTORY = 10;

    private static List<String> getCryptHistory(String email)
    {
        Selector selector = new SqlSelector(core.getSchema(), new SQLFragment("SELECT PreviousCrypts FROM " + core.getTableInfoLogins() + " WHERE Email=?", email));
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
        List<String> history = getCryptHistory(user.getEmail());

        for (String hash : history)
        {
            if (SecurityManager.matchPassword(password, hash))
                return true;
        }

        return false;
    }

    public static Date getLastChanged(User user)
    {
        SqlSelector selector = new SqlSelector(core.getSchema(), new SQLFragment("SELECT LastChanged FROM " + core.getTableInfoLogins() + " WHERE Email=?", user.getEmail()));
        return selector.getObject(Date.class);
    }

    // Look up email in Logins table and return the corresponding password hash
    public static String getPasswordHash(ValidEmail email)
    {
        return getPasswordHash(email.getEmailAddress());
    }

    // Look up email in Logins table and return the corresponding password hash
    private static String getPasswordHash(String email)
    {
        SqlSelector selector = new SqlSelector(core.getSchema(), new SQLFragment("SELECT Crypt FROM " + core.getTableInfoLogins() + " WHERE Email = ?", email));
        return selector.getObject(String.class);
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

    // Used only in the case of email change... current email address might be invalid
    static boolean loginExists(String email)
    {
        return (null != getPasswordHash(email));
    }

    public static boolean loginExists(ValidEmail email)
    {
        return (null != getPasswordHash(email));
    }

    public static Group createGroup(Container c, String name)
    {
        return createGroup(c, name, PrincipalType.GROUP);
    }

    public static Group createGroup(Container c, String name, PrincipalType type)
    {
        // Consider: add validation rules to enum
        if (type != PrincipalType.GROUP && type != PrincipalType.MODULE)
            throw new IllegalStateException("Can't create a group with type \"" + type + "\"");

        String ownerId;

        if (null == c || c.isRoot())
        {
            ownerId = null;
        }
        else
        {
            ownerId = c.getId();

            // Module groups can be associated with any folder; security groups must be associated with a project
            if (type != PrincipalType.MODULE && !c.isProject())
                throw new IllegalStateException("Security groups can only be associated with a project or the root");
        }

        return createGroup(c, name, type, ownerId);
    }

    public static Group createGroup(Container c, String name, PrincipalType type, String ownerId)
    {
        String containerId = (null == c || c.isRoot()) ? null : c.getId();
        Group group = new Group(type);
        group.setName(StringUtils.trimToNull(name));
        group.setOwnerId(ownerId);
        group.setContainer(containerId);

        if (null == group.getName())
            throw new IllegalArgumentException("Group can not have blank name");

        String valid = UserManager.validGroupName(group.getName(), group.getPrincipalType());
        if (null != valid)
            throw new IllegalArgumentException(valid);

        if (groupExists(c, group.getName(), group.getOwnerId()))
            throw new IllegalArgumentException("Group '" + group.getName() + "' already exists");

        Table.insert(null, core.getTableInfoPrincipals(), group);
        ProjectAndSiteGroupsCache.uncache(c);

        return group;
    }

    // Case-insensitive existence check -- disallows groups that differ only by case
    private static boolean groupExists(Container c, String groupName, String ownerId)
    {
        return null != getGroupId(c, groupName, ownerId, false, true);
    }

    public static Group renameGroup(Group group, String newName, User currentUser)
    {
        if (group.isSystemGroup())
            throw new IllegalArgumentException("System groups may not be renamed!");
        Container c = null == group.getContainer() ? null : ContainerManager.getForId(group.getContainer());
        if (StringUtils.isEmpty(newName))
            throw new IllegalArgumentException("Name is required (may not be blank)");
        String valid = UserManager.validGroupName(newName, group.getPrincipalType());
        if (null != valid)
            throw new IllegalArgumentException(valid);
        if (null != getGroupId(c, newName, false))
            throw new IllegalArgumentException("Cannot rename group '" + group.getName() + "' to '" + newName + "' because that name is already used by another group!");

        Table.update(currentUser, core.getTableInfoPrincipals(), Collections.singletonMap("name", newName), group.getUserId());
        GroupCache.uncache(group.getUserId());

        return getGroup(getGroupId(c, newName));
    }

    public static void deleteGroup(Group group)
    {
        deleteGroup(group.getUserId());
    }

    static void deleteGroup(int groupId)
    {
        if (groupId == Group.groupAdministrators ||
                groupId == Group.groupGuests ||
                groupId == Group.groupUsers ||
                groupId == Group.groupDevelopers)
            throw new IllegalArgumentException("The global groups cannot be deleted.");

        Group group = getGroup(groupId);

        // Need to invalidate all computed group lists. This isn't quite right, but it gets the job done.
        GroupMembershipCache.handleGroupChange(group, group);

        // NOTE: Most code can not tell the difference between a non-existent SecurityPolicy and an empty SecurityPolicy
        // NOTE: Both are treated as meaning "inherit", we don't want to accidentally create an empty security policy
        // TODO: create an explicit inherit bit on policy (or distinguish undefined/empty)

        SQLFragment sqlf = new SQLFragment("SELECT DISTINCT ResourceId FROM " + core.getTableInfoRoleAssignments() + " WHERE UserId = ?",groupId);
        List<String> resources = new SqlSelector(core.getSchema().getScope(), sqlf).getArrayList(String.class);

        Table.delete(core.getTableInfoRoleAssignments(), new SimpleFilter(FieldKey.fromParts("UserId"), groupId));

        // make sure we didn't empty out any policies completely
        // NOTE: we can almost do this with SecurityPolicy objects, but we don't have any SecurableResource objects
        // NOTE: handy, so we'd have to rework the API/caching a bit

        FieldKey resourceId = new FieldKey(null,"resourceid");
        for (String id : resources)
        {
            SimpleFilter f = new SimpleFilter(resourceId, id);
            if (!new TableSelector(core.getTableInfoRoleAssignments(),f,null).exists())
            {
                SQLFragment insert = new SQLFragment("INSERT INTO " + core.getTableInfoRoleAssignments() + " (resourceid,userid,role) VALUES (?,-3,'org.labkey.api.security.roles.NoPermissionsRole')",id);
                new SqlExecutor(core.getSchema()).execute(insert);
            }
        }

        Filter groupFilter = new SimpleFilter(FieldKey.fromParts("GroupId"), groupId);
        Table.delete(core.getTableInfoMembers(), groupFilter);

        Filter principalsFilter = new SimpleFilter(FieldKey.fromParts("UserId"), groupId);
        Table.delete(core.getTableInfoPrincipals(), principalsFilter);

        GroupCache.uncache(groupId);
        Container c = ContainerManager.getForId(group.getContainer());
        ProjectAndSiteGroupsCache.uncache(c);
        // 20329 SecurityPolicy cache still has the role assignments for deleted groups.
        SecurityPolicyManager.notifyPolicyChanges(resources);
    }

    public static void deleteGroups(Container c, @Nullable PrincipalType type)
    {
        if (!(null == type || type == PrincipalType.GROUP || type == PrincipalType.MODULE))
            throw new IllegalArgumentException("Illegal group type: " + type);

        String typeString = (null == type ? "%" : String.valueOf(type.getTypeChar()));

        SqlExecutor executor = new SqlExecutor(core.getSchema());
        executor.execute("DELETE FROM " + core.getTableInfoRoleAssignments() + "\n"+
                "WHERE UserId in (SELECT UserId FROM " + core.getTableInfoPrincipals() +
                "\tWHERE Container=? and Type LIKE ?)", c, typeString);
        executor.execute("DELETE FROM " + core.getTableInfoMembers() + "\n"+
                "WHERE GroupId in (SELECT UserId FROM " + core.getTableInfoPrincipals() +
                "\tWHERE Container=? and Type LIKE ?)", c, typeString);
        executor.execute("DELETE FROM " + core.getTableInfoPrincipals() +
                "\tWHERE Container=? AND Type LIKE ?", c, typeString);

        // Consider: query for groups in this container and uncache just those.
        GroupCache.uncacheAll();
        ProjectAndSiteGroupsCache.uncache(c);
    }


    public static void deleteMembers(Group group, List<UserPrincipal> membersToDelete)
    {
        int groupId = group.getUserId();

        if (membersToDelete != null && !membersToDelete.isEmpty())
        {
            SQLFragment sql = new SQLFragment(
            "DELETE FROM " + core.getTableInfoMembers() + "\n" +
                    "WHERE GroupId = ? AND UserId ");
            sql.add(groupId);
            List<Integer> userIds = new ArrayList<>(membersToDelete.size());
            for (UserPrincipal userPrincipal : membersToDelete)
            {
                userIds.add(userPrincipal.getUserId());
            }
            core.getSqlDialect().appendInClauseSql(sql, userIds);

            new SqlExecutor(core.getSchema()).execute(sql);

            for (UserPrincipal member : membersToDelete)
                fireDeletePrincipalFromGroup(groupId, member);
        }
    }

    public static void deleteMember(Group group, UserPrincipal principal)
    {
        int groupId = group.getUserId();
        new SqlExecutor(core.getSchema()).execute("DELETE FROM " + core.getTableInfoMembers() + "\n" +
            "WHERE GroupId = ? AND UserId = ?", groupId, principal.getUserId());
        fireDeletePrincipalFromGroup(groupId, principal);
    }

    // Returns a list of errors
    public static List<String> addMembers(Group group, Collection<? extends UserPrincipal> principals)
    {
        List<String> errors = new LinkedList<>();

        for (UserPrincipal principal : principals)
        {
            try
            {
                addMember(group, principal);
            }
            catch (InvalidGroupMembershipException e)
            {
                errors.add(principal.getName() + ": " + e.getMessage());
            }
        }

        return errors;
    }

    // Add a single user/group to a single group
    public static void addMember(Group group, UserPrincipal principal) throws InvalidGroupMembershipException
    {
        addMember(group, principal, null);
    }

    // Internal only; used by junit test
    static void addMember(Group group, UserPrincipal principal, @Nullable Runnable afterAddRunnable) throws InvalidGroupMembershipException
    {
        String errorMessage = getAddMemberError(group, principal);

        if (null != errorMessage)
            throw new InvalidGroupMembershipException(errorMessage);

        addMemberWithoutValidation(group, principal);

        if (null != afterAddRunnable)
            afterAddRunnable.run();

        // If we added a group then check for circular relationship... we check this above, but it's possible that
        // another thread concurrently made a change that introduced a cycle. If so, revert the add.
        if (principal instanceof Group && hasCycle(group))
        {
            deleteMember(group, principal);
            throw new InvalidGroupMembershipException(CIRCULAR_GROUP_ERROR_MESSAGE);
        }
    }

    // Internal only; used by junit test
    static void addMemberWithoutValidation(Group group, UserPrincipal principal)
    {
        SqlExecutor executor = new SqlExecutor(core.getSchema()).setLogLevel(Level.ERROR);   // Don't log warnings (e.g., constraint violations)

        try
        {
            executor.execute("INSERT INTO " + core.getTableInfoMembers() + " (UserId, GroupId) VALUES (?, ?)", principal.getUserId(), group.getUserId());
        }
        catch (DataIntegrityViolationException e)
        {
            // Assume this is a race condition and ignore, see #14795
            _log.warn("Member could not be added: " + e.getMessage());
        }

        fireAddPrincipalToGroup(group, principal);
    }

    // True if current group relationships cycle through root. Does NOT detect all cycles in the group graph nor even
    // in the subgraph represented by root, just the ones that that link back to root itself.
    private static boolean hasCycle(Group root)
    {
        HashSet<Integer> groupSet = new HashSet<>();
        LinkedList<Integer> recurse = new LinkedList<>();
        recurse.add(root.getUserId());

        while (!recurse.isEmpty())
        {
            int id = recurse.removeFirst();
            groupSet.add(id);
            int[] groups = GroupMembershipCache.getGroupMemberships(id);

            for (int g : groups)
            {
                if (g == root.getUserId())
                    return true;

                if (!groupSet.contains(g))
                    recurse.addLast(g);
            }
        }

        return false;
    }

    // TODO: getAddMemberError() now uses the cache (see #14383), but this is still an n^2 algorithm. Better would be for this
    // method to validate an entire collection of candidates at once, and switch getAddMemberError() to call getValidPrincipals()
    // with a singleton.
    public static <K extends UserPrincipal> Collection<K> getValidPrincipals(Group group, Collection<K> candidates)
    {
        Collection<K> valid = new LinkedList<>();

        // don't suggest groups that will result in errors (i.e. circular relation, already member, etc.)
        for (K candidate : candidates)
        {
            if (null == SecurityManager.getAddMemberError(group, candidate))
                valid.add(candidate);
        }

        return valid;
    }

    // Return an error message if principal can't be added to the group, otherwise return null
    public static String getAddMemberError(Group group, UserPrincipal principal)
    {
        if (null == group)
            return NULL_GROUP_ERROR_MESSAGE;

        if (null == principal)
            return NULL_PRINCIPAL_ERROR_MESSAGE;

        if (group.isGuests() || group.isUsers())
            return "Can't add a member to the " + group.getName() + " group";

        Set<UserPrincipal> members = getGroupMembers(group, MemberType.ALL_GROUPS_AND_USERS);

        if (members.contains(principal))
            return ALREADY_A_MEMBER_ERROR_MESSAGE;

        // End of checks for a user
        if (principal instanceof User)
            return null;

        // We're adding a group, so do some additional validation checks

        Group newMember = (Group)principal;

        if (group.equals(newMember))
            return ADD_GROUP_TO_ITSELF_ERROR_MESSAGE;

        if (group.isSystemGroup())
            return ADD_TO_SYSTEM_GROUP_ERROR_MESSAGE;

        if (newMember.isSystemGroup())
            return ADD_SYSTEM_GROUP_ERROR_MESSAGE;

        if (group.isProjectGroup())
        {
            if (newMember.isProjectGroup() && !group.getContainer().equals(newMember.getContainer()))
                return DIFFERENT_PROJECTS_ERROR_MESSAGE;
        }
        else
        {
            if (newMember.isProjectGroup())
                return PROJECT_TO_SITE_ERROR_MESSAGE;
        }

        for (int id : group.getGroups())
            if (newMember.getUserId() == id)
                return CIRCULAR_GROUP_ERROR_MESSAGE;

        return null;
    }

    // Site groups are first (if included) followed by project groups. Each list is sorted by name (case-insensitive).
    public static @NotNull List<Group> getGroups(@Nullable Container project, boolean includeGlobalGroups)
    {
        if (null != project)
            return ProjectAndSiteGroupsCache.getProjectGroups(project, includeGlobalGroups);
        else
            return ProjectAndSiteGroupsCache.getSiteGroups();
    }

    @Nullable
    public static Group getGroup(int groupId)
    {
        return GroupCache.get(groupId);
    }

    @Nullable
    public static UserPrincipal getPrincipal(int id)
    {
        UserPrincipal principal = UserManager.getUser(id);
        return null != principal ? principal : getGroup(id);
    }

    /** This will preferentially return project users/groups. If no principal is found at the project level and includeSiteGroups=true, it will check site groups */
    @Nullable
    public static UserPrincipal getPrincipal(String name, Container container, boolean includeSiteGroups)
    {
        UserPrincipal up = getPrincipal(name, container);
        if (up != null || !includeSiteGroups)
        {
            return up;
        }

        return getPrincipal(name, null);
    }

    public static UserPrincipal getPrincipal(String name, Container container)
    {
        Integer id = getGroupId(container, name, false);

        if (null != id)
        {
            return getGroup(id);
        }
        else
        {
            try
            {
                return UserManager.getUser(new ValidEmail(name));
            }
            catch(InvalidEmailException e)
            {
                return null;
            }
        }
    }

    public static @NotNull List<User> getProjectUsers(Container c)
    {
        return getProjectUsers(c, false);
    }

    /**
     * Returns a list of Group objects to which the user belongs in the specified container.
     * @param c The container
     * @param u The user
     * @return The list of groups that u belong to in container c
     */
    public static List<Group> getGroups(@NotNull Container c, User u)
    {
        Container proj = c.getProject();
        if (null == proj)
            proj = c;
        int[] groupIds = u.getGroups();
        List<Group> groupList = new ArrayList<>();

        for (int groupId : groupIds)
        {
            //ignore user as group
            if (groupId != u.getUserId())
            {
                Group g = SecurityManager.getGroup(groupId);

                // Only global groups or groups in this project
                if (null != g && (null == g.getContainer() || g.getContainer().equals(proj.getId())))
                    groupList.add(g);
            }
        }

        return groupList;
    }

    // Returns comma-separated list of group names this user belongs to in this container
    public static HtmlString getGroupList(Container c, User u)
    {
        Container proj = c.getProject();

        if (null == proj || u == null)
            return HtmlString.EMPTY_STRING;

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

                // Only site groups or groups in this project (issue 12026)
                if (!g.isProjectGroup() || g.getContainer().equals(proj.getId()))
                {
                    groupList.append(sep);
                    groupList.append(g.getName());
                    sep = ", ";
                }
            }
        }

        return HtmlString.of(groupList.toString());
    }

    /** Returns the requested direct members of this group (non-recursive) */
    public static @NotNull <P extends UserPrincipal> Set<P> getGroupMembers(Group group, MemberType<P> memberType)
    {
        Set<P> principals = new LinkedHashSet<>();
        int[] ids = GroupMembershipCache.getGroupMembers(group);
        addMembers(principals, ids, memberType);

        return principals;
    }

    /** Returns the members of this group dictated by memberType, including those in subgroups (recursive) */
    public static @NotNull <P extends UserPrincipal> Set<P> getAllGroupMembers(Group group, MemberType<P> memberType)
    {
        return getAllGroupMembers(group, memberType, false);
    }

    /** Returns the members of this group dictated by memberType, including those in subgroups (recursive). If group is all site users
     * and returnSiteUsers is false, return empty set */
    public static @NotNull <P extends UserPrincipal> Set<P> getAllGroupMembers(Group group, MemberType<P> memberType, boolean returnSiteUsers)
    {
        Set<Group> visitedGroups = new HashSet<>();
        Set<P> members = new LinkedHashSet<>();
        LinkedList<Group> pendingGroups = new LinkedList<>();
        pendingGroups.add(group);

        while (!pendingGroups.isEmpty())
        {
            Group next = pendingGroups.removeFirst();

            // In nested groups, a group could be present multiple times at different levels, so track visited
            // groups and skip some work if we've already seen this group.
            if (visitedGroups.add(next))
            {
                // 19532 Site user group membership is not explicitly defined in the core.Members table, so that
                // group always returned empty set. Allow a special case of returning real set if explicitly requested.
                if (next.isUsers() && returnSiteUsers)
                {
                    List<Integer> userIds = UserManager.getUserIds();
                    int[] ids = new int[userIds.size()];
                    for (int i = 0; i < userIds.size(); i++)
                    {
                        ids[i] = userIds.get(i);
                    }
                    addMembers(members, ids, memberType);
                }
                else
                {
                    int[] ids = GroupMembershipCache.getGroupMembers(next);
                    // Consider: optimize with a single loop
                    addMembers(members, ids, memberType);
                    addMembers(pendingGroups, ids, MemberType.GROUPS);
                }
            }
        }

        return members;
    }

    private static <P extends UserPrincipal> void addMembers(Collection<P> principals, int[] ids, MemberType<P> memberType)
    {
        for (int id : ids)
        {
            P principal = memberType.getPrincipal(id);
            if (null != principal)
                principals.add(principal);
        }
    }

    // get the list of group members that do not need to be direct members because they are a member of a member group (i.e. groups-in-groups)
    public static Map<UserPrincipal, List<UserPrincipal>> getRedundantGroupMembers(Group group)
    {
        Map<UserPrincipal, List<UserPrincipal>> redundantMembers = new HashMap<>();
        Set<UserPrincipal> origMembers = getGroupMembers(group, MemberType.ALL_GROUPS_AND_USERS);
        LinkedList<UserPrincipal> visited = new LinkedList<>();
        for (UserPrincipal memberGroup : getGroupMembers(group, MemberType.GROUPS))
        {
            visited.addLast(memberGroup);
            checkForRedundantMembers((Group)memberGroup, origMembers, redundantMembers, visited);
            visited.removeLast();
        }
        return redundantMembers;
    }

    private static void checkForRedundantMembers(Group group, Set<UserPrincipal> origMembers, Map<UserPrincipal, List<UserPrincipal>> redundantMembers, LinkedList<UserPrincipal> visited)
    {
        Set<UserPrincipal> members = getGroupMembers(group, MemberType.ALL_GROUPS_AND_USERS);

        for (UserPrincipal principal : members)
        {
            visited.addLast(principal);
            if (origMembers.contains(principal))
            {
                if (!redundantMembers.containsKey(principal))
                    redundantMembers.put(principal, new LinkedList<>(visited));
            }
            else if (principal instanceof Group)
            {
                checkForRedundantMembers((Group)principal, origMembers, redundantMembers, visited);
            }
            visited.removeLast();
        }
    }

    /**
     * With groups-in-groups, a user/group can have multiple pathways of membership to another group
     * (i.e. a is a member of b which is a member of d, and a is a member of c which is a member of d)
     * store each membership pathway as a list of groups and return the set of memberships
     * @param principal The user/group to check membership
     * @param group The root group to find membership pathways for the provided principal
     * @return Set of membership pathways (each as a list of groups)
     */
    public static Set<List<UserPrincipal>> getMembershipPathways(UserPrincipal principal, Group group)
    {
        Set<List<UserPrincipal>> memberships = new HashSet<>();
        if (principal.isInGroup(group.getUserId()))
        {
            LinkedList<UserPrincipal> visited = new LinkedList<>();
            visited.add(group);
            checkForMembership(principal, group, visited, memberships);
        }

        return memberships;
    }

    /**
     * Recursive function to check for membership pathways between two principals
     * @param principal User/Group to check if it has a pathway to the given group
     * @param group Group to check if the user/group is a member
     * @param visited List of groups/users for the given pathway
     * @param memberships The set of membership pathways between the original principals 
     */
    private static void checkForMembership(UserPrincipal principal, Group group, LinkedList<UserPrincipal> visited, Set<List<UserPrincipal>> memberships)
    {
        for (UserPrincipal member : SecurityManager.getGroupMembers(group, MemberType.ALL_GROUPS_AND_USERS))
        {
            if (visited.contains(member))
                continue;

            if (member.equals(principal))
            {
                visited.addLast(member);
                memberships.add(new LinkedList<>(visited));
                visited.removeLast();
                break;
            }
        }

        for (UserPrincipal member : SecurityManager.getGroupMembers(group, MemberType.GROUPS))
        {
            if (visited.contains(member) || member.equals(principal))
                continue;

            visited.addLast(member);
            checkForMembership(principal, (Group)member, visited, memberships);
            visited.removeLast();
        }
    }

    /**
     * Used to get the display HTML for hovering over a group in the Admin reports
     * Example:
     * Joe is a member of Group A
     *   Which is a member of Group B
     *     Which is a member of Group C
     *       Which is assigned the Reader role
     * @param paths The set of membership paths between a user and the given group
     * @param userDisplay The string to display in the user portion of the display (likely email address or display name)
     * @param role The name of the role the group has in the container for the admin report
     * @return String HTML for the hover display
     */
    public static String getMembershipPathwayHTMLDisplay(Set<List<UserPrincipal>> paths, String userDisplay, String role)
    {
        StringBuilder sb = new StringBuilder();
        for (List<UserPrincipal> path : paths)
        {
            // add an extra line if there are > 1 paths displayed
            if (sb.length() > 0)
                sb.append("<BR/>");

            StringBuilder spacer = new StringBuilder();
            for (int i = path.size()-1; i > 0 ; i--)
            {
                String beginTxt = (i == path.size()-1 ? PageFlowUtil.filter(userDisplay) : "Which");
                sb.append(spacer.toString()).append(beginTxt).append(" is a member of ").append("<strong>").append(PageFlowUtil.filter(path.get(i-1).getName())).append("</strong>");
                sb.append("<BR/>");
                spacer.append("&nbsp;&nbsp;&nbsp;");
            }
            sb.append(spacer).append("Which is assigned the ").append(PageFlowUtil.filter(role)).append(" role");
            sb.append("<BR/>");
        }
        return sb.toString();
    }    

    // TODO: Redundant with getProjectUsers() -- this approach should be more efficient for simple cases
    // TODO: Also redundant with getFolderUserids()
    // TODO: Cache this set
    public static Set<Integer> getProjectUsersIds(Container c)
    {
        SQLFragment sql = SecurityManager.getProjectUsersSQL(c.getProject());
        sql.insert(0, "SELECT DISTINCT members.UserId ");

        Selector selector = new SqlSelector(core.getSchema(), sql);
        return new HashSet<>(selector.getCollection(Integer.class));
    }

    // True fragment -- need to prepend SELECT DISTINCT() or IN () for this to be valid SQL
    public static SQLFragment getProjectUsersSQL(Container c)
    {
        return new SQLFragment("FROM " + core.getTableInfoMembers() + " members INNER JOIN " + core.getTableInfoUsers() + " users ON members.UserId = users.UserId\n" +
                                    "INNER JOIN " + core.getTableInfoPrincipals() + " groups ON members.GroupId = groups.UserId\n" +
                                    "WHERE (groups.Container = ?)", c);
    }

    // TODO: Should return a set
    // TODO: Why is the includeGlobal flag necessary?
    public static @NotNull List<User> getProjectUsers(Container c, boolean includeGlobal)
    {
       return getProjectUsers(c, includeGlobal, true);
    }

    public static @NotNull List<User> getProjectUsers(Container c, boolean includeGlobal, boolean includeDeactivated)
    {
        if (c != null && !c.isProject())
            c = c.getProject();

        List<Group> groups = getGroups(c, includeGlobal);
        Set<String> emails = new HashSet<>();

        //get members for each group
        ArrayList<User> projectUsers = new ArrayList<>();
        Set<User> members;

        for (Group g : groups)
        {
            if (g.isGuests() || g.isUsers())
                continue;

            // TODO: currently only getting members that are users (no groups). should this be changed to get users of member groups?
            members = getGroupMembers(g, includeDeactivated ? MemberType.ACTIVE_AND_INACTIVE_USERS : MemberType.ACTIVE_USERS);

            //add this group's members to hashset
            if (!members.isEmpty())
            {
                //get list of users from email
                for (UserPrincipal member : members)
                {
                    User user = UserManager.getUser(member.getUserId());
                    if (null != user && emails.add(user.getEmail()))
                        projectUsers.add(user);
                }
            }
        }

        return projectUsers;
    }

    public static Collection<Integer> getFolderUserids(Container c)
    {
        Container project = (c.isProject() || c.isRoot()) ? c : c.getProject();
        SecurityPolicy policy = c.getPolicy();

        //don't filter if all site users is playing a role
        Group allSiteUsers = SecurityManager.getGroup(Group.groupUsers);
        if (policy.getAssignedRoles(allSiteUsers).size() != 0)
        {
            // Just select all users
            SQLFragment sql = new SQLFragment("SELECT u.UserId FROM ");
            sql.append(core.getTableInfoPrincipals(), "u");
            sql.append(" WHERE u.type='u'");

            return new SqlSelector(core.getSchema(), sql).getCollection(Integer.class);
        }

        //users "in the project" consists of:
        // - users who are members of a project group
        // - users who belong to a site group that has a role assignment in the policy for the specified folder
        // - users who have a direct role assignment in the policy for the specified folder

        Set<Integer> userIds = new HashSet<>();

        // Add all project groups
        Set<Group> groupsToExpand = new HashSet<>(getGroups(project, false));

        // Look for users and site groups that have direct assignment to the container
        for (RoleAssignment roleAssignment : c.getPolicy().getAssignments())
        {
            User user = UserManager.getUser(roleAssignment.getUserId());
            if (user != null)
            {
                userIds.add(user.getUserId());
            }
            else
            {
                Group assignedGroup = getGroup(roleAssignment.getUserId());
                if (assignedGroup != null && !assignedGroup.isProjectGroup())
                {
                    // Add all site groups
                    groupsToExpand.add(assignedGroup);
                }
            }
        }

        // Find the users who are members of all the relevant site groups
        for (Group group : groupsToExpand)
        {
            Set<User> groupMembers = getAllGroupMembers(group, MemberType.ACTIVE_AND_INACTIVE_USERS);
            for (User groupMember : groupMembers)
            {
                userIds.add(groupMember.getUserId());
            }
        }

        return userIds;
    }

    public static List<User> getUsersWithPermissions(Container c, Set<Class<? extends Permission>> perms)
    {
        // No cache right now, but performance seems fine. After the user list and policy are cached, no other queries occur.
        Collection<User> allUsers = UserManager.getActiveUsers();
        List<User> users = new ArrayList<>(allUsers.size());
        SecurityPolicy policy = c.getPolicy();

        for (User user : allUsers)
            if (SecurityManager.hasAllPermissions(null, policy, user, perms, Set.of()))
                users.add(user);

        return users;
    }

    public static List<User> getUsersWithOneOf(Container c, Set<Class<? extends Permission>> perms)
    {
        Collection<User> allUsers = UserManager.getActiveUsers();
        List<User> users = new ArrayList<>(allUsers.size());
        SecurityPolicy policy = c.getPolicy();

        for (User user : allUsers)
            if (SecurityManager.hasAnyPermissions(null, policy, user, perms, Set.of()))
                users.add(user);

        return users;
    }

    /** Returns both users and groups, but direct members only (not recursive) */
    public static List<Pair<Integer, String>> getGroupMemberNamesAndIds(String path)
    {
        return getGroupMemberNamesAndIds(path, false);
    }

    /** Returns both users and groups, but direct members only (not recursive) */
    public static List<Pair<Integer, String>> getGroupMemberNamesAndIds(String path, boolean includeInactive)
    {
        Integer groupId = SecurityManager.getGroupId(path);
        if (groupId == null)
            return Collections.emptyList();
        else
            return getGroupMemberNamesAndIds(groupId, includeInactive);
    }
    
    /** Returns both users and groups, but direct members only (not recursive) */
    public static List<Pair<Integer, String>> getGroupMemberNamesAndIds(Integer groupId, boolean includeInactive)
    {
        final List<Pair<Integer, String>> members = new ArrayList<>();

        if (groupId != null && groupId == Group.groupUsers)
        {
            // Special-case site users group, which isn't maintained in the database
            for (User user : UserManager.getActiveUsers())
            {
                members.add(new Pair<>(user.getUserId(), user.getEmail()));
            }
        }
        else
        {
            String sql = "SELECT Users.UserId, Users.Name\n" +
                    "FROM " + core.getTableInfoMembers() + " JOIN " + core.getTableInfoPrincipals() + " Users ON " + core.getTableInfoMembers() + ".UserId = Users.UserId\n" +
                    "WHERE GroupId = ?";
            SQLFragment sqlFragment;
            if (includeInactive)
            {
                sql += "\nORDER BY Users.Name";
                sqlFragment = new SQLFragment(sql, groupId);
            }
            else
            {
                sql += " AND Active=?" + "\nORDER BY Users.Name";
                sqlFragment = new SQLFragment(sql, groupId, true);
            }
            new SqlSelector(core.getSchema(), sqlFragment).forEach(rs -> members.add(new Pair<>(rs.getInt(1), rs.getString(2))));
        }

        return members;
    }

    /** Returns both users and groups, but direct members only (not recursive) */
    public static String[] getGroupMemberNames(Integer groupId)
    {
        List<Pair<Integer, String>> members = getGroupMemberNamesAndIds(groupId, false);
        String[] names = new String[members.size()];
        int i = 0;
        for (Pair<Integer, String> member : members)
            names[i++] = member.getValue();
        return names;
    }

    /** Takes string such as "/test/subfolder/Users" and returns groupId */
    public static Integer getGroupId(String extraPath)
    {
        if (null == extraPath)
            return null;
        
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
            {
                throw new NotFoundException();
            }
        }

        return getGroupId(c, group);
    }

    /** Takes Container (or null for root) and group name; returns groupId */
    public static Integer getGroupId(@Nullable Container c, String group)
    {
        return getGroupId(c, group, null, true);
    }

    /** Takes Container (or null for root) and group name; returns groupId */
    public static Integer getGroupId(@Nullable Container c, String group, boolean throwOnFailure)
    {
        return getGroupId(c, group, null, throwOnFailure);
    }

    public static Integer getGroupId(@Nullable Container c, String groupName, @Nullable String ownerId, boolean throwOnFailure)
    {
        return getGroupId(c, groupName, ownerId, throwOnFailure, false);
    }

    // This is temporary... in CPAS 1.5 on PostgreSQL it was possible to create two groups in the same container that differed only
    // by case (this was not possible on SQL Server). In CPAS 1.6 we disallow this on PostgreSQL... but we still need to be able to
    // retrieve group IDs in a case-sensitive manner.
    // TODO: For CPAS 1.7: this should always be case-insensitive (we will clean up the database by renaming duplicate groups)
    private static Integer getGroupId(@Nullable Container c, String groupName, @Nullable String ownerId, boolean throwOnFailure, boolean caseInsensitive)
    {
        SQLFragment sql = new SQLFragment("SELECT UserId FROM " + core.getTableInfoPrincipals() + " WHERE Type!='u' AND " + (caseInsensitive ? "LOWER(Name)" : "Name") + " = ? AND Container ");
        sql.add(caseInsensitive ? groupName.toLowerCase() : groupName);

        if (c == null || c.isRoot())
        {
            sql.append("IS NULL");
        }
        else
        {
            sql.append("= ?");
            sql.add(c.getId());
            if (ownerId == null)
                ownerId = c.isRoot() ? null : c.getId();
        }

        if (ownerId == null)
        {
            sql.append(" AND OwnerId IS NULL");
        }
        else
        {
            sql.append(" AND OwnerId = ?");
            sql.add(ownerId);
        }

        Integer groupId = new SqlSelector(core.getSchema(), sql).getObject(Integer.class);

        if (groupId == null && throwOnFailure)
        {
            throw new NotFoundException("Group not found: " + groupName);
        }

        return groupId;
    }

    public interface ViewFactory
    {
        @Nullable HttpView<?> createView(ViewContext context);
    }

    // Modules register a factory to add module-specific ui to the permissions page
    public static void addViewFactory(ViewFactory vf)
    {
        VIEW_FACTORIES.add(vf);
    }

    public static List<ViewFactory> getViewFactories()
    {
        return VIEW_FACTORIES;
    }

    public interface TermsOfUseProvider
    {
        /**
         * If a provider's terms are active and the user hasn't yet agreed to them then
         * the provider redirects to its terms action by throwing a RedirectException.
         * @param context the current ViewContext
         * @param isBasicAuth is this a Basic authentication scenario?
         * @throws RedirectException if provider determines that terms still need to be accepted
         */
        void verifyTermsOfUse(ViewContext context, boolean isBasicAuth) throws RedirectException;
    }

    // Modules register a factory to add module-specific ui to the permissions page
    public static void addTermsOfUseProvider(TermsOfUseProvider provider)
    {
        TERMS_OF_USE_PROVIDERS.add(provider);
    }

    public static List<TermsOfUseProvider> getTermsOfUseProviders()
    {
        return TERMS_OF_USE_PROVIDERS;
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

        List<ValidEmail> emails = new ArrayList<>(rawEmails.size());

        for (String rawEmail : rawEmails)
        {
            if (null == (rawEmail=StringUtils.trimToNull(rawEmail)))
                continue;
            try
            {
                emails.add(new ValidEmail(rawEmail));
            }
            catch(InvalidEmailException e)
            {
                invalidEmails.add(rawEmail);
            }
        }

        return emails;
    }

    public static SecurityMessage getRegistrationMessage(String mailPrefix, boolean isAdminCopy)
    {
        SecurityMessage sm = new SecurityMessage(isAdminCopy);
        Class<? extends RegistrationEmailTemplate> templateClass = isAdminCopy ? RegistrationAdminEmailTemplate.class : RegistrationEmailTemplate.class;
        EmailTemplate et = EmailTemplateService.get().getEmailTemplate(templateClass);
        sm.setMessagePrefix(mailPrefix);
        sm.setEmailTemplate((SecurityEmailTemplate)et);
        sm.setType("User Registration Email");

        return sm;
    }

    public static SecurityMessage getResetMessage(boolean isAdminCopy)
    {
        return getResetMessage(isAdminCopy, null, null);
    }

    public static SecurityMessage getResetMessage(boolean isAdminCopy, User user, String provider)
    {
        if (provider != null)
        {
            ResetPasswordProvider resetProvider = AuthenticationManager.getResetPasswordProvider(provider);
            if (resetProvider != null)
            {
                SecurityMessage sm = resetProvider.getAPIResetPasswordMessage(user, isAdminCopy);
                if (sm != null)
                    return sm;
            }
        }
        return getDefaultResetMessage(isAdminCopy);
    }

    private static SecurityMessage getDefaultResetMessage(boolean isAdminCopy)
    {
        SecurityMessage sm = new SecurityMessage(isAdminCopy);
        Class<? extends PasswordResetEmailTemplate> templateClass = isAdminCopy ? PasswordResetAdminEmailTemplate.class : PasswordResetEmailTemplate.class;
        EmailTemplate et = EmailTemplateService.get().getEmailTemplate(templateClass);
        sm.setEmailTemplate((SecurityEmailTemplate)et);
        sm.setType("Reset Password Email");
        return sm;
    }

    public static HtmlString addUser(ViewContext context, ValidEmail email, boolean sendMail, String mailPrefix) throws Exception
    {
        return addUser(context, email, sendMail, mailPrefix, null, null, true);
    }

    /**
     * @return null if the user already exists, or a message indicating success/failure
     */
    public static HtmlString addUser(ViewContext context, ValidEmail email, boolean sendMail, String mailPrefix, @Nullable List<Pair<String, String>> extraParameters, String provider, boolean isAddUser) throws Exception
    {
        if (UserManager.userExists(email))
        {
            return null;
        }

        HtmlStringBuilder message = HtmlStringBuilder.of();
        NewUserStatus newUserStatus;

        ActionURL messageContentsURL = null;
        User currentUser = context.getUser();

        try
        {
            newUserStatus = SecurityManager.addUser(email, currentUser);

            if (newUserStatus.getHasLogin() && sendMail)
            {
                Container c = context.getContainer();
                messageContentsURL = PageFlowUtil.urlProvider(SecurityUrls.class).getShowRegistrationEmailURL(c, email, mailPrefix);

                sendRegistrationEmail(context, email, mailPrefix, newUserStatus, extraParameters, provider, isAddUser);
            }

            User newUser = newUserStatus.getUser();

            if (newUserStatus.isLdapOrSsoEmail())
            {
                message.append(newUser.getEmail()).append(" added as a new user to the system and NOT emailed since this user will be authenticated via LDAP or SSO.");
                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system NOT emailed since this user will be authenticated via LDAP or SSO.");
            }
            else if (sendMail)
            {
                message.append(email.getEmailAddress()).append(" added as a new user to the system and emailed successfully.");
                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system. Verification email was sent successfully.");
            }
            else
            {
                message.append(email.getEmailAddress()).append(" added as a new user to the system, but no email was sent.");

                // Issue 33254: only allow Site Admins to see the verification URL
                if (currentUser.hasSiteAdminPermission())
                {
                    message.append("  Click ");
                    message.append(HtmlString.unsafe("<a href=\""));
                    message.append(createVerificationURL(context.getContainer(), email, newUserStatus.getVerification(), extraParameters).toString());
                    message.append(HtmlString.unsafe("\" target=\"_blank\">here</a>"));
                    message.append(" to change the password from the random one that was assigned.");
                }

                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system and the administrator chose not to send a verification email.");
            }
        }
        catch (ConfigurationException e)
        {
            message.append(HtmlString.BR);
            message.append(email.getEmailAddress());
            message.append(HtmlString.unsafe(" was added successfully, but could not be emailed due to a failure:<br><pre>"));
            message.append(e.getMessage());
            message.append(HtmlString.unsafe("</pre>"));
            appendMailHelpText(message, messageContentsURL, currentUser.hasRootPermission(AddUserPermission.class));

            User newUser = UserManager.getUser(email);

            if (null != newUser)
                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system. Sending the verification email failed.");
        }
        catch (SecurityManager.UserManagementException e)
        {
            message.append("Failed to create user ").append(email.toString()).append(": ").append(e.getMessage());
        }

        // showRegistrationEmail uses default provider to generate verificationUrl
        // hide showRegistrationEmail link if provider is specified for now
        if (messageContentsURL != null && provider == null)
        {
            LinkBuilder link = new LinkBuilder("here").href(messageContentsURL).target("_blank").clearClasses();
            message.append(" Click ").append(link).append(" to see the email.");

            if (!context.getContainer().isRoot())
            {
                LinkBuilder projectGroupLink = new LinkBuilder("here").href(PageFlowUtil.urlProvider(SecurityUrls.class).getPermissionsURL(context.getContainer())).clearClasses();
                message.append(" Add the new user to a Project Group ").append(projectGroupLink).append(".");
            }
        }

        return message.getHtmlString();
    }

    public static void sendRegistrationEmail(ViewContext context, ValidEmail email, String mailPrefix, NewUserStatus newUserStatus, @Nullable List<Pair<String, String>> extraParameters) throws Exception
    {
        sendRegistrationEmail(context, email, mailPrefix, newUserStatus, extraParameters, null, true);
    }

    public static void sendRegistrationEmail(ViewContext context, ValidEmail email, String mailPrefix, NewUserStatus newUserStatus, @Nullable List<Pair<String, String>> extraParameters, String provider, boolean isAddUser) throws Exception
    {
        Container c = context.getContainer();
        User currentUser = context.getUser();

        ActionURL verificationURL = createModuleVerificationURL(context.getContainer(), email, newUserStatus.getVerification(), extraParameters, provider, isAddUser);

        SecurityManager.sendEmail(c, currentUser, getRegistrationMessage(mailPrefix, false), email.getEmailAddress(), verificationURL);
        if (!currentUser.isGuest() && !currentUser.getEmail().equals(email.getEmailAddress()))
        {
            SecurityMessage msg = getRegistrationMessage(mailPrefix, true);
            msg.setTo(email.getEmailAddress());
            SecurityManager.sendEmail(c, currentUser, msg, currentUser.getEmail(), verificationURL);
        }
    }

    public static void addSelfRegisteredUser(ViewContext context, ValidEmail email, @Nullable List<Pair<String, String>> extraParameters) throws Exception
    {
        addSelfRegisteredUser(context, email, extraParameters, null);
    }

    public static void addSelfRegisteredUser(ViewContext context, ValidEmail email, @Nullable List<Pair<String, String>> extraParameters, @Nullable String registrationProviderName) throws Exception
    {
        User currentUser = context.getUser();
        SecurityManager.NewUserStatus newUserStatus = SecurityManager.addUser(email, currentUser);

        User newUser = newUserStatus.getUser();
        if (newUserStatus.getHasLogin())
        {
            try
            {
                SecurityManager.sendRegistrationEmail(context, email, null, newUserStatus, extraParameters, registrationProviderName, true);
                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system via self-registration. Verification email was sent successfully.");
            }
            catch (ConfigurationException e)
            {
                User createdUser = UserManager.getUser(email);

                if (null != createdUser)
                    UserManager.addToUserHistory(createdUser, createdUser.getEmail() + " was added to the system via self-registration. Sending the verification email failed.");
                throw e;
            }
        }
    }

    private static void appendMailHelpText(HtmlStringBuilder builder, ActionURL messageContentsURL, boolean isAdmin)
    {
        if (isAdmin)
        {
            builder.append("You can attempt to resend this mail later by going to the Site Users link, clicking on the appropriate user from the list, and resetting their password.");

            if (messageContentsURL != null)
            {
                builder.append(" Alternatively, you can copy the ");
                builder.append(new LinkBuilder("contents of the message").href(messageContentsURL).target("_blank"));
                builder.append(" into an email client and send it to the user manually.");
            }

            builder.append(HtmlString.unsafe("</p>"));
            builder.append(HtmlString.unsafe("<p>For help on fixing your mail server settings, please consult the SMTP section of the "));
            builder.append(new HelpTopic("cpasxml").getSimpleLinkHtml("LabKey documentation on modifying your configuration file"));
            builder.append(".").append(HtmlString.BR);
        }
        else
        {
            builder.append("Please contact your site administrator.");
        }
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
        Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
        MutableSecurityPolicy policy = new MutableSecurityPolicy(project);
        policy.addRoleAssignment(getGroup(Group.groupGuests), noPermsRole);
        
        SecurityPolicyManager.savePolicy(policy);
    }

    public static void setAdminOnlyPermissions(Container c)
    {
        MutableSecurityPolicy policy = new MutableSecurityPolicy(c);

        if (!c.isProject())
        {
            //assign all principals who are project admins at the project level to the folder admin role in the container
            SecurityPolicy projectPolicy = c.getProject().getPolicy();
            Role projAdminRole = RoleManager.getRole(ProjectAdminRole.class);
            Role folderAdminRole = RoleManager.getRole(FolderAdminRole.class);
            for (RoleAssignment ra : projectPolicy.getAssignments())
            {
                if (ra.getRole().equals(projAdminRole))
                {
                    UserPrincipal principal = getPrincipal(ra.getUserId());
                    if (null != principal)
                        policy.addRoleAssignment(principal, folderAdminRole);
                }
            }
        }

        //if policy is still empty, add the guests group to the no perms role so that
        //we don't end up with an empty (i.e., inheriting policy)
        if (policy.isEmpty())
            policy.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), NoPermissionsRole.class);

        SecurityPolicyManager.savePolicy(policy);
    }

    public static void setInheritPermissions(Container c)
    {
        SecurityPolicyManager.deletePolicy(c);
    }

    private static final String SUBFOLDERS_INHERIT_PERMISSIONS_NAME = "SubfoldersInheritPermissions";
    
    public static boolean shouldNewSubfoldersInheritPermissions(Container project)
    {
        Map<String, String> props = PropertyManager.getProperties(project, SUBFOLDERS_INHERIT_PERMISSIONS_NAME);
        return "true".equals(props.get(SUBFOLDERS_INHERIT_PERMISSIONS_NAME));
    }

    public static void setNewSubfoldersInheritPermissions(Container project, User user, boolean inherit)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(project, SUBFOLDERS_INHERIT_PERMISSIONS_NAME, true);
        props.put(SUBFOLDERS_INHERIT_PERMISSIONS_NAME, Boolean.toString(inherit));
        props.save();
        addAuditEvent(project, user, String.format("Container %s was updated so that new subfolders would " + (inherit ? "" : "not ") + "inherit security permissions", project.getName()), 0);
    }

    public static void addAuditEvent(Container c, User user, String comment, int groupId)
    {
        if (user != null)
        {
            GroupAuditProvider.GroupAuditEvent event = new GroupAuditProvider.GroupAuditEvent(c.getId(), comment);
            event.setGroup(groupId);
            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());

            AuditLogService.get().addEvent(user, event);
        }
    }


    public static void changeProject(Container c, Container oldProject, Container newProject)
    {
        assert core.getSchema().getScope().isTransactionActive();

        if (oldProject.getId().equals(newProject.getId()))
            return;

        /* when demoting a project to a regular folder, delete the project groups */
        if (oldProject == c)
        {
            SecurityManager.deleteGroups(c, PrincipalType.GROUP);
        }

        /*
         * Clear all policies and assignments for folders that changed project!
         */
        SecurityPolicyManager.removeAllChildren(c);

        /* when promoting a folder to a project, create default project groups */
        if (newProject == c)
        {
            createNewProjectGroups(c);
        }
    }

    public abstract static class SecurityEmailTemplate extends UserOriginatedEmailTemplate
    {
        protected String _optionalPrefix;
        private String _verificationUrl = "";
        private String _recipient = "";
        protected boolean _verificationUrlRequired = true;

        protected SecurityEmailTemplate(@NotNull String name, String description, String subject, String body)
        {
            super(name, description, subject, body, ContentType.Plain, Scope.Site);
        }

        public void setOptionPrefix(String optionalPrefix){_optionalPrefix = optionalPrefix;}
        public void setVerificationUrl(String verificationUrl){_verificationUrl = verificationUrl;}
        public void setRecipient(String recipient){_recipient = recipient;}

        @Override
        protected void addCustomReplacements(Replacements replacements)
        {
            super.addCustomReplacements(replacements);
            replacements.add("verificationURL", String.class, "Link for a user to set a password", ContentType.Plain, c -> _verificationUrl);
            replacements.add("emailAddress", String.class, "The email address of the user performing the operation", ContentType.Plain, c -> _originatingUser == null ? null : _originatingUser.getEmail());
            replacements.add("recipient", String.class, "The email address on the 'to:' line", ContentType.Plain, c -> _recipient);
        }

        @Override
        public boolean isValid(String[] error)
        {
            if (super.isValid(error))
            {
                // add an additional requirement for the verification url
                if (!_verificationUrlRequired || getBody().contains("^verificationURL^"))
                {
                    return true;
                }
                error[0] = "The substitution param: ^verificationURL^ is required to be somewhere in the body of the message";
            }
            return false;
        }
    }

    public static class RegistrationEmailTemplate extends SecurityEmailTemplate
    {
        protected static final String DEFAULT_SUBJECT =
                "Welcome to the ^organizationName^ ^siteShortName^ Web Site new user registration";
        protected static final String DEFAULT_BODY =
                "^optionalMessage^\n\n" +
                "You now have an account on the ^organizationName^ ^siteShortName^ web site. We are sending " +
                "you this message to verify your email address and to allow you to create a password that will provide secure " +
                "access to your data on the web site. To complete the registration process, simply click the link below or " +
                "copy it to your browser's address bar. You will then be asked to choose a password.\n\n" +
                "^verificationURL^\n\n" +
                "The ^siteShortName^ home page is ^homePageURL^. If you have any questions don't hesitate to " +
                "contact the ^siteShortName^ team at ^systemEmail^.";

        @SuppressWarnings("UnusedDeclaration") // Constructor called via reflection
        public RegistrationEmailTemplate()
        {
            this("Register new user", DEFAULT_SUBJECT, DEFAULT_BODY);
        }

        private RegistrationEmailTemplate(String name, String subject, String body)
        {
            super(name, "Sent to the new user and administrator when a user is added to the site.", subject, body);
        }

        @Override
        protected void addCustomReplacements(Replacements replacements)
        {
            super.addCustomReplacements(replacements);
            replacements.add("optionalMessage", String.class, "An optional message to include with the new user email", ContentType.Plain, c -> _optionalPrefix);
        }
    }

    public static class RegistrationAdminEmailTemplate extends RegistrationEmailTemplate
    {
        @SuppressWarnings("UnusedDeclaration") // Constructor called via reflection
        public RegistrationAdminEmailTemplate()
        {
            super("Register new user (bcc to admin)", "^recipient^ : " + DEFAULT_SUBJECT, "The following message was sent to ^recipient^ :\n\n" + DEFAULT_BODY);
            _verificationUrlRequired = false;
        }
    }

    public static class PasswordResetEmailTemplate extends SecurityEmailTemplate
    {
        protected static final String DEFAULT_SUBJECT =
                "Reset Password Notification from the ^siteShortName^ Web Site";
        protected static final String DEFAULT_BODY =
                "We have reset your password on the ^organizationName^ ^siteShortName^ web site. " +
                "To sign in to the system you will need " +
                "to specify a new password. Click the link below or copy it to your browser's address bar. You will then be " +
                "asked to enter a new password.\n\n" +
                "^verificationURL^\n\n" +
                "The ^siteShortName^ home page is ^homePageURL^.";

        public PasswordResetEmailTemplate()
        {
            this("Reset password", DEFAULT_SUBJECT, DEFAULT_BODY);
        }

        private PasswordResetEmailTemplate(String name, String subject, String body)
        {
            super(name, "Sent to the user and administrator when the password of a user is reset.", subject, body);
        }
    }

    public static class PasswordResetAdminEmailTemplate extends PasswordResetEmailTemplate
    {
        public PasswordResetAdminEmailTemplate()
        {
            super("Reset password (bcc to admin)", "^recipient^ : " + DEFAULT_SUBJECT, "The following message was sent to ^recipient^ :\n\n" + DEFAULT_BODY);
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

        return switch (id)
        {
            case Group.groupAdministrators -> "Site Administrators";
            case Group.groupUsers -> "All Site Users";
            default -> group.getName();
        };
    }

    public static boolean canSeeUserDetails(Container c, User user)
    {
        return c.hasPermission(user, SeeUserDetailsPermission.class) || user.hasRootPermission(SeeUserDetailsPermission.class);
    }

    public static boolean canSeeAuditLog(User user)
    {
        //
        // Only returns true if the user has the site permission. If the user is an admin, then the permission
        // check on the current container filter will return true
        //
        return user.hasRootPermission(CanSeeAuditLogPermission.class);
    }

    public static boolean canSeeFilePaths(Container c, User user)
    {
        return (null!=c && c.hasPermission(user, SeeFilePathsPermission.class)) || user.hasRootPermission(SeeFilePathsPermission.class);
    }

    public static void adminRotatePassword(ValidEmail email, BindException errors, Container c, User user)
    {
        adminRotatePassword(email, errors, c, user, "");
    }

    public static void adminRotatePassword(ValidEmail email, BindException errors, Container c, User user, String mailErrorText)
    {
        // We let admins create passwords (i.e., entries in the logins table) if they don't already exist.
        // This addresses SSO and LDAP scenarios, see #10374.
        boolean loginExists = SecurityManager.loginExists(email);
        String pastVerb = loginExists ? "reset" : "created";
        String infinitiveVerb = loginExists ? "reset" : "create";

        try
        {
            String verification;

            if (loginExists)
            {
                // Create a placeholder password that's impossible to guess and a separate email
                // verification key that gets emailed.
                verification = SecurityManager.createTempPassword();
                SecurityManager.setPassword(email, SecurityManager.createTempPassword());
                SecurityManager.setVerification(email, verification);
            }
            else
            {
                verification = SecurityManager.createLogin(email);
            }

            try
            {
                ActionURL verificationURL = SecurityManager.createVerificationURL(c, email, verification, null);
                SecurityManager.sendEmail(c, user, SecurityManager.getResetMessage(false), email.getEmailAddress(), verificationURL);

                if (!user.getEmail().equals(email.getEmailAddress()))
                {
                    SecurityMessage msg = SecurityManager.getResetMessage(true);
                    msg.setTo(email.getEmailAddress());
                    SecurityManager.sendEmail(c, user, msg, user.getEmail(), verificationURL);
                }
                UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " " + pastVerb + " the password.");
            }
            catch (ConfigurationException | MessagingException e)
            {
                String message = "Failed to send email due to: " + e.getMessage();
                if (StringUtils.isNotBlank(mailErrorText))
                    message += '\n' + mailErrorText;
                errors.addError(new LabKeyError(new Exception(message, e)));
                UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " " + pastVerb + " the password, but sending the email failed.");
            }
        }
        catch (SecurityManager.UserManagementException e)
        {
            errors.addError(new LabKeyError(new Exception("Failed to reset password due to: " + e.getMessage(), e)));
            UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " attempted to " + infinitiveVerb + " the password, but the " + infinitiveVerb + " failed: " + e.getMessage());
        }
    }

    // We let admins delete passwords (i.e., entries in the logins table), see #42691
    public static void adminDeletePassword(ValidEmail email, User user)
    {
        new SqlExecutor(CoreSchema.getInstance().getScope()).execute("DELETE FROM " + CoreSchema.getInstance().getTableInfoLogins() + " WHERE Email = ?", email.getEmailAddress());
        UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " deleted the password.");
    }

    public static void populateUserGroupsWithStartupProps()
    {
        // assign users to groups using values read from startup configuration as appropriate for prop modifier and isBootstrap flag
        // expects startup properties formatted like: UserGroups.{email};{modifier}=SiteAdministrators,Developers
        Container rootContainer = ContainerManager.getRoot();
        ModuleLoader.getInstance().getConfigProperties(ConfigProperty.SCOPE_USER_GROUPS).forEach(prop -> {
            User user = getExistingOrCreateUser(prop.getName(), rootContainer);
            String[] groups = prop.getValue().split(",");
            for (String groupName : groups)
            {
                groupName = StringUtils.trimToNull(groupName);
                if (null != groupName)
                {
                    Group group = GroupManager.getGroup(rootContainer, groupName, GroupEnumType.SITE);
                    if (null == group)
                    {
                        try
                        {
                            group = SecurityManager.createGroup(rootContainer, groupName, PrincipalType.GROUP);
                        }
                        catch (IllegalArgumentException e)
                        {
                            throw new ConfigurationException("The group specified in startup properties scope UserGroups did not exist. User: " + prop.getName() + "Group: " + groupName, e);
                        }
                    }
                    try
                    {
                        String canUserBeAddedToGroup = SecurityManager.getAddMemberError(group, user);
                        if (null == canUserBeAddedToGroup)
                        {
                            SecurityManager.addMember(group, user);
                        }
                        else
                        {
                            // ok if the user is already a member of this group, but everything else throw an exception
                            if (!"Principal is already a member of this group".equals(canUserBeAddedToGroup))
                            {
                                throw new ConfigurationException("Startup properties UserGroups misconfigured. Could not add the user: " + prop.getName() + ", to group: " + groupName + " because: " + canUserBeAddedToGroup);
                            }
                        }
                    }
                    catch (InvalidGroupMembershipException e)
                    {
                        throw new ConfigurationException("Startup properties UserGroups misconfigured. Could not add the user: " + prop.getName() + ", to group: " + groupName, e);
                    }
                }
            }
        });
    }


    public static void populateGroupRolesWithStartupProps()
    {
        // create groups with specified roles using values read from startup properties as appropriate for prop modifier and isBootstrap flag
        // expects startup properties formatted like: GroupRoles.{groupName};{modifier}=org.labkey.api.security.roles.ApplicationAdminRole, org.labkey.api.security.roles.SomeOtherStartupRole
        Container rootContainer = ContainerManager.getRoot();
        ModuleLoader.getInstance().getConfigProperties(ConfigProperty.SCOPE_GROUP_ROLES).forEach(prop -> {
            Group group = GroupManager.getGroup(rootContainer, prop.getName(), GroupEnumType.SITE);
            if (null == group)
            {
                try
                {
                    group = SecurityManager.createGroup(rootContainer, prop.getName(), PrincipalType.GROUP);
                }
                catch (IllegalArgumentException e)
                {
                    throw new ConfigurationException("Could not add group specified in startup properties GroupRoles: " + prop.getName(), e);
                }
            }
            String[] roles = prop.getValue().split(",");
            MutableSecurityPolicy policy = new MutableSecurityPolicy(rootContainer);
            for (String roleName : roles)
            {
                roleName = StringUtils.trimToNull(roleName);
                if (null != roleName)
                {
                    Role role = RoleManager.getRole(roleName);
                    if (null == role)
                    {
                        // Issue 36611: The provisioner startup properties break deployment of older products
                        _log.error("Invalid role for group specified in startup properties GroupRoles: " + roleName);
                        continue;
                    }
                    policy.addRoleAssignment(group, role);
                }
            }
            SecurityPolicyManager.savePolicy(policy);
        });
    }

    public static void populateUserRolesWithStartupProps()
    {
        // create users with specified roles using values read from startup properties as appropriate for prop modifier and isBootstrap flag
        // expects startup properties formatted like: UserRoles.{email};{modifier}=org.labkey.api.security.roles.ApplicationAdminRole, org.labkey.api.security.roles.SomeOtherStartupRole
        Container rootContainer = ContainerManager.getRoot();
        ModuleLoader.getInstance().getConfigProperties(ConfigProperty.SCOPE_USER_ROLES).forEach(prop -> {
            User user = getExistingOrCreateUser(prop.getName(), rootContainer);
            String[] roles = prop.getValue().split(",");
            MutableSecurityPolicy policy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(rootContainer));
            for (String roleName : roles)
            {
                roleName = StringUtils.trimToNull(roleName);
                if (null != roleName)
                {
                    Role role = RoleManager.getRole(roleName);
                    if (null == role)
                    {
                        // Issue 36611: The provisioner startup properties break deployment of older products
                        _log.warn("Invalid role for user specified in startup properties UserRoles: " + roleName);
                        continue;
                    }
                    policy.addRoleAssignment(user, role);
                }
            }
            SecurityPolicyManager.savePolicy(policy);
        });
    }

    private static User getExistingOrCreateUser (String email, Container rootContainer)
    {
        try
        {
            User currentUser = UserManager.getUser(rootContainer.getCreatedBy());
            ValidEmail userEmail = new ValidEmail(email);
            User user = UserManager.getUser(userEmail);
            if (null == user)
            {
                SecurityManager.NewUserStatus userStatus = SecurityManager.addUser(userEmail, currentUser);

                user = userStatus.getUser();
                UserManager.addToUserHistory(user, user.getEmail() + " was added to the system via startup properties.");
            }
            return user;
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            throw new ConfigurationException("Invalid email address specified in startup properties for creating new user: " + email, e);
        }
        catch (SecurityManager.UserManagementException e)
        {
            throw new ConfigurationException("Unable to add new user for: " + email, e);
        }
    }

    public static List<User> parseRecipientListForContainer(Container container, String recipientList, Errors errors)
    {
        String[] recipientArr = StringUtils.split(StringUtils.trimToEmpty(recipientList), "\n");
        List<User> validRecipients = new ArrayList<>();

        for (String recipient : recipientArr)
        {
            recipient = StringUtils.trimToNull(recipient);
            if (null == recipient)
                continue;

            User user = UserManager.getUserByDisplayName(recipient);
            if (user == null)
            {
                try
                {
                    user = UserManager.getUser(new ValidEmail(recipient));
                }
                catch (InvalidEmailException x)
                {
                  errors.reject(ERROR_MSG, "Invalid email address: " + recipient);
                  continue;
                }
            }
            if (null == user)
            {
                errors.reject(ERROR_MSG, "Unknown user: " + recipient);
                continue;
            }
            if (!container.hasPermission(user, ReadPermission.class))
            {
                errors.reject(ERROR_MSG, "User does not have permissions to this folder: " + user.getEmail());
                continue;
            }
            if (!validRecipients.contains(user))
                validRecipients.add(user);
        }

        if (validRecipients.isEmpty() && !errors.hasErrors())
            errors.reject(ERROR_MSG, "No recipients provided.");

        return validRecipients;
    }


    /**
     * This is a choke point for checking permissions.
     * It handles SecurityPolicy permissions, impersonation (via User object), locked projects, and contextual roles.
     *
     * This lets the SecurityPolicy object just handle its own ACL-like functionality e.g. computing the
     * permissions that it explicitly assigns (resolving roles and groups).
     */
    public static boolean hasAllPermissions(@Nullable String logMsg, SecurityPolicy policy, UserPrincipal principal, Set<Class<? extends Permission>> perms, Set<Role> contextualRoles)
    {
        return hasPermissions(logMsg, policy, principal, perms, contextualRoles, HasPermissionOption.ALL);
    }

    public static boolean hasAnyPermissions(@Nullable String logMsg, SecurityPolicy policy, UserPrincipal principal, Set<Class<? extends Permission>> perms, Set<Role> contextualRoles)
    {
        return hasPermissions(logMsg, policy, principal, perms, contextualRoles, HasPermissionOption.ANY);
    }

    public static Set<Class<? extends Permission>> getPermissions(SecurityPolicy policy, UserPrincipal principal, Set<Role> contextualRoles)
    {
        if (policy == null)
            return Set.of();

        Container c = ContainerManager.getForId(policy.getContainerId());
        if (null == principal || (null != c && (principal instanceof User && c.isForbiddenProject((User) principal))))
            return Set.of();

        var granted = policy.getOwnPermissions(principal);
        principal.getContextualRoles(policy).forEach(r -> { if (r != null) granted.addAll(r.getPermissions()); });
        if (null != contextualRoles)
            contextualRoles.forEach(r -> granted.addAll(r.getPermissions()));
        if (principal instanceof User)
            return ((User)principal).getImpersonationContext().filterPermissions(granted);
        return granted;
    }

    public static boolean hasPermissions(@Nullable String logMsg, SecurityPolicy policy, UserPrincipal principal, Set<Class<? extends Permission>> permissions, Set<Role> contextualRoles, HasPermissionOption opt)
    {
        try
        {
            SecurityLogger.indent(logMsg);
            permissions.forEach(SecurityPolicy::testPermissionIsRegistered);

            var granted = getPermissions(policy, principal, contextualRoles);
            boolean ret = opt.accept(granted, permissions);
            SecurityLogger.log("SecurityPolicy.hasPermissions " + permissions.toString(), principal, policy, ret);

            return ret;
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }

    @NotNull
    public static List<String> getPermissionNames(SecurityPolicy policy, @NotNull UserPrincipal principal)
    {
        Set<Class<? extends Permission>> perms = SecurityManager.getPermissions(policy, principal, null);
        List<String> names = new ArrayList<>(perms.size());
        for (Class<? extends Permission> perm : perms)
        {
            Permission permInst = RoleManager.getPermission(perm);
            if (null != permInst)
                names.add(permInst.getUniqueName());
        }
        return names;
    }

    /**
     * Returns the roles the principal is playing, either due to
     * direct assignment, or due to membership in a group that is
     * assigned the role.
     * @param principal The principal
     * @return The roles this principal is playing
     */
    @NotNull
    public static Set<Role> getEffectiveRoles(@NotNull SecurityPolicy policy, @NotNull UserPrincipal principal)
    {
        return getEffectiveRoles(policy, principal, true);
    }

    @NotNull
    public static Set<Role> getEffectiveRoles(@NotNull SecurityPolicy policy, @NotNull UserPrincipal principal, boolean includeContextualRoles)
    {
        Set<Role> roles = policy.getRoles(principal.getGroups());
        roles.addAll(policy.getAssignedRoles(principal));
        if (includeContextualRoles)
            roles.addAll(principal.getContextualRoles(policy));;
        return roles;
    }

    private enum HasPermissionOption
    {
        ANY()
        {
            @Override
            boolean accept(Set<Class<? extends Permission>> granted, Set<Class<? extends Permission>> required)
            {
                return required.stream().anyMatch(granted::contains);
            }
        },
        ALL
        {
            @Override
            boolean accept(Set<Class<? extends Permission>> granted, Set<Class<? extends Permission>> required)
            {
                return granted.containsAll(required);
            }
        };

        abstract boolean accept(Set<Class<? extends Permission>> granted, Set<Class<? extends Permission>> required);
    };




    public static class TestCase extends Assert
    {
        Group groupA = null;
        Group groupB = null;
        Container project = null;
        static String TEST_USER_1_EMAIL = "testuser1@test.com";
        static String TEST_USER_1_ROLE_NAME = "org.labkey.api.security.roles.ApplicationAdminRole";
        static String TEST_USER_2_EMAIL = "testuser2@test.com";
        static String TEST_USER_2_GROUP_NAME = "Administrators";
        static String TEST_GROUP_1_NAME = "testGroup1";
        // this has to be a role that is applicable to root container
        static String TEST_GROUP_1_ROLE_NAME = "org.labkey.api.security.roles.CanSeeAuditLogRole";

        @Before
        public void setUp()
        {
            project = JunitUtil.getTestContainer().getProject();
            groupA = SecurityManager.createGroup(project, "a");
            groupB = SecurityManager.createGroup(project, "b");
        }
        //            try{tearDown();} catch(Exception e){};
        @After
        public void tearDown()
        {
            SecurityManager.deleteGroup(groupA);
            try{SecurityManager.deleteGroup(groupB);}catch(Exception ignored){}
        }

        @Test
        public void testRenameGroup()
        {
//            Group g1 = SecurityManager.createGroup(project, "group 1");
            String newName  = groupB.getName() + "new";
            int oldGroupId = getGroupId(project, groupB.getName());
            SecurityManager.renameGroup(groupB, newName, null);
            assertEquals(oldGroupId, getGroupId(project, newName).intValue());
            groupB.setName(newName);
        }

        @Test
        public void testRenameGroupExpectError()
        {
            Object[][] nameErrors = getRenameGroupExpectError();
            for(Object[] nameError : nameErrors)
            {
                attemptRenameGroupExpectErrors((String) nameError[0],(String) nameError[1]);
            }
        }

        private void attemptRenameGroupExpectErrors(String newName, String expectedErrorMessage)
        {

            try
            {
                renameGroup(groupA, newName, null);
            }
            catch(IllegalArgumentException e)
            {
                assertEquals(expectedErrorMessage, e.getMessage());
            }
        }

        private Object[][] getRenameGroupExpectError()
        {
            return new Object[][]{{"", "Name is required (may not be blank)"},
                    {null, "Name is required (may not be blank)"},
                    {groupA.getName(), "Cannot rename group '" + groupA.getName() +
                            "' to '" + groupA.getName() + "' because that name is already used by another group!"},
                    {groupB.getName(), "Cannot rename group '" + groupA.getName() +
                            "' to '" + groupB.getName() + "' because that name is already used by another group!"}

            };
        }

        @Test
        public void testCircularGroupMembership() throws InvalidGroupMembershipException
        {
            LinkedList<Group> groups = new LinkedList<>();

            try
            {
                int maxLoop = 20;
                int count = 0;

//                for(int i=0; i<=maxLoop; i++)
//                {
//                    try
//                    {
//                        deleteGroup(getGroupId(project, "testGroup" + i, groupA.getOwnerId(), false, true));
//                    }
//                    catch(Exception e){}
//                }

                groups.add(groupB);
                addMember(groupA, groupB);

                while(count++ < maxLoop)
                {
                    Group newGroup = createGroup(project, "testGroup" + count);
                    addMember(groups.getLast(), newGroup);
                    groups.add(newGroup);
                    try
                    {
                        addMember(newGroup, groupA);
                        fail("Should have thrown error when attempting to create circular group. Chain is length: " + count);
                    }
                    catch (InvalidGroupMembershipException e)
                    {
                        //eat the exception, it's expected
                    }
                }
            }
            finally
            {
                deleteMember(groupA, groupB);
                while(groups.size()>1)//groupB will be deleted by the tearDown
                {
                    deleteGroup(groups.removeLast());
                }
            }
        }

        @Test
        public void testAddMemberToGroup() throws InvalidGroupMembershipException
        {
            Object[][] groupMemberResponses = getAddMemberErrorArgs();
            for(Object[] groupMemberResponse : groupMemberResponses)
            {
                addMemberToGroupVerifyResponse((Group) groupMemberResponse[0],
                        (UserPrincipal) groupMemberResponse[1], (String) groupMemberResponse[2]);
            }

            addMember(groupA, groupB);
            addMemberToGroupVerifyResponse(groupA, groupB, ALREADY_A_MEMBER_ERROR_MESSAGE);
        }

        private Object[][] getAddMemberErrorArgs()
        {
            return new Object[][]{  {null, null, NULL_GROUP_ERROR_MESSAGE},
                    {groupA, null, NULL_PRINCIPAL_ERROR_MESSAGE},
                    {groupA, groupA, ADD_GROUP_TO_ITSELF_ERROR_MESSAGE}};
        }

        private void addMemberToGroupVerifyResponse(Group group, UserPrincipal principal, String expectedResponse)
        {
            assertEquals(expectedResponse, getAddMemberError(group, principal));
        }

        @Test
        public void testCreateUser() throws Exception
        {
            ValidEmail email;
            String rawEmail;

            // Just in case, loop until we find one that doesn't exist already
            do
            {
                rawEmail = "test_" + Math.round(Math.random() * 10000) + "@localhost.xyz";
                email = new ValidEmail(rawEmail);
            }
            while (SecurityManager.loginExists(email));

            User user = null;

            // Test create user, verify, login, and delete
            try
            {
                NewUserStatus status = addUser(email, null);
                user = status.getUser();
                assertTrue("addUser", user.getUserId() != 0);

                boolean success = SecurityManager.verify(email, status.getVerification());
                assertTrue("verify", success);

                SecurityManager.setVerification(email, null);

                String password = generateStrongPassword(user);
                SecurityManager.setPassword(email, password);

                User user2 = AuthenticationManager.authenticate(ViewServlet.mockRequest("GET", new ActionURL(), null, null, null), rawEmail, password);
                assertNotNull("\"" + rawEmail + "\" failed to authenticate with password \"" + password + "\"; check labkey.log around timestamp " + DateUtil.formatDateTime(new Date(), "HH:mm:ss,SSS") + " for the reason", user2);
                assertEquals(user, user2);
            }
            finally
            {
                UserManager.deleteUser(user.getUserId());
            }
        }

        private String generateStrongPassword(User user)
        {
            String password;

            // Check and loop until password is valid for this user. These randomly generated passwords will often
            // (about 1% of the time) contain a three-character sequence from the email address, which the strong
            // rules disallow.
            do
            {
                password = createTempPassword() + "Az9!";
            }
            while (!PasswordRule.Strong.isValidForLogin(password, user, null));

            return password;
        }

        @Test
        public void testACLS()
        {
            Container testFolder = JunitUtil.getTestContainer();

            // Ignore all contextual roles for the test user
            final User testuser = TestContext.get().getUser();
            UserPrincipal user = new UserPrincipal(testuser.getName(), testuser.getUserId(), testuser.getPrincipalType())
            {
                @Override
                public int[] getGroups()
                {
                    return testuser.getGroups();
                }

                @Override
                public Set<Role> getContextualRoles(SecurityPolicy policy)
                {
                    return Set.of();
                }

                @Override
                public boolean isInGroup(int group)
                {
                    return testuser.isInGroup(group);
                }

                @Override
                public boolean isActive()
                {
                    return testuser.isActive();
                }
            };

            // Test Site User and Guest groups
            MutableSecurityPolicy policy = new MutableSecurityPolicy(testFolder);
            assertFalse("no permission check", policy.hasPermission(user, ReadPermission.class));

            policy.addRoleAssignment(user, ReaderRole.class);
            assertTrue("read permission", policy.hasPermission(user, ReadPermission.class));
            assertFalse("no insert permission", policy.hasPermission(user, InsertPermission.class));
            assertFalse("no update permission", policy.hasPermission(user, UpdatePermission.class));

            Group guestGroup = SecurityManager.getGroup(Group.groupGuests);
            policy.addRoleAssignment(guestGroup, ReaderRole.class);
            assertTrue("read permission", policy.hasPermission(user, ReadPermission.class));
            assertFalse("no insert permission", policy.hasPermission(user, InsertPermission.class));
            assertFalse("no update permission", policy.hasPermission(user, UpdatePermission.class));

            Group userGroup = SecurityManager.getGroup(Group.groupUsers);
            policy.addRoleAssignment(userGroup, EditorRole.class);
            assertTrue("read permission", policy.hasPermission(user, ReadPermission.class));
            assertTrue("insert permission", policy.hasPermission(user, InsertPermission.class));
            assertTrue("update permission", policy.hasPermission(user, UpdatePermission.class));

            // Guest
            assertTrue("read permission", policy.hasPermission(User.guest, ReadPermission.class));
            assertFalse("no insert permission", policy.hasPermission(User.guest, InsertPermission.class));
            assertFalse("no update permission", policy.hasPermission(User.guest, UpdatePermission.class));


            // again with SecurityManager.hasPermissions
            policy = new MutableSecurityPolicy(testFolder);
            // Test Site User and Guest groups
            assertFalse("no permission check", SecurityManager.hasAllPermissions(null, policy, user, Set.of(ReadPermission.class), Set.of()));

            policy.addRoleAssignment(user, ReaderRole.class);
            assertTrue("read permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(ReadPermission.class), Set.of()));
            assertFalse("no insert permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(InsertPermission.class), Set.of()));
            assertFalse("no update permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(UpdatePermission.class), Set.of()));

            policy.addRoleAssignment(guestGroup, ReaderRole.class);
            assertTrue("read permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(ReadPermission.class), Set.of()));
            assertFalse("no insert permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(InsertPermission.class), Set.of()));
            assertFalse("no update permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(UpdatePermission.class), Set.of()));

            policy.addRoleAssignment(userGroup, EditorRole.class);
            assertTrue("read permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(ReadPermission.class), Set.of()));
            assertTrue("insert permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(InsertPermission.class), Set.of()));
            assertTrue("update permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(UpdatePermission.class), Set.of()));

            // Guest
            assertTrue("read permission", SecurityManager.hasAllPermissions(null, policy, User.guest, Set.of(ReadPermission.class), Set.of()));
            assertFalse("no insert permission", SecurityManager.hasAllPermissions(null, policy, User.guest, Set.of(InsertPermission.class), Set.of()));
            assertFalse("no update permission", SecurityManager.hasAllPermissions(null, policy, User.guest, Set.of(UpdatePermission.class), Set.of()));
        }

        @Test
        public void testReadOnlyImpersonate()
        {
            Container testFolder = JunitUtil.getTestContainer();

            // Ignore all contextual roles for the test user
            final User testuser = TestContext.get().getUser();
            // this user is subsetted to only permit read permissions (see AllowedForReadOnlyUser)
            User user = new User(testuser.getEmail(), 1000000);
            user.setImpersonationContext(new ReadOnlyImpersonatingContext());

            MutableSecurityPolicy policy = new MutableSecurityPolicy(testFolder);
            // Test Site User and Guest groups
            assertFalse("no permission check", SecurityManager.hasAllPermissions(null, policy, user, Set.of(ReadPermission.class), Set.of()));

            policy.addRoleAssignment(user, ReaderRole.class);
            assertTrue("read permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(ReadPermission.class), Set.of()));
            assertFalse("no insert permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(InsertPermission.class), Set.of()));
            assertFalse("no update permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(UpdatePermission.class), Set.of()));

            Group guestGroup = SecurityManager.getGroup(Group.groupGuests);
            policy.addRoleAssignment(guestGroup, ReaderRole.class);
            assertTrue("read permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(ReadPermission.class), Set.of()));
            assertFalse("no insert permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(InsertPermission.class), Set.of()));
            assertFalse("no update permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(UpdatePermission.class), Set.of()));

            Group userGroup = SecurityManager.getGroup(Group.groupUsers);
            policy.addRoleAssignment(userGroup, EditorRole.class);
            assertTrue("read permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(ReadPermission.class), Set.of()));
            assertFalse("insert permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(InsertPermission.class), Set.of()));
            assertFalse("update permission", SecurityManager.hasAllPermissions(null, policy, user, Set.of(UpdatePermission.class), Set.of()));

            // Guest
            assertTrue("read permission", SecurityManager.hasAllPermissions(null, policy, User.guest, Set.of(ReadPermission.class), Set.of()));
            assertFalse("no insert permission", SecurityManager.hasAllPermissions(null, policy, User.guest, Set.of(InsertPermission.class), Set.of()));
            assertFalse("no update permission", SecurityManager.hasAllPermissions(null, policy, User.guest, Set.of(UpdatePermission.class), Set.of()));
        }


        @Test
        public void testEmailValidation()
        {
            testEmail("this@that.com", true);
            testEmail("foo@fhcrc.org", true);
            testEmail("dots.dots@dots.co.uk", true);
            testEmail("funny_chars#that%are^allowed&in*email!addresses@that.com", true);

            String displayName = "Personal Name";
            ValidEmail email = testEmail(displayName + " <personal@name.com>", true);
            assertEquals("Display name: expected '" + displayName + "' but was '" + email.getPersonal() + "'", displayName, email.getPersonal());

            String defaultDomain = ValidEmail.getDefaultDomain();
            // If default domain is defined this should succeed; if it's not defined, this should fail.
            testEmail("foo", defaultDomain.length() > 0);

            testEmail("~()@bar.com", false);
            testEmail("this@that.com@con", false);
            testEmail(null, false);
            testEmail("", false);
            testEmail("<@bar.com", false);
            testEmail(displayName + " <personal>", false);  // Can't combine personal name with default domain
        }


        private ValidEmail testEmail(String rawEmail, boolean valid)
        {
            ValidEmail email = null;

            try
            {
                email = new ValidEmail(rawEmail);
                assertTrue(rawEmail, valid);
            }
            catch(InvalidEmailException e)
            {
                assertFalse(rawEmail, valid);
            }

            return email;
        }

        @Test
        public void testDisplayName() throws Exception
        {
            assertEquals("user testdisplayname", displayNameFromEmail(new ValidEmail("user_testDisplayName@labkey.org"),1));
            assertEquals("first last", displayNameFromEmail(new ValidEmail("first.last@labkey.org"),1));
            assertEquals("Ricky Bobby", displayNameFromEmail(new ValidEmail("Ricky Bobby <user@labkey.org>"),1));
        }

        /**
         * Test that a new user can be created with role specified by startup properties
         */
        @Test
        public void testStartupPropertiesForUserRoles() throws Exception
        {
            // ensure that the site wide ModuleLoader has test startup property values in the _configPropertyMap
            prepareTestStartupProperties();

            // examine the original list of users to ensure the test user is not already created
            ValidEmail userEmail = new ValidEmail(TEST_USER_1_EMAIL);
            Map<ValidEmail, User> originalUserEmailMap = UserManager.getUserEmailMap();
            assertFalse("The user defined in the startup properties was already on the user list for this server: " + userEmail, originalUserEmailMap.containsKey(userEmail));

            // call the method that makes use of the test startup properties to add a new user with specified role
            populateUserRolesWithStartupProps();

            // check that the expected user has been added to the list of users
            Map<ValidEmail, User> revisedUserEmailMap = UserManager.getUserEmailMap();
            assertTrue("The user defined in the startup properties was not added to the list of users: " + userEmail, revisedUserEmailMap.containsKey(userEmail));

            // check that the user has the expected role
            Container rootContainer = ContainerManager.getRoot();
            User user = UserManager.getUser(userEmail);
            Collection<Role> roles = rootContainer.getPolicy().getAssignedRoles(user);
            Role role = RoleManager.getRole(TEST_USER_1_ROLE_NAME);
            assertTrue("The user defined in the startup properties: "  + userEmail + " did not have the specified role: " + TEST_USER_1_ROLE_NAME, roles.contains(role));

            // delete the test user that was added
            UserManager.deleteUser(user.getUserId());
        }

        /**
         * Test that a new group can be created with role specified by startup properties
         */
        @Test
        public void testStartupPropertiesForGroupRoles()
        {
            // ensure that the site wide ModuleLoader has test startup property values in the _configPropertyMap
            prepareTestStartupProperties();

            // examine the original list of groups to ensure the test group is not already created
            Container rootContainer = ContainerManager.getRoot();
            assertNull("The group defined in the startup properties was already on the server: " + TEST_GROUP_1_NAME, GroupManager.getGroup(rootContainer, TEST_GROUP_1_NAME, GroupEnumType.SITE));

            // call the method that makes use of the test startup properties to add a new group with specified role
            populateGroupRolesWithStartupProps();

            // check that the expected group has been added
            assertNotNull("The group defined in the startup properties was not added to the list of groups: " + TEST_GROUP_1_NAME, GroupManager.getGroup(rootContainer, TEST_GROUP_1_NAME, GroupEnumType.SITE));

            // check that the group has the expected role
            Group group = GroupManager.getGroup(rootContainer, TEST_GROUP_1_NAME, GroupEnumType.SITE);
            Collection<Role> roles = rootContainer.getPolicy().getAssignedRoles(group);
            Role role = RoleManager.getRole(TEST_GROUP_1_ROLE_NAME);
            assertTrue("The group defined in the startup properties: "  + TEST_GROUP_1_NAME + " did not have the specified role: " + TEST_GROUP_1_ROLE_NAME, roles.contains(role));

            // delete the test group that was added
            deleteGroup(group);
        }

        /**
         * Test that a new user can be created with group specified by startup properties
         */
        @Test
        public void testStartupPropertiesForUserGroups() throws Exception
        {
            // ensure that the site wide ModuleLoader has test startup property values in the _configPropertyMap
            prepareTestStartupProperties();

            // examine the original list of users to ensure the test user is not already created
            ValidEmail userEmail = new ValidEmail(TEST_USER_2_EMAIL);
            Map<ValidEmail, User> originalUserEmailMap = UserManager.getUserEmailMap();
            assertFalse("The user defined in the startup properties was already on the user list for this server: " + userEmail, originalUserEmailMap.containsKey(userEmail));

            // call the method that makes use of the test startup properties to add a new user with specified group assignment
            populateUserGroupsWithStartupProps();

            // check that the expected user has been added to the list of users
            Map<ValidEmail, User> revisedUserEmailMap = UserManager.getUserEmailMap();
            assertTrue("The user defined in the startup properties was not added to the list of users: " + userEmail, revisedUserEmailMap.containsKey(userEmail));

            // check that the user has been added to the specified group
            Container rootContainer = ContainerManager.getRoot();
            User user = UserManager.getUser(userEmail);
            Group group = GroupManager.getGroup(rootContainer, TEST_USER_2_GROUP_NAME, GroupEnumType.SITE);
            assertEquals("The user defined in the startup properties: " + TEST_USER_2_EMAIL + " did not have the specified group: " + TEST_USER_2_GROUP_NAME, "Principal is already a member of this group", SecurityManager.getAddMemberError(group, user));

            // delete the test user that was added
            UserManager.deleteUser(user.getUserId());
        }

        private void prepareTestStartupProperties()
        {
            // prepare a multimap of config properties to test with that has properties assigned for several scopes
            MultiValuedMap<String, ConfigProperty> testConfigPropertyMap = new HashSetValuedHashMap<>();

            // prepare test UserRole properties
            ConfigProperty testUserRoleProp = new ConfigProperty(TEST_USER_1_EMAIL, ",," + TEST_USER_1_ROLE_NAME + ",,", "startup", ConfigProperty.SCOPE_USER_ROLES);
            testConfigPropertyMap.put(ConfigProperty.SCOPE_USER_ROLES, testUserRoleProp);

            // prepare test GroupRole properties
            ConfigProperty testGroupRoleProp = new ConfigProperty(TEST_GROUP_1_NAME, ",," + TEST_GROUP_1_ROLE_NAME + ",,", "startup", ConfigProperty.SCOPE_GROUP_ROLES);
            testConfigPropertyMap.put(ConfigProperty.SCOPE_GROUP_ROLES, testGroupRoleProp);

            // prepare test UserRole properties
            ConfigProperty testUserGroupProp = new ConfigProperty(TEST_USER_2_EMAIL, ",," + TEST_USER_2_GROUP_NAME + ",,", "startup", ConfigProperty.SCOPE_USER_GROUPS);
            testConfigPropertyMap.put(ConfigProperty.SCOPE_USER_GROUPS, testUserGroupProp);

            // set these test startup properties to be used by the entire server
            ModuleLoader.getInstance().setConfigProperties(testConfigPropertyMap);
        }

        @Test
        public void testGetUsersWithPermissions() throws Exception
        {
            Container parent = JunitUtil.getTestContainer();
            Container test = null;
            Pair<ValidEmail,User> userAB  = new Pair<>(new ValidEmail("AB@localhost.test"), null);
            Pair<ValidEmail,User> userAC  = new Pair<>(new ValidEmail("AC@localhost.test"), null);
            Pair<ValidEmail,User> userABC = new Pair<>(new ValidEmail("ABC@localhost.test"), null);

            try
            {
                // Create real users
                for (var p : Arrays.asList(userAB, userAC, userABC))
                {
                    if (null == UserManager.getUser(p.getKey()))
                        addUser(p.getKey(), null, false);
                    p.setValue(UserManager.getUser(p.getKey()));
                }
                // Create a mock container
                String id = GUID.makeGUID();
                test = new Container(parent, "test" + id, id, -1, 0, new Date(), 0, false)
                {
                    MutableSecurityPolicy p = null;
                    @Override
                    public @NotNull SecurityPolicy getPolicy()
                    {
                        if (null == p)
                            p = new MutableSecurityPolicy(this);
                        return p;
                    }
                    @Override
                    public boolean isForbiddenProject(User user)
                    {
                        return false;
                    }
                };
                MutableSecurityPolicy policy = (MutableSecurityPolicy)test.getPolicy();
                policy.addAssignment(new RoleAssignment(id,userAB.getValue(),new RoleAB()));
                policy.addAssignment(new RoleAssignment(id,userAC.getValue(),new RoleAC()));
                policy.addAssignment(new RoleAssignment(id,userABC.getValue(),new RoleAB()));
                policy.addAssignment(new RoleAssignment(id,userABC.getValue(),new RoleAC()));

                var usersWithAll = new HashSet(SecurityManager.getUsersWithPermissions(test, Set.of(PermissionA.class)));
                assertEquals(3, usersWithAll.size());
                assertTrue(usersWithAll.contains(userAB.getValue()));
                assertTrue(usersWithAll.contains(userAC.getValue()));
                assertTrue(usersWithAll.contains(userABC.getValue()));
                var usersWithAny = new HashSet(SecurityManager.getUsersWithOneOf(test, Set.of(PermissionA.class)));
                assertEquals(usersWithAll, usersWithAny);

                usersWithAll = new HashSet(SecurityManager.getUsersWithPermissions(test, Set.of(PermissionB.class)));
                assertEquals(2, usersWithAll.size());
                assertTrue(usersWithAll.contains(userAB.getValue()));
                assertTrue(usersWithAll.contains(userABC.getValue()));
                usersWithAny = new HashSet(SecurityManager.getUsersWithOneOf(test, Set.of(PermissionB.class)));
                assertEquals(usersWithAll, usersWithAny);

                usersWithAll = new HashSet(SecurityManager.getUsersWithPermissions(test, Set.of(PermissionB.class, PermissionC.class)));
                assertEquals(1, usersWithAll.size());
                assertTrue(usersWithAll.contains(userABC.getValue()));
                usersWithAny = new HashSet(SecurityManager.getUsersWithOneOf(test, Set.of(PermissionB.class, PermissionC.class)));
                assertEquals(3, usersWithAny.size());
                assertTrue(usersWithAny.contains(userAB.getValue()));
                assertTrue(usersWithAny.contains(userAC.getValue()));
                assertTrue(usersWithAny.contains(userABC.getValue()));
            }
            finally
            {
                for (var p : Arrays.asList(userAB, userAC, userABC))
                    if (null != p.getValue())
                        UserManager.deleteUser(p.getValue().getUserId());
            }
        }
    }

    static class RoleAB extends AbstractRole implements Role.TestRole
    {
        RoleAB()
        {
            super("RoleAB", "RoleAB", PermissionA.class, PermissionB.class);
        }
    }
    static class RoleAC extends AbstractRole implements Role.TestRole
    {
        RoleAC()
        {
            super("RoleAC", "RoleAC", PermissionA.class, PermissionC.class);
        }
    }
    static class PermissionA extends AbstractPermission implements Permission.TestPermission
    {
        PermissionA()
        {
            super("PermissionA","PermissionA");
        }
    }
    static class PermissionB extends AbstractPermission implements Permission.TestPermission
    {
        PermissionB()
        {
            super("PermissionB","PermissionB");
        }
    }
    static class PermissionC extends AbstractPermission implements Permission.TestPermission
    {
        PermissionC()
        {
            super("PermissionC","PermissionC");
        }
    }
}
