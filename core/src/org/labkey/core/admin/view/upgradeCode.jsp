<%
/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
    Map<String, String> methodMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
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
