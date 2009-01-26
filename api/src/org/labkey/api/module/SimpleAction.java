/*
 * Copyright (c) 2009 LabKey Corporation
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
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.util.DOMUtil;
import org.labkey.api.util.Cache;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.Errors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

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
    public static WebPartView getModuleHtmlView(Module module, String viewName) throws IOException
    {
        File viewFile = new File(new File(module.getExplodedPath(), SimpleController.VIEWS_DIRECTORY), viewName + ModuleHtmlView.HTML_VIEW_EXTENSION);
        if(viewFile.exists() && viewFile.isFile())
            return new ModuleHtmlView(viewFile);
        else
            return null;
    }

    public enum Permission
    {
        login(0),
        read(ACL.PERM_READ),
        insert(ACL.PERM_INSERT),
        update(ACL.PERM_UPDATE),
        delete(ACL.PERM_DELETE),
        admin(ACL.PERM_ADMIN);

        private int _value = 0;

        Permission(int value)
        {
            _value = value;
        }

        public int toInt()
        {
            return _value;
        }
    }

    private Logger _log = Logger.getLogger(SimpleAction.class);
    private ModuleHtmlView _view;
    private Exception _exception;

    public SimpleAction(File viewFile)
    {
        try
        {
            _view = getHtmlView(viewFile);
        }
        catch(Exception e)
        {
            _log.error("Unable to load the file-based HTML view " + viewFile.getAbsolutePath(), e);
            //store execption so we can throw it from handleRequest
            _exception = e;
        }
    }

    public static ModuleHtmlView getHtmlView(File viewFile) throws IOException
    {
        //consider caching?
        return new ModuleHtmlView(viewFile);
    }

    protected String getCommandClassMethodName()
    {
        return null;
    }

    public ModelAndView handleRequest() throws Exception
    {
        //throw any previously-stored exception
        if(null != _exception)
            throw _exception;

        //override page template if view requests
        if(null != _view.getPageTemplate())
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
    public void checkPermissions() throws TermsOfUseException, UnauthorizedException
    {
        User user = getViewContext().getUser();
        Container container = getViewContext().getContainer();

        if(_view.isRequiresLogin() && user.isGuest())
            throw new UnauthorizedException("You must sign in to see this content.");

        if (!container.hasPermission(user, _view.getRequiredPerms()))
        {
            if (container.isForbiddenProject(user))
                throw new ForbiddenProjectException();
            else
                throw new UnauthorizedException("You do not have permission to view this content.");
        }
        
        if(!getViewContext().hasAgreedToTermsOfUse())
            throw new TermsOfUseException();
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return root.addChild(_view.getTitle());
    }
}