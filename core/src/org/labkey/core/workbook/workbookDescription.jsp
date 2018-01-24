<%
/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("editInPlaceElement.css");
        dependencies.add("editInPlaceElement.js");
    }
%>
<%
    Container container = getContainer();
%>
<style type="text/css">
    .wb-name
    {
        color: #999999;
    }
</style>
<div id="wb-description" class="labkey-edit-in-place"><%=null != container.getDescription() ? h(container.getDescription()) : "&nbsp;"%></div>
<script type="text/javascript">
    var _wb_titleId = Ext4.id();
    LABKEY.NavTrail.setTrail("<span class='wb-name'><%=container.getRowId()%>:&nbsp;</span><span class='labkey-edit-in-place' id='" + _wb_titleId + "'><%=h(container.getTitle())%></span>",
            undefined, <%=PageFlowUtil.jsString(container.getTitle())%>, false);

    Ext4.onReady(function(){
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