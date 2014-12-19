<%
/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.BooleanUtils" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromPath("dataviews"));
      return resources;
  }
%>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    int webPartId = me.getModelBean().getRowId();
    Map<String, String> properties = me.getModelBean().getPropertyMap();
    User u = getUser();

    // the manageView flag refers to manage views
    boolean manageView = false;
    if (properties.containsKey("manageView"))
        manageView = BooleanUtils.toBoolean(properties.get("manageView"));

    String renderId = "dataviews-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>
<div id='<%=h(renderId)%>' class="dvc"></div>
<script type="text/javascript">

    Ext4.onReady(function() {
        var dvp = Ext4.create('LABKEY.ext4.DataViewsPanel', {
            id          : 'data-views-panel-<%= webPartId %>',
            renderTo    : <%=q(renderId)%>,
            pageId      : <%= PageFlowUtil.jsString(me.getModelBean().getPageId()) %>,
            index       : <%= me.getModelBean().getIndex() %>,
            webpartId   : <%= webPartId %>,
            manageView  : <%= manageView%>,
            fullPage    : <%= manageView%>,
            returnUrl   : '<%= getActionURL().getLocalURIString()%>',
            allowCustomize : <%= getContainer().hasPermission(u, AdminPermission.class) %>,
            allowEdit   : <%= getContainer().hasPermission(u, InsertPermission.class) %>,
            listeners   : {
                render: function(panel) {
                    if (panel.fullPage) {

                        var size = Ext4.getBody().getViewSize();
                        LABKEY.ext4.Util.resizeToViewport(panel, size.width, size.height);
                    }
                }
            }
        });

        var resize = function(w, h) {
            if (dvp && dvp.doLayout) {

                if (dvp.fullPage)
                    LABKEY.ext4.Util.resizeToViewport(dvp, w, h);
                else
                {
                    // Issue 18337: having multiple data view panels on same page causes resizing issues
                    var lfh = Ext4.query('div.labkey-folder-header');
                    var lsp  = Ext4.query('td.labkey-side-panel');

                    if (lfh.length > 0)
                    {
                        // use the full width of the tab panel header bar minus the width  of the narrow webpart panel
                        var labkeyProjWidth = Ext4.get(lfh[0]).getWidth();
                        var leftPanelWidth = 0;
                        if (lsp.length > 0)
                            leftPanelWidth = Ext4.get(lsp[0]).getWidth();

                        var width = labkeyProjWidth - leftPanelWidth - 30;
                        dvp.setWidth(Ext4.Array.max([width, 625]));
                        dvp.doLayout();
                    }
                }
            }
        };

        Ext4.EventManager.onWindowResize(resize, this, {delay: 250});
    });

    /**
     * Called by Server to handle customization actions. NOTE: The panel must be set to allow customization
     * See LABKEY.ext4.DataViewsPanel.isCustomizable()
     */
    function customizeDataViews(webpartId, pageId, index) {

        var initPanel = function() {
            var panel = Ext4.getCmp('data-views-panel-' + webpartId);

            if (panel) { panel.customize(); }
        };

        Ext4.onReady(initPanel);
    }

    function manageCategories(webpartId) {

        var initPanel = function() {
            var panel = Ext4.getCmp('data-views-panel-' + webpartId);

            if (panel) { panel.onManageCategories(); }
        };

        Ext4.onReady(initPanel);
    }

    /**
     * Called when edit icon is clicked. Enables related UI components to edit reports/datasets/etc. NOTE: The panel
     * must be set to allow customization See LABKEY.ext4.DataViewsPanel.isCustomizable()
     */
    function editDataViews(webpartId) {
        var enableEdit = function (){

            var panel = Ext4.getCmp('data-views-panel-' + webpartId);

            if (panel) { panel.edit(); }
        };

        Ext4.onReady(enableEdit);
    }

    function deleteDataViews(webpartId) {
        var enableDelete = function (){

            var panel = Ext4.getCmp('data-views-panel-' + webpartId);

            if (panel) { panel.deleteSelected(); }
        };

        Ext4.onReady(enableDelete);
    }
</script>
