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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Container c = getContainer();
    boolean isTroubleshooter = c.isRoot() && !c.hasPermission(getUser(), AdminOperationsPermission.class);
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    function deleteExisting(hostToDelete) {

        document.getElementById("delete").value = true;
        document.getElementById("saveAll").value = false;
        document.getElementById("existingExternalHost").value = hostToDelete;
        document.forms["existingExternalHosts"].submit();
    }

    function saveAll() {

        //clicking on save will save all the values - changed and unchanged values
        var num = 1;
        var inputNameExisting = "existingExternalHost" + num;
        var hosts = "";

        while (null != document.getElementById(inputNameExisting))
        {
            hosts += (document.getElementById(inputNameExisting).value + "\n");
            num++;
            inputNameExisting = "existingExternalHost" + num;
        }

        document.getElementById("saveAll").value = true;
        document.getElementById("existingExternalHosts").value = hosts;
        document.forms["existingExternalHosts"].submit();
    }
</script>

<labkey:form method="post" name="existingExternalHosts">

    <%
        AdminController.ExternalHostsForm bean = (AdminController.ExternalHostsForm) HttpView.currentModel();
    %>
    <table class="labkey-data-region-legacy labkey-show-borders">
        <tr>
            <th>External <%=h(bean.getTypeEnum().name())%> Host(s)</th>
            <th></th>
        </tr>
        <% if (bean.getExistingHostList().isEmpty()) { %>
            <tr><td colspan="2">No External <%=h(bean.getTypeEnum().name())%> Hosts have been configured.</td></tr>
        <% } %>

        <%
            int num = 1;
            for (String externalHost : bean.getExistingHostList()) {
                String inputNameExisting = "existingExternalHost" + num;
        %>
        <tr>

            <td><input type="text" id="<%=h(inputNameExisting)%>" name="<%=h(inputNameExisting)%>" value="<%= h(externalHost)%>" size="80"/></td>

            <td><%=isTroubleshooter ? HtmlString.EMPTY_STRING : button("Delete").primary(true).onClick("return deleteExisting(\"" + h(externalHost) + "\");") %>

            </td>
        </tr>
        <%
            num++;
            }
        %>
    </table>
        <% if (!bean.getExistingHostList().isEmpty()) { %>
            <input type="hidden" id="delete" name="delete" value="false" />
            <input type="hidden" id="existingExternalHost" name="existingExternalHost" value="" />
            <input type="hidden" id="existingExternalHosts" name="existingExternalHosts" value="" />
            <tr>
                <td></td>
                <td><br/><input type="hidden" id="saveAll" name="saveAll"><%=isTroubleshooter ? button("Done").href(urlProvider(AdminUrls.class).getAdminConsoleURL()) : button("Save").primary(true).onClick("return saveAll();")%>
            </tr>
        <% } %>
</labkey:form>
