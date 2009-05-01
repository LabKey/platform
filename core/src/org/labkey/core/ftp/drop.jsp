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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.webdav.WebdavService" %>
<%@ page import="org.labkey.core.ftp.FtpPage" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView me = HttpView.currentView();
    ViewContext context = me.getViewContext();
    String contextPath = context.getContextPath();
    FtpPage dropPage = (FtpPage)me.getModelBean();
    String sessionId = PageFlowUtil.getSessionId(request);

    NavTree menu = new NavTree();
    menu.addChild("Browse Files","javascript:browseFiles();");
    menu.addChild("New Folder","javascript:showMkdirDialog();");

%>
<script type="text/javascript">
LABKEY.requiresScript("applet.js",true);
LABKEY.requiresScript("dropApplet.js",true);
</script>
<table width="100%">
      <tr><td class="labkey-announcement-title"><span>File upload tool</span></td><td align="right"><%=PageFlowUtil.generateButton("Close", "#close", "closeWindow();")%></td></tr>
      <tr><td colspan=2 align="left"><a id="ftpLocation" href="<%=h(dropPage.getUserURL())%>"><%=dropPage.getURL()%></a></td>
</tr></table>
<table id=ftpOuterTable width="100%"><tr>
    <td valign="top" width=200 height=100%><div id="appletDiv" class="labkey-nav-bordered" style="padding:2px; margin:1px; width:200px; height:200px;"><script type="text/javascript">
var applet = new LABKEY.Applet({
    id:"dropApplet",
    archive:"<%=request.getContextPath()%>/_applets/applets-9.1.jar",
    code:"org.labkey.applets.drop.DropApplet",
    width:200,
    height:200,
    params:{
        url:<%=PageFlowUtil.jsString(dropPage.getURL())%>,                                     
        webdavPrefix:<%=PageFlowUtil.jsString(contextPath + "/" + WebdavService.getServletPath())%>,
        user:<%=PageFlowUtil.jsString(context.getUser().getEmail())%>,
        password:<%=PageFlowUtil.jsString(sessionId)%>,
        events:"appletEvents",
        dropFileLimit:<%=dropPage.getPipeline()!=null?1000:0%>
    }});

var viewport;

Ext.onReady(function()
{
    viewport = new Ext.Viewport();
    onWindowResize();
    applet.render(Ext.get('appletDiv'));
    applet.onReady(startDropApplication);
});


var resizeIntervalId = null;
function onWindowResize()
{
//    if (!resizeIntervalId)
//        resizeIntervalId = window.setInterval(resize,100);
    resize();
}

function closeWindow()
{
    try
    {
        if (window.opener && window.opener !=  window) // !window.opener.closed)
            window.opener.location.reload();
        window.close();
    }
    catch(x)
    {
    }
}

function resize()
{
    var s = viewport.getSize();
    _resize(s.width,s.height);
}

function _resize(windowWidth,windowHeight)
{
    window.clearInterval(resizeIntervalId); resizeIntervalId = null;
    var elem = Ext.get('scrollDiv');
    if (!elem) return;
    var minHeight=200; var bottomMargin=80;
    var dialogBody = Ext.get('dialogBody');
    if (dialogBody)
        bottomMargin = 20 + 60; // Ext.get(document.body).getBottom() - dialogBody.getBottom();
    var height = Math.max(minHeight, windowHeight-elem.getXY()[1]-bottomMargin);
//    elem.scale(null,height);
    elem.dom.style.height = "" + height + "px";
//     elem.dom.parent().scale(null,height);
    elem.dom.parentNode.style.height = elem.dom.style.height; // this makes firefox redraw properly (on shrink)
}

Ext.EventManager.onWindowResize(onWindowResize);
</script></div>
<%=PageFlowUtil.generateButton("Find Files...", "#findFiles", "browseFiles();")%>
<!--PageFlowUtil.generateButton("New Folder...", "#mkdir", "showMkdirDialog();")-->
<br>
        <table>
            <tr><td><img src="<%=contextPath%>/_.gif" width="100" height="1"></td><td><img src="<%=contextPath%>/_.gif" width="100" height="1"></td></tr>
            <tr><td align="right" id="ftpBytesTransferred">0</td><td>bytes</td></tr>
            <tr><td align="right" id="ftpFilesTransferred">0</td><td>files</td></tr>
            <tr><td align="right" id="ftpFilesPending">0</td><td>remaining</td></tr>
<!--            <tr><td align="right" id="ftpCountUpdate">0</td><td>update</td></tr> -->
        </table>
</td>

<td width=1><img src="<%=contextPath%>/_.gif" width=1 height=204></td>

<td valign="top" width=100% height=100%>
    <table class="labkey-no-spacing" width="100%">
        <tr><td class="labkey-tab-space">&nbsp;</td><td class="labkey-tab-space">&nbsp;</td><td id="transfersTab" class="labkey-tab-selected" onclick="showTransfers()"><a href="#">transfers</a></td><td class="labkey-tab-space">&nbsp;</td><!--<td id="filesTab" class="labkey-tab labkey-tab-shaded" onclick="showFiles()"><a href="#">files</a></td><td class="labkey-tab-space">&nbsp;</td>--><td id="consoleTab" class="labkey-tab labkey-tab-shaded" onclick="showConsole()"><a href="#">console</a></td><td class="labkey-tab-space" width="100%">&nbsp;</td></tr>
        <tr><td colspan="20" style="padding:0px"></td></tr>
    </table>
    <table class="labkey-no-spacing" width="100%" height="100%">
        <tr>
        <td colspan="20" valign="top" width="100%" height="100%" class="labkey-nav-bordered" style="border-top:0px;">
            <div id="scrollDiv" style="overflow:scroll; width:100%;">
            <div id="ftpConsole" style="display:none; font-family:courier,monospace;">&nbsp;</div><div id="ftpTransfers" style="display:inline;">&nbsp;</div><!--<div id="ftpListing" style="display:none; font-family:courier,monospace;">&nbsp;</div>-->
            </div>
        </td></tr>
    </table>
</td>
</tr></table>
