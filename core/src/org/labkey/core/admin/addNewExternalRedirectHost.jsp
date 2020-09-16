<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Container c = getContainer();
    boolean isTroubleshooter = c.isRoot() && !c.hasPermission(getUser(), AdminOperationsPermission.class);
%>
<labkey:errors/>
<div style="width: 700px">
    <p>
        For security reasons, LabKey Server restricts the host names that can be used in returnUrl parameters.
        By default, only redirects to the same LabKey instance are allowed.
        Other server host names must be configured below to allow them to be automatically redirected.
        For more information on the security concern, please refer to the
        <a href="https://cheatsheetseries.owasp.org/cheatsheets/Unvalidated_Redirects_and_Forwards_Cheat_Sheet.html">OWASP cheat sheet</a>.
    </p>
    <p>
        Add allowed hosts based on the server name or IP address, as they will be referenced in returnUrl values.
        For example: www.myexternalhost.com or 1.2.3.4
    </p>
</div>

<%
    if (isTroubleshooter)
    {
%>
<%=button("Done").href(urlProvider(AdminUrls.class).getAdminConsoleURL())%>
<%
    }
    else
    {
%>
<labkey:form method="post">
    <table>
        <tr>
            <td class="labkey-form-label"><label for="newExternalRedirectHostTextField">Host</label></td>
            <td><input name="newExternalRedirectHost" id="newExternalRedirectHostTextField" size="75" /></td>
        </tr>
        <tr>
            <td><br/><input type="hidden" id="saveNew" name="saveNew" value="true"><%= button("Save").submit(true) %></td>
        </tr>
    </table>
</labkey:form>
<%
    }
%>