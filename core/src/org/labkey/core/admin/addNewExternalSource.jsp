<%
/*
 * Copyright (c) 2019 LabKey Corporation
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
<%@ page import="org.labkey.api.collections.LabKeyCollectors" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.Directive" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="java.util.Arrays" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Container c = getContainer();
    boolean isTroubleshooter = c.isRoot() && !c.hasPermission(getUser(), AdminOperationsPermission.class);
%>
<labkey:errors/>
<div style="width: 800px;">
    <p>
        For security reasons, the standard LabKey Content Security Policy (CSP) restricts the hosts that browsers can
        use as resource origins. By default, only sources from this server are allowed; other server hosts must be
        configured below to enable them to be used as external sources. All provided hosts are added into the CSP
        using the \${} substitution key shown next to each directive.
<%
    if (!isTroubleshooter)
    {
%>
        To allow a resource, pick a directive and add the associated hostname or IP address, for example: www.myexternalhost.com or 1.2.3.4.
<%
    }
%>
    </p>
    <p>
        For more information on the security concern, please refer to the
        <%=link("OWASP cheat sheet", "https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html#cross-origin-resource-sharing").clearClasses()%>
    </p>
</div>
<%
    if (!isTroubleshooter)
    {
%>
<labkey:form method="post">
    <table>
        <tr>
            <td><%=select().name("newDirective").addOptions(
                Arrays.stream(Directive.values()).collect(LabKeyCollectors.toLinkedMap(Enum::name, d->d.getCspDirective() + " ${" + d.getSubstitutionKey() + "}"))
            )%></td>
            <td>&nbsp;<input name="newHost" id="newHostTextField" size="75" /></td>
        </tr>
        <tr>
            <td><br/><input type="hidden" id="saveNew" name="saveNew" value="true"><%= button("Add").submit(true) %></td>
        </tr>
    </table>
</labkey:form>
<%
    }
%>
