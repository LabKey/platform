<%
/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.CustomizeMenuForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
        dependencies.add("viewPicker.js");
    }
%>
<%
    JspView<CustomizeMenuForm> me = (JspView<CustomizeMenuForm>) JspView.currentView();
    CustomizeMenuForm bean = me.getModelBean();
%>
<div id="someUniqueElement2"></div>
<script type="text/javascript">
    Ext.onReady(function() {

        var bean = <%= text(new JSONObject(bean).toString()) %>;

        Ext.QuickTips.init();

        var submitFunction = function(params)
        {
            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('admin','customizeMenu.view'),
                method: 'POST',
                timeout: 30000,
                params: params,
                success: function(response, opts){
                    window.location = LABKEY.ActionURL.buildURL('admin', 'projectSettings.view', null, {tabId : 'menubar'});
                },
                failure: function(response, opts) {
                    LABKEY.Utils.displayAjaxErrorResponse(response, opts);
                }
            });
        };


        var cancelFunction = function()
        {
            window.location = LABKEY.ActionURL.buildURL('admin', 'projectSettings.view', null, {tabId : 'menubar'});
        };

        customizeMenu(submitFunction, cancelFunction, 'someUniqueElement2', bean);
    });
</script>