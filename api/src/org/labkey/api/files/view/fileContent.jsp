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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.attachments.AttachmentDirectory" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.files.FileUrls" %>
<%@ page import="org.labkey.api.files.view.FilesWebPart" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<script type="text/javascript">
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresScript("applet.js");
    LABKEY.requiresScript("fileBrowser.js");
    LABKEY.requiresScript("FileUploadField.js");
    LABKEY.requiresScript("ActionsAdmin.js");
    LABKEY.requiresScript("PipelineAction.js");
    LABKEY.requiresScript("FileContent.js");
</script>

<%
    ViewContext context = HttpView.currentContext();
    FilesWebPart.FilesForm bean = (FilesWebPart.FilesForm)HttpView.currentModel();
    FilesWebPart me = (FilesWebPart) HttpView.currentView();

    AttachmentDirectory root = bean.getRoot();
    Container c = context.getContainer();

    String startDir = "/";
%>


<div class="extContainer">
    <table>
        <tr><td><div id="files"></div></td></tr>
    </table>
</div>

<script type="text/javascript">
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
function renderBrowser(rootPath, dir)
{
    var configureAction = new Ext.Action({text: 'Configure', handler: function()
    {
        window.location = <%=PageFlowUtil.jsString(PageFlowUtil.urlProvider(FileUrls.class).urlShowAdmin(c).getLocalURIString())%>;
    }});

    var dropAction = new Ext.Action({text: 'Upload multiple files', scope:this, disabled:false, handler: function()
    {
        var dropUrl = <%=PageFlowUtil.jsString((new ActionURL("ftp","drop",c)).getEncodedLocalURIString() + (null == me.getFileSet() ? "" : "fileSetName=" + PageFlowUtil.encode(root.getLabel())))%>;
        window.open(dropUrl, '_blank', 'height=600,width=1000,resizable=yes');
    }});

    // TODO even better just refresh/rerender the browser not the whole page
    var combo = new Ext.form.ComboBox({
        name: 'filesetComboBox',
        store: fileSets,
        typeAhead: true,
        mode: 'local',
        triggerAction: 'all',
        selectOnFocus:true,
        width:135,
        value:selectedValue
    });
    combo.on("select",function(){
        var value = combo.getValue();
        if (value.indexOf("showAdmin.view") != -1)
            window.location=value;
        else
            fileBrowser.changeDirectory(value);
    });

    if (!fileSystem)
        fileSystem = new LABKEY.WebdavFileSystem({
            baseUrl:rootPath,
            rootName:'fileset'
        });

    fileBrowser = new LABKEY.FilesWebPartPanel({
        fileSystem: fileSystem,
        helpEl:null,
        showAddressBar: <%=bean.isShowAddressBar()%>,
        showFolderTree: <%=bean.isShowFolderTree()%>,
        showProperties: false,
        showDetails: <%=bean.isShowDetails()%>,
        allowChangeDirectory: true,
        //actions: {drop:dropAction, configure:configureAction},
        tbarItems: buttonActions
/*
        buttonCfg:['download','deletePath','refresh'
        <%=c.hasPermission(context.getUser(), InsertPermission.class)?",'uploadTool'":""%>
        ,'->'
        , new Ext.form.Label({html:'File Set:&nbsp;'}), combo
        <%=c.hasPermission(context.getUser(), AdminPermission.class)?",'configure'":""%>
        ]
*/
    });

    fileBrowser.height = 300;
/*
    fileBrowser.on("doubleclick", function(record){
        var contentType = record.data.contentType || "attachment";
        var location = "<%=PageFlowUtil.encodePath(request.getContextPath())%>/files<%=c.getEncodedPath()%>" + encodeURI(record.data.name) + "?renderAs=DEFAULT<%=me.getFileSet()==null ? "" : "&fileSet=" + PageFlowUtil.encode(me.getFileSet())%>";
        if (0 == contentType.indexOf("image/") || 0 == contentType.indexOf("text/"))
            window.open(location,"_blank");
        else
            window.location = location;
        });
*/

//    var resizer = new Ext.Resizable('files', {width:800, height:600, minWidth:640, minHeight:400});
//    resizer.on("resize", function(o,width,height){ this.setWidth(width); this.setHeight(height); }.createDelegate(fileBrowser));

    fileBrowser.render('files');

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

    fileBrowser.start(dir);

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
    var fileSets = [
<%
        boolean navigate = false;
        String selectedValue = null;
        ActionURL url = PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c);
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        AttachmentDirectory main = svc.getMappedAttachmentDirectory(c, false);
        if (null != main && null != main.getFileSystemDirectory())
        {
            String value = navigate ? url.getLocalURIString() : "/";
            out.write("[" + q(value) + ",'Default']");
            if (StringUtils.isEmpty(me.getFileSet()) || StringUtils.equals(me.getFileSet(),"Default"))
                selectedValue = value;
        }
        for (AttachmentDirectory attDir : svc.getRegisteredDirectories(c))
        {
            String name = attDir.getLabel();
            url.replaceParameter("fileSetName",name);
            String value = navigate ? url.getLocalURIString() : "/@files/" + name;
            out.write(",[" + q(value) + "," + q(name) + "]");
            if (StringUtils.equals(me.getFileSet(),name))
                selectedValue = value;
        }
        if (c.hasPermission(context.getUser(), AdminPermission.class))
        {
    //        out.write(",[" + q(new ActionURL(FileContentController.ShowAdminAction.class,c).getLocalURIString()) + ",'[configure]']");
        }
%>
    ];

    var selectedValue = <%=q(selectedValue)%>;

    Ext.onReady(function(){renderBrowser(<%=q(bean.getRootPath())%>, <%=q(startDir)%>);});
</script>