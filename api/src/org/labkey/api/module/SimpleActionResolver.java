package org.labkey.api.module;

import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.SpringActionController.ActionResolver;
import org.labkey.api.action.SpringActionController.ActionDescriptor;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.ActionNames;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Nick A
 * Date: Apr 8, 2010
 * Time: 12:09:52 PM
 *
 */

public class SimpleActionResolver implements ActionResolver
{
    public static final String VIEWS_DIRECTORY = "views";
    SimpleAction _sa = null;
    SimpleActionDescriptor _sad = null;

    public Controller resolveActionName(Controller actionController, String actionName)
    {
        ViewContext ctx = HttpView.getRootContext();
        String controllerName = ctx.getActionURL().getPageFlow();
        Module module = ModuleLoader.getInstance().getModuleForPageFlow(controllerName);

        Resource r = (module == null) ? null : module.getModuleResource("/" + VIEWS_DIRECTORY + "/" + actionName + ModuleHtmlViewDefinition.HTML_VIEW_EXTENSION);
        if (r != null)
        {
            // Register a ActionDescriptor for Simple
            // if action not registered -- add it
            /*
            if(_sa == null)
            {
                _sa = new SimpleAction(r);
                _sad = new SimpleActionDescriptor(_sa);
                SpringActionController.registerAction(_sad);
            }
            return _sa;
            */
            _sa = new SimpleAction(r);
            SpringActionController.registerAction(new SimpleActionDescriptor(_sa));
            return _sa;
        }

        return null;
    }

    public void addTime(Controller action, long elapsedTime)
    {
        if(_sad != null)
            _sad.addTime(elapsedTime);
        //SpringActionController.getActionDescriptor(action.getClass()).addTime(elapsedTime);
    }


    public Collection<ActionDescriptor> getActionDescriptors()
    {
        //return Collections.emptyList();
        if(_sad == null)
            return Collections.emptyList();

        return Collections.emptyList();
    }

    private class SimpleActionDescriptor implements ActionDescriptor
    {
        private final BaseViewAction _action;

        private long _count = 0;
        private long _elapsedTime = 0;
        private long _maxTime = 0;

        // Takes an instance rather than a class as arg.
        private SimpleActionDescriptor(BaseViewAction action)
        {
            // We want to return an instance of the SimpleActionDescriptor in this case (not the class)
            // Doesn't initialize any of the things the 'DefaultActionDescriptor' does.
            _action = action;
        }

        public Constructor getConstructor()
        {
            // For sake of interface.
            return null;
        }

        public Class<? extends Controller> getActionClass()
        {
            return _action.getClass();
        }

        synchronized public void addTime(long time)
        {
            _count++;
            _elapsedTime += time;

            if (time > _maxTime)
                _maxTime = time;
        }

        public String getPageFlow()
        {
            return null;
        }

        public String getPrimaryName()
        {
            return null;
        }

        synchronized public SpringActionController.ActionStats getStats()
        {
            return new DefaultActionStats(_count, _elapsedTime, _maxTime);
        }

        public List<String> getAllNames()
        {
            return null;
        }

        // Immutable stats holder to eliminate external synchronization needs
        private class DefaultActionStats implements SpringActionController.ActionStats
        {
            private final long _count;
            private final long _elapsedTime;
            private final long _maxTime;

            private DefaultActionStats(long count, long elapsedTime, long maxTime)
            {
                _count = count;
                _elapsedTime = elapsedTime;
                _maxTime = maxTime;
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
        }

    }
}
