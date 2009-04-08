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
package org.labkey.api.view;

import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.PageFlowControllerFIXED;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionMessage;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Ownable;
import org.labkey.api.security.*;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.action.SpringActionController;
import org.labkey.common.util.Pair;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Mar 20, 2005
 * Time: 1:26:34 PM
 * 
 * @deprecated use SpringActionController
 */
@Deprecated
public class ViewController extends PageFlowControllerFIXED implements Controller
{
    static private final Logger _log = Logger.getLogger(ViewController.class);
    HttpView _view = null;
    ViewContext _context = null;

    protected HttpView getView()
    {
        return _view;
    }

    protected Forward includeView(ModelAndView v) throws Exception
    {
        getView().include(v);
        return null;
    }

    protected ViewContext getViewContext()
    {
        return _context;
    }

    protected boolean addError(String error)
    {
        PageFlowUtil.getActionErrors(getRequest(), true).add("main", new ActionMessage("Error", error));
        return true;
    }

    protected boolean addError(Throwable t)
    {
        _log.error("error", t);
        ExceptionUtil.logExceptionToMothership(getRequest(), t);
        return addError("An exception occurred: " + t);
    }


    private static class RequiredPermissions
    {
        private int perm;
        private int group;

        RequiredPermissions(int perm, int group)
        {
            if (!(group == Group.groupGuests || group == Group.groupUsers || group == Group.groupAdministrators))
                throw new IllegalArgumentException();
            if ((perm & ACL.PERM_DENYALL) != 0)
                throw new IllegalArgumentException();
            this.perm = perm;
            this.group = group;
        }

        public void test(Container c, User user) throws ServletException
        {
            boolean isAdmin = user.isAdministrator();

            if (group == Group.groupAdministrators && !isAdmin || group == Group.groupUsers && user.isGuest())
                HttpView.throwUnauthorized();

            // emulate requiresAdmin() behavior, SiteAdministrators always have Admin permission
            if (perm != ACL.PERM_NONE)
            {
                int granted = c.getAcl().getPermissions(user);
                if (isAdmin)
                    granted |= ACL.PERM_ADMIN;
                if (granted != (granted | perm))
                    HttpView.throwUnauthorized();
            }
        }
    }


    private static final Map<Class, Map<String, RequiredPermissions>> _permissionMaps = new HashMap<Class, Map<String, RequiredPermissions>>();

    private static final RequiredPermissions NOT_ANNOTATED = new RequiredPermissions(ACL.PERM_NONE, Group.groupGuests);
    private static Map<String, RequiredPermissions> getPermissionMap(Class<? extends ViewController> controllerClass)
    {
        synchronized (_permissionMaps)
        {
            Map<String, RequiredPermissions> permMap = _permissionMaps.get(controllerClass);
            StringBuilder sbNoAnnotation = new StringBuilder();
            boolean hasRequiresAnnotations = false;

            // CONSIDER: have module loader do this, since it has a list of controllers
            if (null == permMap)
            {
                permMap = new HashMap<String,RequiredPermissions>();
                for (Class c = controllerClass ; c != ViewController.class ; c = c.getSuperclass())
                {
                    for (Method m : controllerClass.getDeclaredMethods())
                    {
                        Jpf.Action jpfAction = m.getAnnotation(Jpf.Action.class);
                        RequiresPermission requiresPermission = m.getAnnotation(RequiresPermission.class);
                        RequiresSiteAdmin requiresSiteAdmin = m.getAnnotation(RequiresSiteAdmin.class);

                        if (null == jpfAction)
                        {
                            if (null != requiresPermission)
                                throw new IllegalStateException("@RequiresPermission is valid only on actions: " + controllerClass.getClass().getName() + "." + m.getName() + "()");
                            if (null != requiresSiteAdmin)
                                throw new IllegalStateException("@RequiresSiteAdmin is valid only on actions: " + controllerClass.getClass().getName() + "." + m.getName() + "()");
                            continue;
                        }
                        if (permMap.containsKey(m.getName()))
                            continue;

                        if (requiresPermission == null && requiresSiteAdmin == null)
                            sbNoAnnotation.append(m.getName()).append(" ");
                        else
                            hasRequiresAnnotations = true;

                        if (hasRequiresAnnotations)
                            permMap.put(m.getName(), new RequiredPermissions(
                                requiresPermission == null ? ACL.PERM_NONE : requiresPermission.value(),
                                requiresSiteAdmin == null ? Group.groupGuests : Group.groupAdministrators));
                        else
                            permMap.put(m.getName(), NOT_ANNOTATED);
                    }
                }
                
                _permissionMaps.put(controllerClass, permMap);

                // enforce that all methods have annotation (or none do)
                if (hasRequiresAnnotations && sbNoAnnotation.length() > 0)
                    throw new IllegalStateException(controllerClass.getName() + ": some actions do not have @RequiresPermission annotation: " + sbNoAnnotation.toString());
            }
            return permMap;
        }
    }


    public static void checkRequiredPermissions(User user, Container c, Class<? extends ViewController> controllerClass, String action) throws ServletException
    {
        RequiredPermissions required = getPermissionMap(controllerClass).get(action);
        if (null == required)
            return;
        if (null == c)
            HttpView.throwNotFound();
        required.test(c, user);
    }


    @Override
    protected synchronized void beforeAction() throws Exception
    {
        HttpView view = HttpView.currentView();
        ViewContext context = view.getViewContext();

        // Beehive might be calling an action other than the user's requested action
        // in the case of validation failure for instance.

        String urlAction = context.getActionURL().getAction();
        String currentAction = getActionMapping().getPath().substring(1);

        checkRequiredPermissions(context.getUser(), context.getContainer(), this.getClass(), urlAction);
        if (!urlAction.equals(currentAction))
            checkRequiredPermissions(context.getUser(), context.getContainer(), this.getClass(), currentAction);
        if (NOT_ANNOTATED != getPermissionMap(this.getClass()).get(urlAction))
            requiresTermsOfUse();

        _view = view;
        _context = context;
    }


    @Override
    protected synchronized void afterAction() throws Exception
    {
        super.afterAction();
        _view = null;
        _context = null;
    }

    public Container getContainer() throws ServletException
    {
        Container c = getViewContext().getContainer();
        if (null == c)
            HttpView.throwNotFound();
        return c;
    }

    protected Container getContainer(int perm) throws ServletException
    {
        return getViewContext().getContainer(perm);
    }

    public ActionURL getActionURL()
    {
        return getViewContext().getActionURL();
    }

    public ActionURL cloneActionURL()
    {
        return getViewContext().cloneActionURL();
    }

    /**
     * Returns a string version of a URL with ONLY the parameters specified here.
     * Useful for creating a substitution string to be used in dataregions.
     *
     * @param action    Action within the current controller to redirect to
     * @param newParams Params to replace.
     * @return url
     */
    protected String getActionURLString(String action, Pair[] newParams)
    {
        return getActionURL(action, newParams, true).getLocalURIString();
    }

    protected ActionURL getActionURL(String action, Pair[] newParams, boolean deleteOldParams)
    {
        ActionURL urlhelp = cloneActionURL();
        urlhelp.setAction(action);
        if (deleteOldParams)
            urlhelp.deleteParameters();

        if (null != newParams)
        {
            for (Pair p : newParams)
            {
                if (null == p.getKey())
                    continue;
                urlhelp.replaceParameter(_toString(p.getKey()), _toString(p.getValue()));
            }
        }

        return urlhelp;
    }


    public User getUser()
    {
        return getViewContext().getUser();
    }


    protected Forward renderInTemplate(HttpView view, Container c, NavTrailConfig config) throws Exception
    {
        HomeTemplate template = new HomeTemplate(getViewContext(), c, view, config);
        includeView(template);
        return null;
    }


    protected Forward renderInTemplate(HttpView view, Container c, String title) throws Exception
    {
        NavTrailConfig config = new NavTrailConfig(getViewContext());
        config.setTitle(title);
        return renderInTemplate(view, c, config);
    }


    private String _toString(Object o)
    {
        return null == o ? "" : String.valueOf(o);
    }


    protected void requiresLogin() throws ServletException
    {
        if (getUser().isGuest())
            HttpView.throwUnauthorized();
    }


    protected void requiresGlobalAdmin() throws ServletException
    {
        if (!getUser().isAdministrator())
            HttpView.throwUnauthorized();
    }


    protected void requiresPermission(int perm) throws ServletException
    {
        getViewContext().requiresPermission(perm);
    }

    private void requiresTermsOfUse() throws ServletException
    {
        ViewContext viewContext = getViewContext();
        if (viewContext == null)
        {
            viewContext = HttpView.currentContext();
        }
        viewContext.requiresTermsOfUse();
    }


    protected void requiresUpdatePermission(Ownable ownable) throws ServletException
    {
        Container c = getContainer();
        getViewContext().requiresTermsOfUse();
        if (c.hasPermission(getUser(), ACL.PERM_UPDATE))
        {
            return;
        }
        if (c.hasPermission(getUser(), ACL.PERM_UPDATEOWN) && ownable != null)
        {
            if (!getUser().isGuest() && ownable.getCreatedBy() == getUser().getUserId())
            {
                return;
            }
        }
        HttpView.throwUnauthorized();
    }


    protected boolean checkDeletePermission(Ownable ownable) throws ServletException
    {
        Container c = getContainer();
        if (c == null)
        {
            HttpView.throwUnauthorized();
            return false;
        }
        if (c.hasPermission(getUser(), ACL.PERM_DELETE))
        {
            return true;
        }
        if (c.hasPermission(getUser(), ACL.PERM_DELETEOWN) && ownable != null)
        {
            if (!getUser().isGuest() && ownable.getCreatedBy() == getUser().getUserId())
            {
                return true;
            }
        }
        return false;
    }


    protected void requiresDeletePermission(Ownable ownable) throws ServletException
    {
        if (!checkDeletePermission(ownable))
        {
            HttpView.throwUnauthorized();
        }
        requiresTermsOfUse();
    }


    protected void requiresPermission(int perm, Container container) throws ServletException
    {
        requiresPermission(perm, container.getId());
    }

    /**
     * Check to make sure we are in the container we should be in AND user has permission
     *
     * @param perm              Permission required by the user
     * @param objectContainerId Container for the object we're trying to show
     * @throws UnauthorizedException if insufficient permissions, NotFoundException if no such container, TermsOfUseException if required and not yet accepted
     */
    protected void requiresPermission(int perm, String objectContainerId) throws ServletException
    {
        if (objectContainerId == null)
        {
            HttpView.throwNotFound("Object not found");
            return;
        }
        if (objectContainerId.equals(getContainer().getId()))
        {
            requiresPermission(perm);
            return;
        }

        Container c = ContainerManager.getForId(objectContainerId);
        if (null == c)
        {
            HttpView.throwNotFound();
            return;
        }

        if (!c.hasPermission(getUser(), perm))
            HttpView.throwUnauthorized();

        //Now fix up the path so user ends up in right location
        ActionURL helper = cloneActionURL();
        helper.setContainer(c);
        HttpView.throwRedirect(helper.getLocalURIString());
    }

    protected void requiresAdmin() throws ServletException
    {
        if (!getUser().isAdministrator())
            requiresPermission(ACL.PERM_ADMIN);
    }

    protected void requiresAdmin(String objectContainerId) throws ServletException
    {
        if (!getUser().isAdministrator())
            requiresPermission(ACL.PERM_ADMIN, objectContainerId);
    }

    public boolean isPost()
    {
        return "POST".equalsIgnoreCase(getRequest().getMethod());
    }

    protected Forward sendAjaxCompletions(List<AjaxCompletion> completions) throws IOException
    {
        PageFlowUtil.sendAjaxCompletions(getResponse(), completions);
        return null;
    }

    protected Forward streamBytes(byte[] bytes, String type, long expires) throws IOException
    {
        if (bytes == null)
            return null;
        HttpServletResponse response = getResponse();
        response.setDateHeader("Expires", expires);
        response.setContentType(type);
        response.getOutputStream().write(bytes);
        return null;
    }

    @Deprecated
    protected ActionURL urlFor(Enum action)
    {
        try
        {
            return getContainer().urlFor(action);
        }
        catch (ServletException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        ActionURL redirectURL = SpringActionController.getUpgradeMaintenanceRedirect(request, null);
        if (null != redirectURL)
        {
            response.sendRedirect(redirectURL.toString());
            return null;
        }

        String pageFlow = getClass().getPackage().getName().replace('.', '/');
        String dispatchUrl = "/" + pageFlow + "/" + HttpView.currentContext().getActionURL().getAction() + ".do";
        RequestDispatcher r = request.getRequestDispatcher(dispatchUrl);
        r.forward(request, response);
        return null;
    }
}
