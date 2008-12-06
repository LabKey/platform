<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="org.labkey.api.webdav.WebdavResolver" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.core.webdav.DavController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.webdav.WebdavService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
//FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz", TimeZone.getTimeZone("GMT"), Locale.US);
FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz");
%>
<%
    DavController.ListPage listpage = (DavController.ListPage) HttpView.currentModel();
    WebdavResolver.Resource resource = listpage.resource;
    String path = resource.getPath();
    ViewContext context = HttpView.currentContext();
    AppProps app = AppProps.getInstance();
    User user = context.getUser();
    String userAgent = StringUtils.trimToEmpty(request.getHeader("user-agent"));
    boolean supportsDavMount = false;
    boolean supportsDavScheme = false;
    boolean supportsWebdavScheme = userAgent.contains("Konqueror");
%>
<script type="text/javascript" language="javascript">
    LABKEY.requiresExtJs(true);
    LABKEY.requiresScript("fileBrowser.js");
</script>

<script type="text/javascript">
Ext.onReady(function()
{
    Ext.QuickTips.init();
    var fileSystem = new WebdavFileSystem({});
//    var fileSystem = new AppletFileSystem({getDropApplet:getDropApplet});
    var fileBrowser = new FileBrowser({fileSystem:fileSystem, renderTo:'files'});

    // let's let that layout
    (function() {
        fileBrowser.selectPath("/");
        fileBrowser.changeDirectory("/");
        fileBrowser.tree.getRootNode().expand();
    }).defer(10);
});
</script>

<div class="extContainer" style="padding:20px;">
<div id="files"/>
</div>

<script type="text/javascript">
    LABKEY.requiresScript("applet.js",true);
</script>
<script type="text/javascript">
LABKEY.writeApplet(
{
    id:"dropApplet",
    archive:"<%=request.getContextPath()%>/_applets/applets-8.3.jar?guid=<%=GUID.makeHash()%><%=AppProps.getInstance().getServerSessionGUID()%>",
    code:"org.labkey.applets.drop.DropApplet",
    width:200,
    height:200,
    params:
    {
        url:"http://localhost:8080/labkey/_webdav/",
        scheme:"http",
        host:"localhost",
        port:"8080",
        path:"/",
        webdavPrefix:"/labkey/_webdav/",
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

<hr>
<ul>
    <li>should be able to root anywhere in the tree</li>
    <li>pipeline actions</li>
    <li>pipeline actions filtered by provider</li>
    <li>history, audit</li>
    <li>don't we can read all the files we can list</li>
</ul>
</ul>
