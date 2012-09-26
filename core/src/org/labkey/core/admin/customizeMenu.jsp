<%
    /*
    * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.admin.CustomizeMenuForm" %>
<%@ page import="org.json.JSONObject" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<CustomizeMenuForm> me = (JspView<CustomizeMenuForm>) JspView.currentView();
    CustomizeMenuForm bean = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();
    String requiredPermission = PageFlowUtil.jsString(RoleManager.getPermission(AdminPermission.class).getUniqueName());
%>
<div id="someUniqueElement"></div>
<div id="someUniqueElement2"></div>
<script type="text/javascript">
    LABKEY.requiresScript("viewPicker.js");

    var bean = <%= text(new JSONObject(bean).toString()) %>;
</script>
<script type="text/javascript">

    (function(){

        var init = function()
        {
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

                LABKEY.Utils.resizeToViewport(customizeForm, Math.min(w, 800));
            };

            Ext.EventManager.onWindowResize(_resize);
            Ext.EventManager.fireWindowResize();

        };

        Ext.onReady(init);
    })();
</script>