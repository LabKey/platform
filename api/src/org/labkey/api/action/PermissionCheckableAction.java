/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.action;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.ContextualRoles;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresAllOf;
import org.labkey.api.security.RequiresAnyOf;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.AdminReadPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CSRFException;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.view.ForbiddenProjectException;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by adam on 3/25/2016.
 */
public abstract class PermissionCheckableAction implements Controller, PermissionCheckable, HasViewContext
{
    private ViewContext _context = null;
    UnauthorizedException.Type _unauthorizedType = UnauthorizedException.Type.redirectToLogin;

    @Override
    public ViewContext getViewContext()
    {
        return _context;
    }


    @Override
    public void setViewContext(ViewContext context)
    {
        _context = context;
    }

    protected void setUnauthorizedType(UnauthorizedException.Type unauthorizedType)
    {
        _unauthorizedType = unauthorizedType;
    }

    @Override
    public void checkPermissions() throws UnauthorizedException
    {
        checkPermissions(_unauthorizedType);
    }

    protected void checkPermissions(UnauthorizedException.Type unauthorizedType) throws UnauthorizedException
    {
        // ideally, we should pass the bound FORM to getContextualRoles so that actions can determine if the OwnerRole
        // should apply, but this would require a large amount of rework that is too much to consider now.
        //
        // If the action needs Basic Authentication, then do the standard permissions check, but ignore terms-of-use
        // (non-web clients can't view/respond to terms of use page). If user is guest and unauthorized, then send a
        // Basic Authentication request.
        try
        {
            boolean isSendBasic = (unauthorizedType == UnauthorizedException.Type.sendBasicAuth || unauthorizedType == UnauthorizedException.Type.sendUnauthorized);
            checkPermissionsAndTermsOfUse(getContextualRoles(), isSendBasic);
        }
        catch (UnauthorizedException e)
        {
            e.setType(unauthorizedType);
            throw e;
        }
    }

    private void checkActionPermissions(Set<Role> contextualRoles) throws UnauthorizedException
    {
        try
        {
            SecurityLogger.indent("BaseViewAction.checkActionPermissions(" + getClass().getName() + ")");
            _checkActionPermissions(contextualRoles);
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }


    private void _checkActionPermissions(Set<Role> contextualRoles) throws UnauthorizedException
    {
        ViewContext context = getViewContext();
        String method = context.getRequest().getMethod();
        boolean isPOST = "POST".equals(method);

        Container c = context.getContainer();
        User user = context.getUser();

        if (c.isForbiddenProject(user))
            throw new ForbiddenProjectException();

        Class<? extends Controller> actionClass = getClass();

        boolean requiresSiteAdmin = actionClass.isAnnotationPresent(RequiresSiteAdmin.class);
        if (requiresSiteAdmin && !user.isInSiteAdminGroup())
        {
            throw new UnauthorizedException();
        }

        boolean requiresLogin = actionClass.isAnnotationPresent(RequiresLogin.class);
        if (requiresLogin && user.isGuest())
        {
            throw new UnauthorizedException();
        }

        // User must have ALL permissions in this set
        Set<Class<? extends Permission>> permissionsRequired = new HashSet<>();

        RequiresPermission requiresPerm = actionClass.getAnnotation(RequiresPermission.class);
        if (null != requiresPerm)
        {
            permissionsRequired.add(requiresPerm.value());
        }

        RequiresAllOf requiresAllOf = actionClass.getAnnotation(RequiresAllOf.class);
        if (null != requiresAllOf)
        {
            Collections.addAll(permissionsRequired, requiresAllOf.value());
        }

        // Special handling for admin console actions to support TroubleShooter role.
        // Only users with the specified permission (AdminPermission.class by default) can POST,
        // but those with AdminReadPermission (i.e., TroubleShooters) can GET.
        AdminConsoleAction adminConsoleAction = actionClass.getAnnotation(AdminConsoleAction.class);
        boolean isAdminConsoleAction = null != adminConsoleAction;
        if (isAdminConsoleAction)
        {
            if (!c.isRoot())
                throw new NotFoundException();

            if (isPOST)
                permissionsRequired.add(adminConsoleAction.value());
            else
                permissionsRequired.add(AdminReadPermission.class);
        }

        ContextualRoles rolesAnnotation = actionClass.getAnnotation(ContextualRoles.class);
        if (rolesAnnotation != null)
        {
            contextualRoles = RoleManager.mergeContextualRoles(context, rolesAnnotation.value(), contextualRoles);
        }

        // Policy must have all permissions in permissionsRequired
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(c);
        if (!policy.hasPermissions(user, permissionsRequired, contextualRoles))
            throw new UnauthorizedException();


        CSRF.Method csrfCheck = CSRF.Method.POST;
        if ("ADMINONLY".equals(AppProps.getInstance().getCSRFCheck()))
        {
            boolean requiresAdmin = requiresSiteAdmin || permissionsRequired.contains(AdminPermission.class) || permissionsRequired.contains(AdminOperationsPermission.class);
            csrfCheck = requiresAdmin ? CSRF.Method.POST : CSRF.Method.NONE;
        }

        CSRF csrfAnnotation = null;
        if (actionClass.isAnnotationPresent(CSRF.class))
        {
            csrfAnnotation = actionClass.getAnnotation(CSRF.class);
            csrfCheck = csrfAnnotation.value();
        }

        csrfCheck.validate(context);

        // if csrfCheck != POST, check to see if it would have failed with csrfCheck == POST, for auditing purposes
        if (csrfCheck != CSRF.Method.POST && (csrfAnnotation==null || csrfAnnotation.value() != CSRF.Method.NONE))
        {
            try
            {
                CSRF.Method.POST.validate(context);
            }
            catch (CSRFException ex)
            {
                String referer = StringUtils.trimToNull(ex.getReferer());
                Logger.getLogger(PermissionCheckableAction.class).warn("CSRF checking will fail for this request: " + getClass() + (null != referer ? " referer: " + referer : ""));
                SpringActionController.getActionDescriptor(this.getClass()).addException(ex);
            }
        }

        // User must have at least one permission in this set
        Set<Class<? extends Permission>> permissionsAnyOf = Collections.emptySet();
        RequiresAnyOf requiresAnyOf = actionClass.getAnnotation(RequiresAnyOf.class);
        if (null != requiresAnyOf)
        {
            permissionsAnyOf = new HashSet<>();
            Collections.addAll(permissionsAnyOf, requiresAnyOf.value());
            if (!policy.hasOneOf(user, permissionsAnyOf, contextualRoles))
                throw new UnauthorizedException();
        }

        boolean requiresNoPermission = actionClass.isAnnotationPresent(RequiresNoPermission.class);

        if (permissionsRequired.isEmpty() && !requiresSiteAdmin && !requiresLogin && !requiresNoPermission && !isAdminConsoleAction && permissionsAnyOf.isEmpty())
        {
            throw new ConfigurationException("@RequiresPermission, @RequiresSiteAdmin, @RequiresLogin, @RequiresNoPermission, or @AdminConsoleAction annotation is required on class " + actionClass.getName());
        }

        // All permission checks have succeeded, now check for deprecated action.
        if (actionClass.isAnnotationPresent(DeprecatedAction.class))
            throw new DeprecatedActionException(actionClass);
    }


    private void checkPermissionsAndTermsOfUse(Set<Role> contextualRoles, boolean isSendBasic) throws UnauthorizedException
    {
        checkActionPermissions(contextualRoles);

        if (!getClass().isAnnotationPresent(IgnoresTermsOfUse.class))
            verifyTermsOfUse(isSendBasic);
    }


    /**
     * Check if terms of use are ever required for this request. If so, enumerate all the terms-of-use providers and ask
     * each to verify terms are set via its custom mechanism. If a provider's terms are active and the user hasn't yet
     * agreed to them then the provider redirects to its terms action by throwing a RedirectException.
     */
    protected void verifyTermsOfUse(boolean isSendBasic) throws RedirectException
    {
        ViewContext context = getViewContext();
        if (context.getUser().isSearchUser())
            return;

        Container c = context.getContainer();
        if (null == c)
            return;

        boolean isBasicAuth = isSendBasic || SecurityManager.isBasicAuthentication(context.getRequest());

        for (SecurityManager.TermsOfUseProvider provider : SecurityManager.getTermsOfUseProviders())
            provider.verifyTermsOfUse(context, isBasicAuth);
    }


    /**
     * Actions may provide a set of {@link Role}s used during permission checking
     * or null if no contextual roles apply.
     */
    @Nullable
    protected Set<Role> getContextualRoles()
    {
        return null;
    }
}
