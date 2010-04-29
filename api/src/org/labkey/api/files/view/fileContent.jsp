<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.attachments.AttachmentDirectory" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.files.view.FilesWebPart" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<script type="text/javascript">
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresScript("applet.js");
    LABKEY.requiresScript("StatusBar.js");
    LABKEY.requiresScript("fileBrowser.js");
    LABKEY.requiresScript("FileUploadField.js");
    LABKEY.requiresScript("ActionsAdmin.js");
    LABKEY.requiresScript("PipelineAction.js");
    LABKEY.requiresScript("FileProperties.js");
    LABKEY.requiresScript("FileContent.js");
</script>

<%
    ViewContext context = HttpView.currentContext();
    FilesWebPart.FilesForm bean = (FilesWebPart.FilesForm)HttpView.currentModel();
    FilesWebPart me = (FilesWebPart) HttpView.currentView();

    AttachmentDirectory root = bean.getRoot();
    Container c = context.getContainer();

    ActionURL projConfig = PageFlowUtil.urlProvider(AdminUrls.class).getProjectSettingsFileURL(c);
%>

<style type="text/css">
    .x-layout-mini
    {
        display: none;
    }
</style>

<div class="extContainer" id="<%=bean.getContentId()%>"></div>

<%  if (!bean.isEnabled()) { %>

    File sharing has been disabled for this project. Sharing can be configured from the <a href="<%=projConfig%>">project settings</a> view.    

<%  } else if (!bean.isRootValid()) { %>

    <span class="labkey-error">
        The file root for this folder is invalid. It may not exist or may have been configured incorrectly.<br>
        <%=c.hasPermission(context.getUser(), AdminPermission.class) ? "File roots can be configured from the <a href=\"" + projConfig + "\">project settings</a> view." : "Contact your administrator to address this problem."%>
    </span>

<%  } %>

<script type="text/javascript">

    /**
     * activate the Ext state manager (for directory persistence), but by default, make all components
     * not try to load state.
     */
    Ext.state.Manager.setProvider(new Ext.state.CookieProvider());
    Ext.override(Ext.Component,{
        stateful:false
    });

Ext.BLANK_IMAGE_URL = LABKEY.contextPath + "/_.gif";
Ext.QuickTips.init();

var autoResize = <%=bean.isAutoResize()%>;
var fileBrowser = null;
var fileSystem = null;
var actionsURL = <%=PageFlowUtil.jsString(PageFlowUtil.urlProvider(PipelineUrls.class).urlActions(context.getContainer()).getLocalURIString() + "path=")%>;
var buttonActions = [];

<%
    for (FilesWebPart.FilesForm.actions action  : bean.getButtonConfig())
    {
%>
        buttonActions.push('<%=action.name()%>');
<%
    }
%>
function renderBrowser(rootPath, renderTo)
{
    if (!fileSystem)
    {
        fileSystem = new LABKEY.WebdavFileSystem({
            extraPropNames: ['description', 'actions'],

            // extra props should model Ext.data.Field types
            extraDataFields: [
                {name: 'description', mapping: 'propstat/prop/description'},
                {name: 'actionHref', mapping: 'propstat/prop/actions', convert : function (v, rec)
                    {
                        var result = [];
                        var actionsElements = Ext.DomQuery.compile('propstat/prop/actions').call(this, rec);
                        if (actionsElements.length > 0)
                        {
                            var actionElements = actionsElements[0].getElementsByTagName('action');
                            for (var i = 0; i < actionElements.length; i++)
                            {
                                var action = new Object();
                                var childNodes = actionElements[i].childNodes;
                                for (var n = 0; n < childNodes.length; n++)
                                {
                                    var childNode = childNodes[n];
                                    if (childNode.nodeName == 'message')
                                    {
                                        action.message = childNode.textContent || childNode.text;
                                    }
                                    else if (childNode.nodeName == 'href')
                                    {
                                        action.href = childNode.textContent || childNode.text;
                                    }
                                }
                                result[result.length] = action;
                            }
                        }
                        return result;
                    }}
            ],
            //extraPropNames: ["actions", "description"],
            baseUrl:rootPath,
            rootName:'fileset'
        });
    }
    var prefix = undefined;

<%  if (bean.getStatePrefix() != null) { %>
    prefix = '<%=bean.getStatePrefix()%>';
<%  } %>

    fileBrowser = new LABKEY.FilesWebPartPanel({
        fileSystem: fileSystem,
        helpEl:null,
        resizable: !Ext.isIE,
        showAddressBar: <%=bean.isShowAddressBar()%>,
        showFolderTree: <%=bean.isShowFolderTree()%>,
        folderTreeCollapsed: <%=bean.isFolderTreeCollapsed()%>,
        showProperties: false,
        showDetails: <%=bean.isShowDetails()%>,
        allowChangeDirectory: true,
        tbarItems: buttonActions,
        isPipelineRoot: <%=bean.isPipelineRoot()%>,
        adminUser : <%=getViewContext().getContainer().hasPermission(getViewContext().getUser(), AdminPermission.class)%>,
        statePrefix: prefix
    });

    //fileBrowser.height = 350;
    //fileBrowser.render(renderTo);

    var panel = new Ext.Panel({
        layout: 'fit',
        renderTo: renderTo,
        border: false,
/*
        layoutConfig: {
            columns: 1
        },
*/
        items: [fileBrowser],
        height: 350
    });

    var _resize = function(w,h)
    {
        if (!fileBrowser.rendered)
            return;
        var padding = [20,20];
        var xy = fileBrowser.el.getXY();
        var size = {
            width : Math.max(100,w-xy[0]-padding[0]),
            height : Math.max(100,h-xy[1]-padding[1])};
        fileBrowser.setSize(size);
        fileBrowser.doLayout();
    };

    if (autoResize)
    {
        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();
    }

    fileBrowser.start();

    <% //Temporary code for testing events
    if (AppProps.getInstance().isDevMode())
    {
    %>
        fileBrowser.on(BROWSER_EVENTS.transfercomplete, function(result) {showTransfer("transfercomplete", result)});
        fileBrowser.on(BROWSER_EVENTS.transferstarted, function(result) {showTransfer("transferstarted", result)});
        function showTransfer(heading, result)
        {
            console.log("Transfer event: " + heading);
            for (var fileIndex = 0; fileIndex <result.files.length; fileIndex++)
                console.log("  name: " + result.files[fileIndex].name + ", id: " + result.files[fileIndex].id);
        }
    <%
    }
    %>
}

<%  if (bean.isEnabled() && bean.isRootValid()) { %>
        Ext.onReady(function(){renderBrowser(<%=q(bean.getRootPath())%>, <%=q(bean.getContentId())%>);});
<%  } %>
</script>