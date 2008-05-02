package org.labkey.api.action;

import org.apache.beehive.netui.pageflow.Forward;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.*;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PrintTemplate;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MultipartException;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 17, 2007
 * Time: 10:17:36 AM
 *
 * CONSIDER using DispatchServlet instead of Controller here, or perhaps make the Module expose a DispatchServlet
 *
 * This class acts pretty much as DispatchServlet.  However, it does not follow all the rules/conventions of DispatchServlet.
 * Whenever a discrepency is found that someone cares about, please go ahead and make a change in the direction of better
 * compatibility. 
 */
public abstract class SpringActionController implements Controller, HasViewContext, ViewResolver
{
    // This is a prefix to indicate that a field is present on a form
    // For instance for checkboxes (with checkbox name = 'myField' use hidden name="@myField" 
    public static final String FIELD_MARKER = "@";

    // common error codes
    public static final String ERROR_MSG = null;
    public static final String ERROR_CONVERSION = "typeMismatch";
    public static final String ERROR_REQUIRED = "requiredError";
    public static final String ERROR_UNIQUE = "uniqueConstraint";

    private static Map<Class<? extends Controller>, ActionDescriptor> _classToDescriptor = new HashMap<Class<? extends Controller>, ActionDescriptor>();

    static Logger _log = Logger.getLogger(SpringActionController.class);

    public void setActionResolver(ActionResolver actionResolver)
    {
        _actionResolver = actionResolver;
    }

    public ActionResolver getActionResolver()
    {
        return _actionResolver;
    }

    private static void registerAction(ActionDescriptor ad)
    {
        _classToDescriptor.put(ad.getActionClass(), ad);
    }

    private static ActionDescriptor getActionDescriptor(Class<? extends Controller> actionClass)
    {
        ActionDescriptor ad = _classToDescriptor.get(actionClass);

        if (null == ad)
        {
            // Should add a marker object to the map and just throw on null
            if ("BeehiveForwardingAction".equals(actionClass.getSimpleName()))
                return null;
            else
                throw new IllegalStateException("Action class '" + actionClass + "' has not been registered with a controller");
        }

        return ad;
    }

    public static String getPageFlowName(Class<? extends Controller> actionClass)
    {
        return getActionDescriptor(actionClass).getPageFlow();
    }

    public static String getActionName(Class<? extends Controller> actionClass)
    {
        return getActionDescriptor(actionClass).getPrimaryName();
    }

    // I don't think there is an interface for this
    public interface ActionResolver
    {
        Controller resolveActionName(Controller actionController, String actionName);
        void addTime(Controller action, long elapsedTime);
        Set<ActionDescriptor> getActionDescriptors();
    }

    public interface ActionDescriptor
    {
        String getPageFlow();
        String getPrimaryName();
        List<String> getAllNames();
        Class<? extends Controller> getActionClass();
        Constructor getConstructor();

        void addTime(long time);
        long getCount();
        long getElapsedTime();
        long getMaxTime();
    }


    WebApplicationContext _webApplicationContext;
    ViewResolver _viewResolver = null;
    ActionResolver _actionResolver;
    ViewContext _viewContext;


    public SpringActionController()
    {
    }

    public SpringActionController(WebApplicationContext wac)
    {
        setWebApplicationContext(wac);
    }

    public WebApplicationContext getWebApplicationContext()
    {
        return _webApplicationContext;
    }

    public void setWebApplicationContext(WebApplicationContext webApplicationContext)
    {
        _webApplicationContext = webApplicationContext;
    }

    public void setViewContext(ViewContext context)
    {
        _viewContext = new ViewContext(context);
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
            HttpView.throwUnauthorized();
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
        return page;
    }

    void setViewResolver(ViewResolver v)
    {
        _viewResolver = v;
    }


    public View resolveViewName(String viewName, Locale locale) throws Exception
    {
        View v;

        if (null != _viewResolver)
        {
            v = _viewResolver.resolveViewName(viewName, locale);
            if (null != v)
                return v;
        }

        if (null != _webApplicationContext)
        {
            ViewResolver resolver = (ViewResolver)_webApplicationContext.getBean("viewResolver");
            v = resolver.resolveViewName(viewName, locale);
            if (null != v)
                return v;
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

/* We should probably try using the webApplicationContext to try to look up actions here.
           Something like this...
        if (null == c)
            WebApplicationContext wac = _webApplicationContext;
            if (null != wac)
            {
                Controller c = wac.getBean(this.getName() + "." + name, Controller.class);
                if (null != c)
                    return c;
            }
*/
        return c;
   }


    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws MultipartException
    {
        request.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, getWebApplicationContext());
        _viewContext.setWebApplicationContext(_webApplicationContext);

        Throwable throwable = null;

        String contentType = request.getContentType();
        if (null != contentType && contentType.startsWith("multipart"))
            request = (new CommonsMultipartResolver()).resolveMultipart(request);

        ViewContext context = getViewContext();
        context.setRequest(request);
        context.setResponse(response);

        ActionURL url = context.getActionURL();
        long startTime = System.currentTimeMillis();
        Controller action = null;

        try
        {
            action = resolveAction(url.getAction());
            if (null == action)
            {
                HttpView.throwNotFound();
                return null;
            }

            PageConfig pageConfig = defaultPageConfig();

            if (action instanceof HasViewContext)
                ((HasViewContext)action).setViewContext(context);
            if (action instanceof HasPageConfig)
                ((HasPageConfig)action).setPageConfig(pageConfig);

            if (action instanceof PermissionCheckable)
            {
                ((PermissionCheckable)action).checkPermissions();
            }
            else
            {
                BaseViewAction.checkPermissionsAndTermsOfUse(action.getClass(), context);
            }

            beforeAction(action);
            ModelAndView mv = action.handleRequest(request, response);
            if (mv != null)
            {
                if (mv.getView() instanceof RedirectView)
                {
                    // treat same as a throw redirect
                    HttpView.throwRedirect(((RedirectView)mv.getView()).getUrl());
                }
                renderInTemplate(context, action, pageConfig, mv);
            }
        }
        catch (HttpRequestMethodNotSupportedException x)
        {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            throwable = x;
        }
        catch (Throwable x)
        {
            Forward f = ExceptionUtil.handleException(request, response, x, null, false);
            if (null != f)
                ExceptionUtil.doErrorRedirect(response, f.toString());
            throwable = x;
        }
        finally
        {
            afterAction(throwable);

            if (null != action)
                _actionResolver.addTime(action, System.currentTimeMillis() - startTime);
        }
        return null;
    }

    protected void renderInTemplate(ViewContext context, Controller action, PageConfig page, ModelAndView mv)
            throws Exception
    {
        View view = resolveView(mv);
        mv.setView(view);

        ModelAndView template = getTemplate(context, mv, action, page);

        ModelAndView render = template == null ? mv : template;
        render.getView().render(render.getModel(), context.getRequest(), context.getResponse());
    }


    protected ModelAndView getTemplate(ViewContext context, ModelAndView mv, Controller action, PageConfig page)
    {
        switch (page.getTemplate())
        {
        case None:
        {
            return null;
        }
        case Print:
        {
            PrintTemplate template = new PrintTemplate(mv, page);
            return template;
        }
        case Dialog:
        {
            DialogTemplate template = new DialogTemplate(mv, page);
            return template;
        }
        case Home:
        case Fast:
        default:
        {
            NavTree root = new NavTree();
            appendNavTrail(action, root);
            NavTree[] children = root.getChildren();
            if (children.length > 0 && page.getTitle() == null)
                page.setTitle(children[children.length-1].getKey());
// UNDONE            
//            if (page.getTemplate() == PageConfig.Template.Fast)
//                template = new FastTemplate(context, context.getContainer(), mv, page, root.getChildren());
//            else
            HomeTemplate template = new HomeTemplate(context, context.getContainer(), mv, page, root.getChildren());
            return template;
        }
        }
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
                throw new ServletException("Could not resolve view with name '" + mv.getViewName() + " in controller " + this.getClass().getName());
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


    protected void beforeAction(Controller action)
    {
    }


    protected void afterAction(Throwable t)
    {
    }


    public static class DefaultActionResolver implements ActionResolver
    {
        private final Class _outerClass;
        private String _pageFlowName;
        private Map<String, ActionDescriptor> _nameToDescriptor = new HashMap<String, ActionDescriptor>();

        public DefaultActionResolver(Class outerClass, Class<? extends Controller>... otherClasses)
        {
            _outerClass = outerClass;
            _pageFlowName = ViewServlet.getPageFlowName(_outerClass);

            // firstly, add all shared actions.
            addInnerClassActions(SharedActions.class);

            // Add all concrete inner classes of this controller
            addInnerClassActions(_outerClass);

            // Add all actions that were passed in
            for (Class<? extends Controller> actionClass : otherClasses)
                addAction(actionClass);

            _nameToDescriptor = Collections.unmodifiableMap(_nameToDescriptor);
        }

        private void addInnerClassActions(Class outerClass)
        {
            Class[] innerClasses = outerClass.getDeclaredClasses();

            for (Class innerClass : innerClasses)
                if (Controller.class.isAssignableFrom(innerClass) && !Modifier.isAbstract(innerClass.getModifiers()))
                    addAction(innerClass);
        }

        private void addAction(Class<? extends Controller> actionClass)
        {
            ActionDescriptor ad = new DefaultActionDescriptor(actionClass);

            for (String name : ad.getAllNames())
                _nameToDescriptor.put(name, ad);

            registerAction(ad);
        }


        public Controller resolveActionName(Controller actionController, String name)
        {
            ActionDescriptor ad =  _nameToDescriptor.get(name);

            if (ad == null)
                return null;

            Constructor con = ad.getConstructor();

            try
            {
                if (con.getParameterTypes().length == 1)
                    return (Controller)con.newInstance(actionController);
                else
                    return (Controller)con.newInstance();
            }
            catch (IllegalAccessException e)
            {
                _log.error("unexpected error", e);
                throw new RuntimeException(e);
            }
            catch (InstantiationException e)
            {
                _log.error("unexpected error", e);
                throw new RuntimeException(e);
            }
            catch (InvocationTargetException e)
            {
                _log.error("unexpected error", e);
                throw new RuntimeException(e);
            }
        }


        public void addTime(Controller action, long elapsedTime)
        {
            ActionDescriptor ad = getActionDescriptor(action.getClass());

            // TODO: BeehiveForwardingAction?  Use marker object instead of null
            if (null == ad)
                return;

            synchronized(ad)
            {
                ad.addTime(elapsedTime);
            }
        }


        private class DefaultActionDescriptor implements ActionDescriptor
        {
            private Class<? extends Controller> _actionClass;
            private Constructor _con;
            private String _primaryName;
            private List<String> _allNames;
            private long _count = 0;
            private long _elapsedTime = 0;
            private long _maxTime = 0;

            private DefaultActionDescriptor(Class<? extends Controller> actionClass)
            {
                _actionClass = actionClass;

                // @ActionNames("name1, name2") annotation overrides default behavior of using class name to generate name
                ActionNames actionNames = actionClass.getAnnotation(ActionNames.class);

                if (null != actionNames)
                    initializeNames(actionNames.value().split(","));
                else
                    initializeNames(getDefaultActionName());

                if (_outerClass != null)
                {
                    try
                    {
                        _con = actionClass.getConstructor(_outerClass);
                    }
                    catch (NoSuchMethodException x)
                    {
                        /* */
                    }
                }

                try
                {
                    if (null == _con)
                        _con = actionClass.getConstructor();
                }
                catch (NoSuchMethodException x)
                {
                    throw new RuntimeException(x);
                }
            }

            private void initializeNames(String... names)
            {
                _allNames = new ArrayList<String>(names.length);
                for (String name : names)
                    _allNames.add(name.trim());
                _primaryName = _allNames.get(0);
            }

            public Class<? extends Controller> getActionClass()
            {
                return _actionClass;
            }

            public Constructor getConstructor()
            {
                return _con;
            }

            public String getPageFlow()
            {
                return _pageFlowName;
            }

            public String getPrimaryName()
            {
                return _primaryName;
            }

            public List<String> getAllNames()
            {
                return _allNames;
            }

            // Externally synchronized
            public void addTime(long time)
            {
                _count++;
                _elapsedTime += time;

                if (time > _maxTime)
                    _maxTime = time;
            }

            public long getCount()
            {
                return _count;
            }

            public long getElapsedTime()
            {
                return _elapsedTime;
            }

            public long getMaxTime()
            {
                return _maxTime;
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

        // Returns the live values.  No synchronization is needed on the map itself (it's completely initialized when
        // the controller class loads) but callers will want to synchronize on each ActionDescriptor before inspecting
        // or displaying statistics
        public Set<ActionDescriptor> getActionDescriptors()
        {
            return new HashSet<ActionDescriptor>(_nameToDescriptor.values());
        }
    }
}
