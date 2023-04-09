<%@ page import="org.junit.Test" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="org.labkey.api.action.BaseViewAction" %>
<%@ page import="org.labkey.api.action.SpringActionController" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.module.SimpleAction" %>
<%@ page import="org.labkey.api.view.ViewServlet" %>
<%@ page import="org.springframework.web.servlet.mvc.Controller" %>
<%@ page import="java.lang.reflect.Method" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.Objects" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspTest.DRT" %>

<%!
    // Enumerate all registered actions, verifying that the actions and their associated forms can be instantiated
    @Test
    public void testActions() throws IllegalAccessException, InstantiationException
    {
        List<String> errorMessages = new LinkedList<>();
        Set<Class<?>> formClasses = new TreeSet<>(Comparator.comparing(Class::getName));

        for (Module module : ModuleLoader.getInstance().getModules())
        {
            for (Class<? extends Controller> controllerClass : module.getControllerClassToName().keySet())
            {
                SpringActionController controller = (SpringActionController) ViewServlet.getController(module, controllerClass);
                controller.getActionResolver().getActionDescriptors().stream()
                    .map(ad -> {
                        try
                        {
                            return ad.createController(controller);
                        }
                        catch (Exception e)
                        {
                            errorMessages.add(e.getMessage() + " while attempting to construct " + ad.getActionClass());
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

        // Identify all form getter methods that return an old JSONObject (or an array or list of them). This is useful
        // for the JSONObject migration process.
//        for (Class<?> formClass : formClasses)
//        {
//            for (Method method : formClass.getDeclaredMethods())
//            {
//                String name = method.getName();
//                if (name.startsWith("get"))
//                {
//                    String typeName = method.getGenericReturnType().toString();
//
//                    if (typeName.contains("org.json.old.JSONObject"))
//                        System.out.println(typeName + ": " + formClass + "." + name + " -> " + typeName);
//                }
//            }
//        }

        assertTrue(errorMessages.toString(), errorMessages.isEmpty());
    }
%>

