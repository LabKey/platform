<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%
    JspView me = (JspView) HttpView.currentView();
    Container container = me.getViewContext().getContainer();
%>
<script type="text/javascript">
    LABKEY.requiresCss("editInPlaceElement.css");
    LABKEY.requiresScript("editInPlaceElement.js");
</script>

<style type="text/css">
    .wb-name-container
    {
        width: 1%;
        white-space: nowrap;
    }
    .wb-name
    {
        font-size: 12pt;
        font-weight: bold;
    }
    .wb-title-container
    {
        width: 99%;
    }
    .wb-title
    {
        font-size: 12pt;
        font-weight: bold;
    }
    .wb-name-title-container
    {
        padding-bottom: 5px;
        width: 100%;
        border: 0;
    }
    td .wb-name-title-container
    {
        text-align: left;
        vertical-align: top;
    }
</style>

<table class="wb-name-title-container">
    <tr>
        <td class="wb-name-container"><div class="wb-name"><%=PageFlowUtil.filter(container.getName() + ":")%></div></td>
        <td class="wb-title-container"><div id="wb-title" class="wb-title"><%=PageFlowUtil.filter(container.getTitle())%></div></td>
    </tr>
</table>

<div id="wb-description"><%=null != container.getDescription() ? PageFlowUtil.filter(container.getDescription()) : "&nbsp;"%></div>

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
            applyTo: 'wb-title',
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