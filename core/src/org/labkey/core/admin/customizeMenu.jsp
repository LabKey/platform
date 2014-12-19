<%
/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.core.admin.CustomizeMenuForm" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("clientapi/ext3"));
        resources.add(ClientDependency.fromPath("viewPicker.js"));
        return resources;
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

        var customizeForm = customizeMenu(submitFunction, cancelFunction, 'someUniqueElement2', bean);

        var _resize = function(w, h) {
            if (!customizeForm.rendered)
                return;

            LABKEY.ext.Utils.resizeToViewport(customizeForm, Math.min(w, 800));
        };

        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();
    });
</script>