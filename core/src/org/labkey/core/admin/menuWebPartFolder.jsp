<%
    /*
    * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.admin.MenuViewFactory.MenuWebPartFolderForm" %>
<%@ page import="org.labkey.core.admin.MenuViewFactory" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<MenuWebPartFolderForm> me = (HttpView<MenuViewFactory.MenuWebPartFolderForm>) HttpView.currentView();
    MenuWebPartFolderForm form = me.getModelBean();
    String divId = "folderMenu_" + form.getUniqueId();
%>
<div id="<%=text(divId)%>"></div>
<script type="text/javascript">
    LABKEY.requiresExt4Sandbox();
</script>
<script type="text/javascript">

    (function(){

       var initWebPart = function() {

           Ext4.onReady(function() {

               var folderPanel = Ext4.create('panel.MenuWebPartFolder', {
                   renderTo : '<%=text(divId)%>',
                   height   : 300,
                   minWidth : 400,
                   rootPath :  <%= PageFlowUtil.jsString(form.getRootPath()) %>,
                   filterType: <%= PageFlowUtil.jsString(form.getFilterType()) %>,
                   urlBase   : <%= PageFlowUtil.jsString(form.getUrlBase()) %>,
                   includeChildren: <%= form.isIncludeChildren() %>
               });

               var _resize = function(w, h) {
                   if (!folderPanel.rendered)
                       return;

                   LABKEY.Utils.resizeToViewport(folderPanel, w, h);
               };

               Ext4.EventManager.onWindowResize(_resize);
           });

       };

       LABKEY.requiresScript("admin/MenuWebPartFolder.js", true, initWebPart);
    })();
</script>