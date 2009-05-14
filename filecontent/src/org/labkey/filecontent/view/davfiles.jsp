<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.time.FastDateFormat" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.webdav.WebdavService" %>
<%@ page import="org.labkey.api.attachments.AttachmentDirectory" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.filecontent.FileContentController" %>
<%@ page import="org.labkey.filecontent.FilesWebPart" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
//FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz", TimeZone.getTimeZone("GMT"), Locale.US);
FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz");
%>
<%
    ViewContext context = HttpView.currentContext();
    AttachmentDirectory root = (AttachmentDirectory)HttpView.currentModel();
    FilesWebPart me = (FilesWebPart) HttpView.currentView();
    Container c = context.getContainer();

    // prefix is where we what the tree rooted
    // TODO: applet and fileBrowser could use more consistent configuration parameters
    String rootName = c.getName();
    String webdavPrefix = context.getContextPath() + "/" + WebdavService.getServletPath();
    String rootPath = webdavPrefix + c.getEncodedPath();
    if (me.getFileSet() != null)
    {
        if (!rootPath.endsWith("/"))
            rootPath += "/";
        rootPath += PageFlowUtil.encode("@files") + "/" + PageFlowUtil.encode(me.getFileSet());
    }
    //String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + rootPath;
%>
<script type="text/javascript" language="javascript">
    LABKEY.requiresExtJs(true);
    LABKEY.requiresScript("applet.js");
    LABKEY.requiresScript("fileBrowser.js");
    LABKEY.requiresScript("FileUploadField.js");
</script>

<div class="extContainer" style="padding:20px;">
<div id="files"></div>
</div>

<div style="display:none;">
<div id="help">
    help helpy help.
</div>
</div>

<script type="text/javascript">
Ext.BLANK_IMAGE_URL = LABKEY.contextPath + "/_.gif";

var fileBrowser = null;
var fileSystem = null;

Ext.onReady(function()
{
    var configureAction = new Ext.Action({text: 'Configure', handler: function()
    {
        window.location = <%=PageFlowUtil.jsString(new ActionURL(FileContentController.ShowAdminAction.class, c).getLocalURIString())%>;
    }});
    var dropAction = new Ext.Action({text: 'Upload multiple files', scope:this, disabled:false, handler: function()
    {
        var dropUrl = <%=PageFlowUtil.jsString((new ActionURL("ftp","drop",c)).getEncodedLocalURIString() + (null == me.getFileSet() ? "" : "fileSetName=" + PageFlowUtil.encode(root.getLabel())))%>;
        window.open(dropUrl, '_blank', 'height=600,width=1000,resizable=yes');
    }});

    Ext.QuickTips.init();
    fileSystem = new LABKEY.WebdavFileSystem({
        baseUrl:<%=PageFlowUtil.jsString(rootPath)%>,
        rootName:<%=PageFlowUtil.jsString(rootName)%>});
    fileBrowser = new LABKEY.FileBrowser({
        fileSystem:fileSystem
        ,helpEl:null
        ,showAddressBar:false
        ,showFolderTree:false
        ,showProperties:false
        ,showDetails:true
        ,allowChangeDirectory:false
        ,actions:{drop:dropAction, configure:configureAction}
        ,tbar:['download','deletePath','refresh'<%=c.hasPermission(context.getUser(),ACL.PERM_INSERT)?",'uploadTool'":""%><%=c.hasPermission(context.getUser(),ACL.PERM_ADMIN)?",'configure'":""%>]
    });

    fileBrowser.on("doubleclick", function(record){
        window.location = "<%=PageFlowUtil.encodePath(request.getContextPath())%>/files<%=c.getEncodedPath()%>" + encodeURI(record.data.name) + "?renderAs=DEFAULT<%=me.getFileSet()==null ? "" : "&fileSet=" + PageFlowUtil.encode(me.getFileSet())%>";
    });

    fileBrowser.render('files');
    var resizer = new Ext.Resizable('files', {width:800, height:600, minWidth:640, minHeight:400});
    resizer.on("resize", function(o,width,height){ this.setWidth(width); this.setHeight(height); }.createDelegate(fileBrowser));
    fileBrowser.start();
});
</script>
