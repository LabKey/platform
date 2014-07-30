<%
/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext3"));
        resources.add(ClientDependency.fromFilePath("editInPlaceElement.css"));
        resources.add(ClientDependency.fromFilePath("editInPlaceElement.js"));
        return resources;
    }
%>
<%
    Container container = getContainer();
%>
<script type="text/javascript">
    var _wb_titleId = Ext.id();
    LABKEY.NavTrail.setTrail("<span class='wb-name'><%=container.getRowId()%>:&nbsp;</span><span class='labkey-edit-in-place' id='" + _wb_titleId + "'><%=h(container.getTitle())%></span>",
            undefined, <%=PageFlowUtil.jsString(container.getTitle())%>);
</script>

<style type="text/css">
    .wb-name
    {
        color: #999999;
    }
</style>

<div id="wb-description" class="labkey-edit-in-place"><%=null != container.getDescription() ? h(container.getDescription()) : "&nbsp;"%></div>

<script type="text/javascript">
    Ext.onReady(function(){
        if (!LABKEY.Security.currentUser.canUpdate)
            return;

        new LABKEY.ext.EditInPlaceElement({
            applyTo: 'wb-description',
            multiLine: true,
            emptyText: 'No description provided. Click to add one.',
            updateConfig: {
                url: LABKEY.ActionURL.buildURL("core", "updateDescription"),
                jsonDataPropName: 'description'
            }
        });

        new LABKEY.ext.EditInPlaceElement({
            applyTo: _wb_titleId,
            updateConfig: {
                url: LABKEY.ActionURL.buildURL("core", "updateTitle"),
                jsonDataPropName: 'title'
            },
            listeners: {
                beforecomplete: function(newText){
                    return (newText.length > 0);
                }
            }
        });
    });
</script>