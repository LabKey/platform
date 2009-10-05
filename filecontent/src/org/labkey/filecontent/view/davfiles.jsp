<%
/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0                                                   m
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
<%@ page import="org.labkey.api.attachments.AttachmentService" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
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
    if (!rootPath.endsWith("/"))
        rootPath += "/";
    String startDir = "/";
    if (me.getFileSet() != null)
        startDir += "/@files/" + me.getFileSet();
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
Ext.QuickTips.init();

var fileBrowser = null;
var fileSystem = null;

function renderBrowser(rootPath, dir)
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
            rootName:'fileset'});
    fileBrowser = new LABKEY.FileBrowser({
        fileSystem:fileSystem
        ,helpEl:null
        ,showAddressBar:false
        ,showFolderTree:false
        ,showProperties:false
        ,showDetails:true
        ,allowChangeDirectory:false
        ,actions:{drop:dropAction, configure:configureAction}
        ,tbar:['download','deletePath','refresh'
        <%=c.hasPermission(context.getUser(),ACL.PERM_INSERT)?",'uploadTool'":""%>
        ,'->'
        , new Ext.form.Label({html:'File Set:&nbsp;'}), combo
        <%=c.hasPermission(context.getUser(),ACL.PERM_ADMIN)?",'configure'":""%>
        ]
    });

    fileBrowser.on("doubleclick", function(record){
        var contentType = record.data.contentType || "attachment";
        var location = "<%=PageFlowUtil.encodePath(request.getContextPath())%>/files<%=c.getEncodedPath()%>" + encodeURI(record.data.name) + "?renderAs=DEFAULT<%=me.getFileSet()==null ? "" : "&fileSet=" + PageFlowUtil.encode(me.getFileSet())%>";
        if (0 == contentType.indexOf("image/") || 0 == contentType.indexOf("text/"))
            window.open(location,"_blank");
        else
            window.location = location;
    });

    fileBrowser.render('files');
    var resizer = new Ext.Resizable('files', {width:800, height:600, minWidth:640, minHeight:400});
    resizer.on("resize", function(o,width,height){ this.setWidth(width); this.setHeight(height); }.createDelegate(fileBrowser));
    fileBrowser.start(dir);
}
var fileSets = [
<%
    boolean navigate = false;
    String selectedValue = null;
    ActionURL url = new ActionURL(FileContentController.BeginAction.class, c);
    AttachmentDirectory main = AttachmentService.get().getMappedAttachmentDirectory(c, false);
    if (null != main && null != main.getFileSystemDirectory())
    {
        String value = navigate ? url.getLocalURIString() : "/";
        out.write("[" + q(value) + ",'Default']");
        if (StringUtils.isEmpty(me.getFileSet()) || StringUtils.equals(me.getFileSet(),"Default"))
            selectedValue = value;
    }
    for (AttachmentDirectory attDir : AttachmentService.get().getRegisteredDirectories(c))
    {
        String name = attDir.getLabel();
        url.replaceParameter("fileSetName",name);
        String value = navigate ? url.getLocalURIString() : "/@files/" + name;
        out.write(",[" + q(value) + "," + q(name) + "]");
        if (StringUtils.equals(me.getFileSet(),name))
            selectedValue = value;
    }
    if (c.hasPermission(context.getUser(),ACL.PERM_ADMIN))
    {
//        out.write(",[" + q(new ActionURL(FileContentController.ShowAdminAction.class,c).getLocalURIString()) + ",'[configure]']");
    }
%>
];
var selectedValue = <%=q(selectedValue)%>;

Ext.onReady(function(){
    renderBrowser(<%=q(rootPath)%>, <%=q(startDir)%>);    
});

</script>  