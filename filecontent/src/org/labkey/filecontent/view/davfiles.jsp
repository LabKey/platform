<%
/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.webdav.WebdavService" %>
<%@ page import="org.labkey.api.attachments.AttachmentDirectory" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
//FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz", TimeZone.getTimeZone("GMT"), Locale.US);
FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz");
%>
<%
    ViewContext context = HttpView.currentContext();
    AttachmentDirectory root = (AttachmentDirectory)HttpView.currentModel();
    Container c = ContainerManager.getForId(root.getContainerId());
    if (null == c)
    {
        return;
    }
    // prefix is where we what the tree rooted
    // TODO: applet and fileBrowser could use more consistent configuration parameters
    String rootName = c.getName();
    String webdavPrefix = context.getContextPath() + "/" + WebdavService.getServletPath();
    String rootPath = webdavPrefix + c.getPath();
    String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + rootPath;
%>
<script type="text/javascript" language="javascript">
    LABKEY.requiresExtJs(true);
    LABKEY.requiresScript("fileBrowser.js");
    LABKEY.requiresScript("applet.js",true);
</script>

<div class="extContainer" style="padding:20px;">
<div id="files"/>
</div>

<div style="display:none;">
<div id="help">
    help helpy help.
</div>
</div>

<script type="text/javascript">
Ext.BLANK_IMAGE_URL = LABKEY.contextPath + "/_.gif";

Ext.onReady(function()
{
    Ext.QuickTips.init();
    var fileSystem = new WebdavFileSystem({
        baseUrl:<%=PageFlowUtil.jsString(rootPath)%>,
        rootName:<%=PageFlowUtil.jsString(rootName)%>});
    var fileBrowser = new FileBrowser({
        fileSystem:fileSystem
        ,helpEl:'help'
        ,showAddressBar:false
        ,showFolderTree:false
        ,showProperties:false
        ,showDetails:false
        ,allowChangeDirectory:false
    });
    fileBrowser.render('files');
    var resizer = new Ext.Resizable('files', {width:800, height:600, minWidth:640, minHeight:400});
    resizer.on("resize", function(o,width,height){ this.setWidth(width); this.setHeight(height); }.createDelegate(fileBrowser));
    fileBrowser.start.defer(0, fileBrowser);
});

LABKEY.writeApplet(
{
    id:"dropApplet",
    archive:"<%=request.getContextPath()%>/_applets/applets-9.1.jar?guid=<%=GUID.makeHash()%><%=AppProps.getInstance().getServerSessionGUID()%>",
    code:"org.labkey.applets.drop.DropApplet",
    width:200,
    height:200,
    params:
    {
        url:<%=PageFlowUtil.jsString(baseUrl)%>,
        webdavPrefix:<%=PageFlowUtil.jsString(webdavPrefix)%>,
        user:<%=PageFlowUtil.jsString(context.getUser().getEmail())%>,
        password:<%=PageFlowUtil.jsString(request.getSession(true).getId())%>
    }
});

function getDropApplet()
{
    try
    {
        var el = Ext.get("dropApplet");
        var applet = el ? el.dom : null;
        if (applet && 'isActive' in applet && applet.isActive())
            return applet;
    }
    catch (x)
    {
    }
    return null;
}
</script>
