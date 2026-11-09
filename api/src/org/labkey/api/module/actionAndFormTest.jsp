<%
/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
%>
<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.action.BaseViewAction" %>
<%@ page import="org.labkey.api.action.SpringActionController" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.module.SimpleAction" %>
<%@ page import="org.labkey.api.util.logging.LogHelper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ViewServlet" %>
<%@ page import="org.springframework.web.servlet.mvc.Controller" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Objects" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="java.lang.reflect.InvocationTargetException" %>
<%@ page extends="org.labkey.api.jsp.JspTest.DRT" %>

<%!
    private final Logger _log = LogHelper.getLogger(getClass(), "Testing of action and form classes");
    private final ViewContext _context = HttpView.currentContext();

    // Enumerate all registered actions, verifying that the actions and their associated forms can be instantiated
    @Test
    public void testActions() throws IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException
    {
        List<String> errorMessages = new LinkedList<>();
        Set<Class<?>> formClasses = new TreeSet<>(Comparator.comparing(Class::getName));

        for (Module module : ModuleLoader.getInstance().getModules())
        {
            for (Class<? extends Controller> controllerClass : module.getControllerClassToName().keySet())
            {
                SpringActionController controller = (SpringActionController) ViewServlet.getController(module, controllerClass);
                controller.setViewContext(_context);
                controller.getActionResolver().getActionDescriptors().stream()
                    .map(ad -> {
                        try
                        {
                            return controller.resolveAction(ad.getPrimaryName());
                        }
                        catch (Exception e)
                        {
                            String message = e.getMessage() + " while attempting to construct " + ad.getActionClass() + " (" + ad.getPrimaryName() + ") from controller " + controller.getClass().getName();
                            errorMessages.add(message);
                            _log.error(message, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(action -> action instanceof BaseViewAction<?> && !(action instanceof SimpleAction))
                    .map(action -> {
                        try
                        {
                            return ((BaseViewAction<?>) action).getCommandClass();
                        }
                        catch (Exception e)
                        {
                            errorMessages.add(e.getMessage());
                            return Object.class;
                        }
                    })
                    .filter(aClass -> Object.class != aClass)
                    .forEach(formClasses::add);
            }
        }

        assertTrue(errorMessages.toString(), errorMessages.isEmpty());
    }
%>

