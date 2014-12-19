/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
import org.labkey.api.resource.Resource;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.Errors;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/*
* User: Dave
* Date: Jan 23, 2009
* Time: 10:21:33 AM
*/

/**
 * Action for simple file-based views in a module
 */
public class SimpleAction extends BaseViewAction implements NavTrailAction
{
    public static WebPartView getModuleHtmlView(Module module, String viewName, Portal.WebPart webpart) throws IOException
    {
        Resource r = module.getModuleResource(SimpleController.VIEWS_DIRECTORY + "/" + viewName + ModuleHtmlViewDefinition.HTML_VIEW_EXTENSION);
        return r != null && r.isFile() ? new ModuleHtmlView(r, webpart) : null;
    }

    public enum PermissionEnum
    {
        login(0),
        read(ACL.PERM_READ),
        insert(ACL.PERM_INSERT),
        update(ACL.PERM_UPDATE),
        delete(ACL.PERM_DELETE),
        admin(ACL.PERM_ADMIN),
        none(ACL.PERM_NONE);

        private int _value = 0;

        PermissionEnum(int value)
        {
            _value = value;
        }

        public int toInt()
        {
            return _value;
        }
    }

    private ModuleHtmlView _view;
    private Exception _exception;

    public SimpleAction(Resource r)
    {
        try
        {
            _view = new ModuleHtmlView(r);
        }
        catch(Exception e)
        {
            //hold onto it so we can show it during handleRequest()
            _exception = e;
        }
    }

    protected String getCommandClassMethodName()
    {
        return null;
    }

    public ModelAndView handleRequest() throws Exception
    {
        //throw any previously-stored exception
        if (null != _exception)
            throw _exception;

        //override page template if view requests
        if (null != _view.getPageTemplate())
            getPageConfig().setTemplate(_view.getPageTemplate());

        return _view;
    }

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

        if (null != _view && _view.isRequiresLogin() && user.isGuest())
            throw new UnauthorizedException("You must sign in to see this content.");

        if (null != _view)
        {
            // Handle old-style permission bits, for backward compatibility
            int perm = _view.getRequiredPerms();
            Set<Class<? extends Permission>> perms = new HashSet<>();

            if ((perm & ACL.PERM_READ) > 0 || (perm & ACL.PERM_READOWN) > 0)
                perms.add(ReadPermission.class);
            if ((perm & ACL.PERM_INSERT) > 0)
                perms.add(InsertPermission.class);
            if ((perm & ACL.PERM_UPDATE) > 0 || (perm & ACL.PERM_UPDATEOWN) > 0)
                perms.add(UpdatePermission.class);
            if ((perm & ACL.PERM_DELETE) > 0 || (perm & ACL.PERM_DELETEOWN) > 0)
                perms.add(DeletePermission.class);
            if ((perm & ACL.PERM_ADMIN) > 0)
                perms.add(AdminPermission.class);

            if (!container.hasPermissions(user, perms))
            {
                if (container.isForbiddenProject(user))
                    throw new ForbiddenProjectException();
                else
                    throw new UnauthorizedException("You do not have permission to view this content.");
            }
        }

        if (null != _view && !_view.getRequiredPermissionClasses().isEmpty())
        {
            for (Class<? extends Permission> perm : _view.getRequiredPermissionClasses())
            {
                if (!container.hasPermission(user, perm))
                {
                    if (container.isForbiddenProject(user))
                        throw new ForbiddenProjectException();
                    else
                        throw new UnauthorizedException("You do not have permission to view this content.");
                }
            }
        }
        
        if (!getViewContext().hasAgreedToTermsOfUse())
            throw new TermsOfUseException();
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return root.addChild(_view.getTitle());
    }
}
