<%@ page import="org.labkey.api.data.DeferredUpgrade" %>
<%@ page import="org.labkey.api.data.UpgradeCode" %>
<%@ page import="org.labkey.api.module.ModuleContext" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.core.admin.sql.SqlScriptController.ScriptsAction" %>
<%@ page import="org.labkey.core.admin.sql.SqlScriptController.UpgradeCodeAction" %>
<%@ page import="java.lang.reflect.Modifier" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Class<?>[] params = new Class[]{ModuleContext.class};
    Map<String, String> methodMap = new TreeMap<>();
    ModuleLoader.getInstance().getModules().forEach(module -> {
        UpgradeCode code = module.getUpgradeCode();
        if (null != code)
        {
            Arrays.stream(code.getClass().getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> Arrays.equals(method.getParameterTypes(), params))
                .forEach(method -> {
                    String key = module.getName() + ": " + method.getName();
                    String display = key + (method.isAnnotationPresent(DeferredUpgrade.class) ? " (@DeferredUpgrade)" : "");
                    methodMap.put(key, display);
                });
        }
    });
%>
<labkey:errors/>

<p class="labkey-warning-messages" style="display:inline-block">Invoking upgrade code is very dangerous and could corrupt your database. Use this page only if your LabKey Account Manager instructs you to do so.</p>
<br>
<labkey:form method="post" action="<%=urlFor(UpgradeCodeAction.class)%>">
    <%=select().name("combined").addOptions(methodMap).className(null)%>
    <br><br>
    <%=button("Invoke").submit(true)%>&nbsp;
    <%=button("Cancel").href(urlFor(ScriptsAction.class))%>
</labkey:form>
