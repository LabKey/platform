<%
/*
 * Copyright (c) 2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.query.controllers.OlapController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>
<%
    List<String> contextNames = (List<String>)HttpView.currentModel();
%>
<script>
    function deleteApp(contextName)
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL("olap", "deleteApp"),
            method: 'POST',
            jsonData: {
                contextName: contextName
            },
            success: function () {
                window.location.reload(true);
            }
        })
    }

    function confirmDeleteApp(contextName)
    {
        Ext4.Msg.confirm("Delete App Context",
                "Are you sure you want to delete the app context '" + Ext4.htmlEncode(contextName) + "'?",
                function (btnId) {
                    if (btnId == "yes") {
                        deleteApp(contextName);
                    }
                }
        );
    }
</script>

<labkey:errors/>

Application contexts defined in this folder:
<p>
<%=textLink("Create New", new ActionURL(OlapController.EditAppAction.class, getContainer()))%>
<table>
<% for (String contextName : contextNames) { %>
    <tr>
        <td><%=h(contextName)%></td>
        <td><%=textLink("edit", new ActionURL(OlapController.EditAppAction.class, getContainer()).addParameter("contextName", contextName))%></td>
        <td><%=textLink("delete", "#", "confirmDeleteApp(" + PageFlowUtil.jsString(contextName) + ");return false;", null)%></td>
    </tr>
<% } %>
</table>

