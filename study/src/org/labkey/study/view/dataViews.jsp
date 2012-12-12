<%
/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
      resources.add(ClientDependency.fromFilePath("Ext4"));
      resources.add(ClientDependency.fromFilePath("study/DataViewsPanel.css"));
      resources.add(ClientDependency.fromFilePath("study/DataViewsPanel.js"));
      resources.add(ClientDependency.fromFilePath("study/DataViewPropertiesPanel.js"));
      return resources;
  }
%>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    User u = me.getViewContext().getUser();
    int webPartId = me.getModelBean().getRowId();
%>
<div>
    <div id='dataset-browsing-<%=me.getModelBean().getIndex()%>' class="dvc"></div>
</div>
<script type="text/javascript">

    function init()
    {
        var dataViewsPanel = Ext4.create('LABKEY.ext4.DataViewsPanel', {
            id          : 'data-views-panel-<%= webPartId %>',
            renderTo    : 'dataset-browsing-<%= me.getModelBean().getIndex() %>',
            pageId      : <%= PageFlowUtil.jsString(me.getModelBean().getPageId()) %>,
            index       : <%= me.getModelBean().getIndex() %>,
            webpartId   : <%= webPartId %>,
            returnUrl   : '<%= me.getViewContext().getActionURL().getLocalURIString()%>',
            allowCustomize : <%= me.getViewContext().getContainer().hasPermission(u, AdminPermission.class) %>
        });

        var _resize = function(w,h) {
            LABKEY.Utils.resizeToViewport(dataViewsPanel, w, -1, null, null, 15); // don't fit to height
        };

        Ext4.EventManager.onWindowResize(_resize);
    }

    /**
     * Called by Server to handle cusomization actions. NOTE: The panel must be set to allow customization
     * See LABKEY.ext4.DataViewsPanel.isCustomizable()
     */
    function customizeDataViews(webpartId, pageId, index) {

        function initPanel() {
            // eew, should find better way to access global scope
            var panel = Ext4.getCmp('data-views-panel-' + webpartId);

            if (panel) { panel.customize(); }
        }

        Ext4.onReady(initPanel);
    }

    Ext4.onReady(init);
</script>
