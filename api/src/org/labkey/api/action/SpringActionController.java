/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.module.AllowedBeforeInitialUserIsSet;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SimpleAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.template.PageConfig;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * User: matthewb
 * Date: May 17, 2007
 *
 * CONSIDER using DispatchServlet instead of Controller here, or perhaps make the Module expose a DispatchServlet
 *
 * This class acts pretty much as DispatchServlet.  However, it does not follow all the rules/conventions of DispatchServlet.
 * Whenever a discrepancy is found that someone cares about, please go ahead and make a change in the direction of better
 * compatibility. 
 */
public abstract class SpringActionController implements Controller, HasViewContext, ViewResolver, ApplicationContextAware
{
    // This is a prefix to indicate that a field is present on a form
    // For instance for checkboxes (with checkbox name = 'myField' use hidden name="@myField"
    // if you change this, change labkey.js (LABKEY.fieldMarker) as well
    public static final String FIELD_MARKER = "@";

    // common error codes
    public static final String ERROR_MSG = null;
    /** Use this error code only when no further error message is available. */
    public static final String ERROR_GENERIC = "GenericError";
    public static final String ERROR_CONVERSION = "typeMismatch";
    public static final String ERROR_REQUIRED = "requiredError";
    public static final String ERROR_UNIQUE = "uniqueConstraint";

    private static final Map<Class<? extends Controller>, ActionDescriptor> _classToDescriptor = new HashMap<>();

    private static final Logger _log = Logger.getLogger(SpringActionController.class);

    public void setActionResolver(ActionResolver actionResolver)
    {
        _actionResolver = actionResolver;
    }

    public ActionResolver getActionResolver()
    {
        return _actionResolver;
    }

    protected static void registerAction(ActionDescriptor ad)
    {
        _classToDescriptor.put(ad.getActionClass(), ad);
    }

    @NotNull
    static ActionDescriptor getActionDescriptor(Class<? extends Controller> actionClass)
    {
        ActionDescriptor ad = _classToDescriptor.get(actionClass);

        if (null == ad)
            throw new IllegalStateException("Action class '" + actionClass + "' has not been registered with a controller");

        return ad;
    }

    public static String getControllerName(Class<? extends Controller> actionClass)
    {
        return getActionDescriptor(actionClass).getControllerName();
    }

    public static String getActionName(Class<? extends Controller> actionClass)
    {
        return getActionDescriptor(actionClass).getPrimaryName();
    }

    public static Collection<ActionDescriptor> getRegisteredActionDescriptors()
    {
        return new ArrayList<>(_classToDescriptor.values());
    }


    // I don't think there is an interface for this
    public interface ActionResolver
    {
        Controller resolveActionName(Controller actionController, String actionName);
        void addTime(Controller action, long elapsedTime);
        Collection<ActionDescriptor> getActionDescriptors();
    }

    public interface ActionDescriptor
    {
        String getControllerName();
        String getPrimaryName();
        List<String> getAllNames();
        Class<? extends Controller> getActionClass();
        Controller createController(Controller actionController);

        void addTime(long time);
        void addException(Exception x);
        ActionStats getStats();
    }

    public interface ActionStats
    {
        Class<? extends ActionType> getActionType();
        long getCount();
        long getElapsedTime();
        long getMaxTime();
        boolean hasExceptions();
        List<Exception> getExceptions();
    }

    ApplicationContext _applicationContext = null;
    ActionResolver _actionResolver;
    ViewContext _viewContext;


    public SpringActionController()
    {
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        _applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext()
    {
        return _applicationContext;
    }

    public void setViewContext(ViewContext context)
    {
        _viewContext = context;
    }

    public ViewContext getViewContext()
    {
        return _viewContext;
    }

    public boolean isPost()
    {
        return "POST".equalsIgnoreCase(getViewContext().getRequest().getMethod());
    }

    protected Container getContainer()
    {
        return getViewContext().getContainer();
    }

    protected User getUser()
    {
        return getViewContext().getUser();
    }

    protected void requiresLogin() throws ServletException
    {
        if (getUser().isGuest())
        {
            throw new UnauthorizedException();
        }
    }
    
    protected ViewBackgroundInfo getViewBackgroundInfo()
    {
        ViewContext vc = getViewContext();
        return new ViewBackgroundInfo(vc.getContainer(), vc.getUser(), vc.getActionURL());
    }

    public PageConfig defaultPageConfig()
    {
        PageConfig page = new PageConfig();

        HttpServletRequest request = getViewContext().getRequest();
        if (null != StringUtils.trimToNull(request.getParameter("_print")) ||
            null != StringUtils.trimToNull(request.getParameter("_print.x")))
            page.setTemplate(PageConfig.Template.Print);
        if (null != StringUtils.trimToNull(request.getParameter("_frame")) ||
            null != StringUtils.trimToNull(request.getParameter("_frame.x")))
            page.setTemplate(PageConfig.Template.Framed);
        if (null != StringUtils.trimToNull(request.getParameter("_template")))
        {
            try
            {
                PageConfig.Template template =
                        PageConfig.Template.valueOf(StringUtils.trimToNull(request.getParameter("_template")));
                page.setTemplate(template);
            }
            catch (IllegalArgumentException ex)
            {
                _log.debug("Illegal page template type", ex);
            }
        }

        // If admin has turned off LabKey searching of this folder then turn off external search bot indexing as well.
        // This should improve search results (e.g., Google will tend to point to latest version of labkey.org docs pages, instead of archived versions).
        if (!getViewContext().getContainer().isSearchable())
            page.setNoIndex();

        return page;
    }


    public View resolveViewName(String viewName, Locale locale) throws Exception
    {
        if (null != _applicationContext)
        {
            // WWSD (what would spring do)
        }

        return HttpView.viewFromString(viewName);
    }
    

    /** returns an uninitialized instance of the named action */
    public Controller resolveAction(String name)
    {
        name = StringUtils.trimToNull(name);
        if (null == name)
            return null;

        Controller c = null;
        if (null != _actionResolver)
            c = _actionResolver.resolveActionName(this, name);

        if (null != _applicationContext)
        {
            // WWSD (what would spring do)
        }
        return c;
   }


    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        request.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, getApplicationContext());
        _viewContext.setApplicationContext(_applicationContext);

        Throwable throwable = null;

        String contentType = request.getContentType();
        if (null != contentType && contentType.startsWith("multipart"))
        {
            request = (new CommonsMultipartResolver()).resolveMultipart(request);
            // ViewServlet doesn't check validChars for parameters in a multipart request, so check again
            if (!ViewServlet.validChars(request))
            {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Illegal characters in request body");
                return null;
            }
        }

        ViewContext context = getViewContext();
        context.setRequest(request);
        context.setResponse(response);

        ActionURL url = context.getActionURL();
        long startTime = System.currentTimeMillis();
        Controller controller = null;

        try
        {
            controller = resolveAction(url.getAction());
            if (null == controller)
            {
                throw new NotFoundException("Unable to find action '" + url.getAction() + "' to handle request in controller '" + url.getController() + "'");
            }

            if (!(controller instanceof PermissionCheckable))
                throw new IllegalStateException("All actions must implement PermissionCheckable. " + controller.getClass().getName() + " should extend PermissionCheckableAction or one of its subclasses.");

            PermissionCheckable checkable = (PermissionCheckable)controller;

            ActionURL redirectURL = getUpgradeMaintenanceRedirect(request, controller);

            if (null != redirectURL)
            {
                _log.debug("URL " + url.toString() + " was redirected to " + redirectURL + " instead");
                response.sendRedirect(redirectURL.toString());
                return null;
            }

            // This container check used to be in checkPermissions(), but that meant actions with lenient permissions checking
            // (e.g., CustomStylesheetAction) failed to validate the container. We now proactively validate the container on
            // all view-based URLs. #21950
            Container c = context.getContainer();
            if (null == c)
            {
                String containerPath = context.getActionURL().getExtraPath();
                if (containerPath != null && containerPath.contains("/"))
                {
                    throw new NotFoundException("No such folder or workbook: " + containerPath);
                }
                else
                {
                    throw new NotFoundException("No such project: " + containerPath);
                }
            }

            PageConfig pageConfig = defaultPageConfig();

            if (controller instanceof HasViewContext)
                ((HasViewContext)controller).setViewContext(context);
            if (controller instanceof HasPageConfig)
                ((HasPageConfig)controller).setPageConfig(pageConfig);

            Class<? extends Controller> actionClass = controller.getClass();
            if (actionClass.isAnnotationPresent(IgnoresAllocationTracking.class) || "true".equals(request.getParameter("skip-profiling")))
            {
                MemTracker.get().ignore();
            }
            else
            {
                // Don't send back mini-profiler id if the user won't be able to get the profiler info
                if (MiniProfiler.isEnabled(context))
                {
                    RequestInfo req = MemTracker.get().current();
                    if (req != null)
                    {
                        LinkedHashSet<Long> ids = new LinkedHashSet<>();
                        ids.add(req.getId());
                        ids.addAll(MemTracker.get().getUnviewed(context.getUser()));

                        response.setHeader("X-MiniProfiler-Ids", ids.toString());
                    }
                }
            }

            checkable.checkPermissions();

            // Actions can annotate themselves with an ActionType, which helps custom schemas that want to limit access from generic actions
            if (actionClass.isAnnotationPresent(Action.class))
            {
                Action actionAnnotation = actionClass.getAnnotation(Action.class);
                QueryService.get().setEnvironment(QueryService.Environment.ACTION, actionAnnotation.value());
            }

            beforeAction(controller);
            ModelAndView mv = controller.handleRequest(request, response);
            if (mv != null)
            {
                if (mv.getView() instanceof RedirectView)
                {
                    // treat same as a throw redirect
                    throw new RedirectException(((RedirectView)mv.getView()).getUrl());
                }
                renderInTemplate(context, controller, pageConfig, mv);
            }
        }
        catch (HttpRequestMethodNotSupportedException x)
        {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            throwable = x;
        }
        catch (Throwable x)
        {
            handleException(request, response, x);
            throwable = x;
        }
        finally
        {
            afterAction(throwable);

            if (null != controller)
                _actionResolver.addTime(controller, System.currentTimeMillis() - startTime);
        }

        return null;
    }


    protected void handleException(HttpServletRequest request, HttpServletResponse response, Throwable x)
    {
        ActionURL errorURL = ExceptionUtil.handleException(request, response, x, null, false);
        if (null != errorURL)
            ExceptionUtil.doErrorRedirect(response, errorURL.toString());
    }


    public static ActionURL getUpgradeMaintenanceRedirect(HttpServletRequest request, Controller action)
    {
        if (UserManager.hasNoUsers())
        {
            // Let the "initial user" view & post, stylesheet & javascript actions, etc. through... otherwise redirect to initial user action
            if (null != action && action.getClass().isAnnotationPresent(AllowedBeforeInitialUserIsSet.class))
                return null;
            else
                return PageFlowUtil.urlProvider(LoginUrls.class).getInitialUserURL();
        }

        boolean upgradeRequired = ModuleLoader.getInstance().isUpgradeRequired();
        boolean startupComplete = ModuleLoader.getInstance().isStartupComplete();
        boolean maintenanceMode = AppProps.getInstance().isUserRequestedAdminOnlyMode();

        if (upgradeRequired || !startupComplete || maintenanceMode)
        {
            boolean actionIsAllowed = (null != action && action.getClass().isAnnotationPresent(AllowedDuringUpgrade.class));

            if (null != action)
                _log.debug("Action " + action.getClass() + " allowed: " + actionIsAllowed);

            if (!actionIsAllowed)
            {
                User user = (User)request.getUserPrincipal();

                // Don't redirect the indexer... let it get the page content, #12042 and #11345
                if (user.isSearchUser())
                    return null;

                URLHelper returnURL = null;
                try
                {
                    StringBuilder url = new StringBuilder(request.getRequestURL().toString());
                    if (request.getQueryString() != null)
                    {
                        url.append("?");
                        url.append(request.getQueryString());
                    }
                    returnURL = new URLHelper(url.toString());
                }
                catch (URISyntaxException e)
                {
                    // ignore
                }

                if (!user.isInSiteAdminGroup())
                {
                    if (isApiLike(request,action))
                    {
                        UnauthorizedException uae = new UnauthorizedException(upgradeRequired || !startupComplete ?
                                "server is not ready" :
                                "server is in admin-only mode");
                        uae.setType(UnauthorizedException.Type.sendBasicAuth);
                        throw uae;
                    }
                    return PageFlowUtil.urlProvider(AdminUrls.class).getMaintenanceURL(returnURL);
                }
                else if (upgradeRequired || !startupComplete)
                {
                    return PageFlowUtil.urlProvider(AdminUrls.class).getModuleStatusURL(returnURL);
                }
            }
        }

        return null;
    }

    private static boolean siteManagerExist() {
        return false;
    }

    protected void renderInTemplate(ViewContext context, Controller action, PageConfig page, ModelAndView mv)
            throws Exception
    {
        View view = resolveView(mv);
        mv.setView(view);

        if (mv instanceof HttpView)
            page.addClientDependencies(((HttpView)mv).getClientDependencies());

        ModelAndView template = getTemplate(context, mv, action, page);

        if (template instanceof HttpView)
            page.addClientDependencies(((HttpView)template).getClientDependencies());

        ModelAndView render = template == null ? mv : template;
        render.getView().render(render.getModel(), context.getRequest(), context.getResponse());
    }


    // Send plain text back to browser; useful for script responses, e.g.
    protected void sendPlainText(String message) throws IOException
    {
        HttpServletResponse response = getViewContext().getResponse();
        response.setContentType("text/plain");

        try (PrintWriter out = response.getWriter())
        {
            out.print(message);
        }

        response.flushBuffer();
    }


    protected ModelAndView getTemplate(ViewContext context, ModelAndView mv, Controller action, PageConfig page)
    {
        NavTree root = new NavTree();
        appendNavTrail(action, root);
        if (root.hasChildren())
        {
            List<NavTree> children = root.getChildren();
            page.setNavTrail(children);
            if (null == page.getTitle())
                page.setTitle(children.get(children.size() - 1).getText());
        }

        return page.getTemplate().getTemplate(context, mv, page);
    }


    View resolveView(ModelAndView mv) throws Exception
    {
        View view;
        if (mv.isReference())
        {
            // We need to resolve the view name.
            view = resolveViewName(mv.getViewName(), Locale.getDefault());

            if (view == null)
            {
                throw new ServletException("Could not resolve view with name '" + mv.getViewName() + "' in controller " + this.getClass().getName());
            }
        }
        else
        {
            // No need to lookup: the ModelAndView object contains the actual View object.
            view = mv.getView();
            if (view == null)
            {
                throw new ServletException("ModelAndView [" + mv + "] neither contains a view name nor a View object");
            }
        }
        return view;
    }



    protected void appendNavTrail(Controller action, NavTree root)
    {
        if (action instanceof NavTrailAction)
        {
            ((NavTrailAction)action).appendNavTrail(root);
        }
    }


    protected void beforeAction(Controller action) throws ServletException
    {
    }


    protected void afterAction(Throwable t)
    {
    }

    public static abstract class BaseActionDescriptor implements ActionDescriptor
    {
        private long _count = 0;
        private long _elapsedTime = 0;
        private long _maxTime = 0;
        private List<Exception> _exceptions = null;

        synchronized public void addTime(long time)
        {
            _count++;
            _elapsedTime += time;

            if (time > _maxTime)
                _maxTime = time;
        }

        @Override
        public void addException(Exception ex)
        {
            if (null == _exceptions)
                _exceptions = new ArrayList<>();
            if (_exceptions.size() < 20)
                _exceptions.add(ex);
        }

        synchronized public ActionStats getStats()
        {
            return new BaseActionStats(_count, _elapsedTime, _maxTime, _exceptions);
        }

        // Immutable stats holder to eliminate external synchronization needs
        private class BaseActionStats implements ActionStats
        {
            private final long _count;
            private final long _elapsedTime;
            private final long _maxTime;
            private final List<Exception> _exceptions;

            private BaseActionStats(long count, long elapsedTime, long maxTime, List<Exception> ex)
            {
                _count = count;
                _elapsedTime = elapsedTime;
                _maxTime = maxTime;
                _exceptions = ex;
            }

            @Override
            public long getCount()
            {
                return _count;
            }

            @Override
            public long getElapsedTime()
            {
                return _elapsedTime;
            }

            @Override
            public long getMaxTime()
            {
                return _maxTime;
            }

            @Override
            @Nullable
            public Class<? extends ActionType> getActionType()
            {
                ActionDescriptor desc = BaseActionDescriptor.this;
                Class<? extends Controller> actionClass = desc.getActionClass();
                Action actionAnnotation = actionClass.getAnnotation(Action.class);

                return  null != actionAnnotation ? actionAnnotation.value() : null;
            }

            @Override
            public boolean hasExceptions()
            {
                return null != _exceptions && !_exceptions.isEmpty();
            }

            @Override
            public List<Exception> getExceptions()
            {
                return null == _exceptions ? Collections.emptyList() : _exceptions;
            }
        }
    }

    public static class HTMLFileActionResolver implements ActionResolver
    {
        public static final String VIEWS_DIRECTORY = "views";

        private Map<String, ActionDescriptor> _nameToDescriptor;
        private final String _controllerName;

        public HTMLFileActionResolver(String controllerName)
        {
            _nameToDescriptor = new HashMap<>();
            _controllerName = controllerName;
        }

        public void addTime(Controller action, long elapsedTime)
        {
            /* Never called */
        }        

        public Controller resolveActionName(Controller actionController, String actionName)
        {
            if(_nameToDescriptor.get(actionName) != null)
            {
                return _nameToDescriptor.get(actionName).createController(actionController);
            }

            if (null == _controllerName)
                return null;
            Module module = ModuleLoader.getInstance().getModuleForController(_controllerName);
            if (null == module)
                return null;

            Path path = ModuleHtmlView.getStandardPath(actionName);
            if (!ModuleHtmlView.exists(module, path))
                return null;

            HTMLFileActionDescriptor htmlDescriptor = createFileActionDescriptor(module, actionName);
            _nameToDescriptor.put(actionName, htmlDescriptor);
            registerAction(htmlDescriptor);

            return htmlDescriptor.createController(actionController);
        }

        protected HTMLFileActionDescriptor createFileActionDescriptor(Module module, String actionName)
        {
            return new HTMLFileActionDescriptor(module, actionName);
        }

        protected class HTMLFileActionDescriptor extends BaseActionDescriptor
        {
            private final Module _module;
            private final String _primaryName;
            private final List<String> _allNames;

            protected HTMLFileActionDescriptor(Module module, String primaryName)
            {
                _module = module;
                _primaryName = primaryName;
                _allNames = Collections.singletonList(_primaryName);
            }

            public String getControllerName()
            {
                return _controllerName;
            }

            public String getPrimaryName()
            {
                return _primaryName;
            }

            public List<String> getAllNames()
            {
                return _allNames;
            }

            public Class<? extends Controller> getActionClass()
            {
                return SimpleAction.class;
            }

            public Controller createController(Controller actionController)
            {
                return new SimpleAction(_module, ModuleHtmlView.getStandardPath(getPrimaryName()));
            }
        }

        // WARNING: This might not be thread safe.
        public Collection<ActionDescriptor> getActionDescriptors()
        {
            return _nameToDescriptor.values();
        }
        
        public ActionDescriptor getActionDescriptor(String actionName)
        {
            return _nameToDescriptor.get(actionName);
        }
    }


    public static class DefaultActionResolver implements ActionResolver
    {
        private final Class<? extends Controller> _outerClass;
        private final String _controllerName;
        private final Map<String, ActionDescriptor> _nameToDescriptor;

        private HTMLFileActionResolver _htmlResolver;

        @SafeVarargs
        public DefaultActionResolver(Class<? extends Controller> outerClass, Class<? extends Controller>... otherClasses)
        {
            _outerClass = outerClass;
            _controllerName = ViewServlet.getControllerName(_outerClass);
            _htmlResolver = null; // This gets loaded if file-based actions are used.

            Map<String, ActionDescriptor> nameToDescriptor = new CaseInsensitiveHashMap<>();

            // Add all concrete inner classes of this controller
            addInnerClassActions(nameToDescriptor, _outerClass);

            // Add all actions that were passed in
            for (Class<? extends Controller> actionClass : otherClasses)
                addAction(nameToDescriptor, actionClass);

            _nameToDescriptor = nameToDescriptor;
        }

        private void addInnerClassActions(Map<String, ActionDescriptor> nameToDescriptor, Class<? extends Controller> outerClass)
        {
            Class[] innerClasses = outerClass.getDeclaredClasses();

            for (Class innerClass : innerClasses)
                if (Controller.class.isAssignableFrom(innerClass) && !Modifier.isAbstract(innerClass.getModifiers()))
                    addAction(nameToDescriptor, innerClass);
        }

        private void addAction(Map<String, ActionDescriptor> nameToDescriptor, Class<? extends Controller> actionClass)
        {
            try
            {
                ActionDescriptor ad = new DefaultActionDescriptor(actionClass);

                for (String name : ad.getAllNames())
                {
                    ActionDescriptor existingDescriptor = nameToDescriptor.put(name, ad);
                    if (existingDescriptor != null)
                    {
                        throw new IllegalStateException("Duplicate action name " + name + " registered for " + ad.getActionClass() + " and " + existingDescriptor.getActionClass());
                    }
                }

                registerAction(ad);
            }
            catch (Exception e)
            {
                // Too early to log to mothership
                _log.error("Exception while registering action class", e);
            }
        }


        public Controller resolveActionName(Controller actionController, String name)
        {
            ActionDescriptor ad;
            synchronized (_nameToDescriptor)
            {
                ad = _nameToDescriptor.get(name);
            }

            if (ad == null)
            {
                // Check if this action is described in the file-based action directory
                return resolveHTMLActionName(actionController, name);
            }

            return ad.createController(actionController);
        }


        public void addTime(Controller action, long elapsedTime)
        {
            getActionDescriptor(action.getClass()).addTime(elapsedTime);
        }


        private class DefaultActionDescriptor extends BaseActionDescriptor
        {
            private final Class<? extends Controller> _actionClass;
            private final Constructor _con;
            private final String _primaryName;
            private final List<String> _allNames;

            private DefaultActionDescriptor(Class<? extends Controller> actionClass) throws ServletException
            {
                if (actionClass.getConstructors().length == 0)
                    throw new ServletException(actionClass.getName() + " has no public constructors");

                _actionClass = actionClass;

                // @ActionNames("name1, name2") annotation overrides default behavior of using class name to generate name
                ActionNames actionNames = actionClass.getAnnotation(ActionNames.class);

                _allNames = (null != actionNames ? initializeNames(actionNames.value().split(",")) : initializeNames(getDefaultActionName()));
                _primaryName = _allNames.get(0);

                Constructor con = null;

                if (_outerClass != null)
                {
                    try
                    {
                        con = actionClass.getConstructor(_outerClass);
                    }
                    catch (NoSuchMethodException x)
                    {
                        /* */
                    }
                }

                try
                {
                    _con = (null != con ? con : actionClass.getConstructor());
                }
                catch (NoSuchMethodException x)
                {
                    throw new RuntimeException("Zero-argument constructor not found for " + actionClass.getName(), x);
                }
            }

            private List<String> initializeNames(String... names)
            {
                List<String> list = new ArrayList<>(names.length);
                for (String name : names)
                    list.add(name.trim());
                return list;
            }

            public Class<? extends Controller> getActionClass()
            {
                return _actionClass;
            }

            public Controller createController(Controller actionController)
            {
                try
                {
                    if (_con.getParameterTypes().length == 1)
                        return (Controller)_con.newInstance(actionController);
                    else
                        return (Controller)_con.newInstance();
                }
                catch (IllegalAccessException | InstantiationException | InvocationTargetException e)
                {
                    _log.error("unexpected error", e);
                    throw new RuntimeException(e);
                }

            }

            public String getControllerName()
            {
                return _controllerName;
            }

            public String getPrimaryName()
            {
                return _primaryName;
            }

            public List<String> getAllNames()
            {
                return _allNames;
            }

            private String getDefaultActionName()
            {
                String name = _actionClass.getName();
                name = name.substring(name.lastIndexOf(".")+1);
                name = name.substring(name.lastIndexOf("$")+1);
                if (name.endsWith("Action"))
                    name = name.substring(0,name.length()-"Action".length());
                if (name.endsWith("Controller"))
                    name = name.substring(0,name.length()-"Controller".length());
                name = name.substring(0,1).toLowerCase() + name.substring(1);

                return name;
            }
        }


        protected Controller resolveHTMLActionName(Controller actionController, String actionName)
        {
            if (_htmlResolver == null)
            {
                _htmlResolver = getHTMLFileActionResolver();
            }

            Controller thisActionsController = _htmlResolver.resolveActionName(actionController, actionName);

            if (thisActionsController != null)
            {
                synchronized (_nameToDescriptor)
                {
                    // The HTMLFileResolver registers the action
                    _nameToDescriptor.put(actionName, _htmlResolver.getActionDescriptor(actionName));
                }
            }

            return thisActionsController;
        }

        protected HTMLFileActionResolver getHTMLFileActionResolver()
        {
            return new HTMLFileActionResolver(_controllerName);
        }
        
        public Collection<ActionDescriptor> getActionDescriptors()
        {
            synchronized (_nameToDescriptor)
            {
                return new ArrayList<>(_nameToDescriptor.values());
            }
        }
    }


    // Check for cases that should not respond with a Redirect, used by getUpgradeMaintenanceRedirect()
    public static boolean isApiLike(HttpServletRequest request, Controller action)
    {
        boolean throwUnauthorized = StringUtils.equals("UNAUTHORIZED",request.getHeader("X-ONUNAUTHORIZED"));
        boolean xmlhttp = StringUtils.equals("XMLHttpRequest", request.getHeader("x-requested-with"));
        boolean json = StringUtils.startsWith(request.getHeader("Content-Type"), "application/json");
        boolean apiClass = action instanceof ApiAction;
        boolean r = StringUtils.equals(request.getHeader("User-Agent"),"Rlabkey");
        return throwUnauthorized || xmlhttp || json || apiClass || r;
    }
}
