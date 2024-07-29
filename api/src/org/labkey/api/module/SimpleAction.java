/*
 * Copyright (c) 2009-2018 LabKey Corporation
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
package org.labkey.api.module;

import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.NavTrailAction;
import org.labkey.api.data.Container;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.util.Path;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashSet;
import java.util.Set;

/**
 * Action for simple .html file-backed views in a module's ./resources directory.
 */
public class SimpleAction extends BaseViewAction<Object> implements NavTrailAction
{
    private ModuleHtmlView _view;
    private Exception _exception;

    public SimpleAction(Module module, Path path)
    {
        try
        {
            _view = ModuleHtmlView.get(module, path);
        }
        catch (Exception e)
        {
            //hold onto it so we can show it during handleRequest()
            _exception = e;
        }
    }

    @Override
    protected String getCommandClassMethodName()
    {
        return null;
    }

    @Override
    public ModelAndView handleRequest() throws Exception
    {
        //throw any previously-stored exception
        if (null != _exception)
            throw _exception;

        getPageConfig().setIncludePostParameters(true);

        //override page template if view requests
        if (_view != null && null != _view.getPageTemplate())
            getPageConfig().setTemplate(_view.getPageTemplate());

        if (_view.isAppView())
            getViewContext().setAppView(true);

        return _view;
    }

    @Override
    public void validate(Object target, Errors errors)
    {
        //since simple HTML views don't interact with permanent storage (i.e., the database)
        //we really don't need to do much validation here
        //in the future we could validate query string params against value or regex
        //checks, and replace an error message token in the HTML view, but
        //that could also be easily done by JavaScript in the page itself.
    }

    @Override
    public void checkPermissions() throws UnauthorizedException
    {
        User user = getUser();
        Container container = getContainer();
        if (null == container)
            throw new NotFoundException("The folder path '" + getViewContext().getActionURL().getExtraPath() + "' does not match an existing folder on the server!");

        if (null != _view)
        {
            if (_view.isRequiresLogin() && user.isGuest())
                throw new UnauthorizedException("You must sign in to see this content.");

            if (OptionalFeatureService.get().isFeatureEnabled(ACL.RESTORE_USE_OF_ACLS))
            {
                Set<Class<? extends Permission>> oldStylePerms = new HashSet<>();

                // Handle old-style permission bits, for backward compatibility
                int perm = _view.getRequiredPerms();

                if ((perm & ACL.PERM_READ) > 0 || (perm & ACL.PERM_READOWN) > 0)
                    oldStylePerms.add(ReadPermission.class);
                if ((perm & ACL.PERM_INSERT) > 0)
                    oldStylePerms.add(InsertPermission.class);
                if ((perm & ACL.PERM_UPDATE) > 0 || (perm & ACL.PERM_UPDATEOWN) > 0)
                    oldStylePerms.add(UpdatePermission.class);
                if ((perm & ACL.PERM_DELETE) > 0 || (perm & ACL.PERM_DELETEOWN) > 0)
                    oldStylePerms.add(DeletePermission.class);
                if ((perm & ACL.PERM_ADMIN) > 0)
                    oldStylePerms.add(AdminPermission.class);

                if (!container.hasPermissions(user, oldStylePerms))
                {
                    container.throwIfForbiddenProject(user);
                    throw new UnauthorizedException("You do not have permission to view this content.");
                }
            }

            Set<Class<? extends Permission>> perms = _view.getRequiredPermissionClasses();
            if (!perms.isEmpty())
            {
                if (!container.hasPermissions(user, perms))
                {
                    container.throwIfForbiddenProject(user);
                    throw new UnauthorizedException("You do not have permission to view this content.");
                }
            }
        }

        verifyTermsOfUse(false);
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        root.addChild(_view.getTitle());
    }
}
