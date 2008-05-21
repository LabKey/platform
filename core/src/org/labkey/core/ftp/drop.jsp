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
<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.core.ftp.FtpPage" %>
<%@ page import="org.labkey.core.webdav.DavController" %>
<%@ page import="org.labkey.api.util.GUID" %>
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

    WebTheme theme = org.labkey.api.view.WebTheme.getTheme();
    String border = "#" + theme.getHeaderLineColor();
    String highlight ="#" + theme.getHeaderLineColor();
%>
<style type="text/css">
    TD.myTabSelected, TD.myTab, TD.myTabSelected A, TD.myTab A {font-weight:bold; color:#ffffff;}
    TD.myTabSelected, TD.myTab {padding-left:5px; padding-right:5px; padding-top:2px; padding-bottom:2px;}
    TD.myTabSelected {background-color:<%= highlight %>; border-bottom:1px solid <%=highlight%>; border-left:1px solid #ffffff; border-top:1px solid #ffffff; border-right:1px solid #ffffff; }
    TD.myTab {background-color:#808080; border:1px solid #ffffff; }
    TD.myTabEmpty {background-color:#ffffff;}
</style>
<script type="text/javascript">LABKEY.requiresYahoo("yahoo");</script>
<script type="text/javascript">LABKEY.requiresYahoo("event");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dom");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dragdrop");</script>
<script type="text/javascript">LABKEY.requiresYahoo("animation");</script>
<script type="text/javascript">LABKEY.requiresYahoo("container");</script>
<script type="text/javascript">LABKEY.requiresScript("utils/dialogBox.js");</script>
<script type="text/javascript">
LABKEY.borderColor = '<%=border%>'; 
LABKEY.requiresScript("applet.js",true);
LABKEY.requiresScript("dropApplet.js",true);
</script>
<div><h3 class="ms-announcementtitle">
    <a id="ftpLocation" href="<%=h(dropPage.getUserURL())%>"><%=dropPage.getURL()%></a>
</h3></div>
<table id=ftpOuterTable width="100%"><tr>
    <td valign="top" width=200 height=100%><div id="appletDiv" style="border:solid 1px <%=border%>; padding:2px; margin:1px; width:200px; height:200px;"><script type="text/javascript">
LABKEY.writeApplet({
    id:"dropApplet",
    archive:"<%=request.getContextPath()%>/_applets/applets-8.2.jar?guid=<%=GUID.makeHash()%><%=AppProps.getInstance().getServerSessionGUID()%>",
    code:"org.labkey.applets.drop.DropApplet",
    width:200,
    height:200,
    params:{
        <%-- TODO switch applet to use URL which encodes more info --%>
        url:<%=PageFlowUtil.jsString(dropPage.getURL())%>,                                     
          scheme:<%=PageFlowUtil.jsString(dropPage.getScheme())%>,
          host:<%=PageFlowUtil.jsString(dropPage.getHost())%>,
          port:<%=PageFlowUtil.jsString(dropPage.getPort())%>,
          path:<%=PageFlowUtil.jsString(dropPage.getPath())%>,
          webdavPrefix:<%=PageFlowUtil.jsString(contextPath + DavController.SERVLETPATH)%>,
        port:<%=PageFlowUtil.jsString(dropPage.getPort())%>,
        user:<%=PageFlowUtil.jsString(context.getUser().getEmail())%>,
        password:<%=PageFlowUtil.jsString(sessionId)%>,
        updateEvent:"dropApplet_Update",
        dragEnterEvent:"dropApplet_DragEnter",
        dragExitEvent:"dropApplet_DragExit",
        dropFileLimit:<%=dropPage.getPipeline()!=null?1000:0%>
    }});
function onWindowLoad()
{
    onWindowResize();
    init();
}
var resizeIntervalId = null;
function onWindowResize()
{
    if (!resizeIntervalId)
        resizeIntervalId = window.setInterval(resize,100);
}
function resize()
{
    _resize(YAHOO.util.Dom.getViewportWidth(),YAHOO.util.Dom.getViewportHeight());
}
function _resize(windowWidth,windowHeight)
{
    window.clearInterval(resizeIntervalId); resizeIntervalId = null;
    var elem = _id('scrollDiv');
    if (!elem) return;
    var minHeight=200; var bottomMargin=80;
    var dialogBody = _id('dialogBody');
    if (dialogBody)
        bottomMargin = 20 + YAHOO.util.Dom.getDocumentHeight() - (YAHOO.util.Dom.getY(dialogBody) + dialogBody.offsetHeight);
    elem.style.height = "" + Math.max(minHeight, windowHeight-YAHOO.util.Dom.getY(elem)-bottomMargin) + "px";
    elem.parentNode.style.height = elem.style.height; // this makes firefox redraw properly (on shrink)
}
YAHOO.util.Event.addListener(window, "load", onWindowLoad);
YAHOO.util.Event.addListener(window, "resize", onWindowResize);
</script></div>
<a href="#findFiles" onclick="browseFiles();"><img src="<%=PageFlowUtil.buttonSrc("Find Files...")%>"></a>
<!--<a href="#mkdir" onclick="showMkdirDialog();"><img src="<%=PageFlowUtil.buttonSrc("New Folder...")%>"></a>-->
<br>
        <table class="normal">
            <tr><td><img src="<%=contextPath%>/_.gif" width="100" height="1"></td><td><img src="<%=contextPath%>/_.gif" width="100" height="1"></td></tr>
            <tr><td align="right" id="ftpBytesTransferred">0</td><td>bytes</td></tr>
            <tr><td align="right" id="ftpFilesTransferred">0</td><td>files</td></tr>
            <tr><td align="right" id="ftpFilesPending">0</td><td>remaining</td></tr>
<!--            <tr><td align="right" id="ftpCountUpdate">0</td><td>update</td></tr> -->
        </table>
</td>

<td width=1><img src="<%=contextPath%>/_img.gif" width=1 height=204></td>

<td valign="top" width=100% height=100%>
    <table cellspacing="0" cellpadding="0" width=100%>
        <tr><td class="myTabEmpty">&nbsp;</td><td id="transfersTab" class="myTabSelected" onclick="showTransfers()"><a href="#">transfers</a></td><!--<td id="filesTab" class="myTab" onclick="showFiles()"><a href="#">files</a></td>--><td id="consoleTab" class="myTab" onclick="showConsole()"><a href="#">console</a></td><td class="myTabEmpty" width="100%">&nbsp;</td></tr>
        <tr><td colspan="20" width="100%" height="3" style="background-color:<%=highlight%>;"><img src="<%=contextPath%>/_.gif" width=600 height=3></td></tr>
    <tr>
        <td colspan="20" valign="top" width="100%" height="100%" style="border-bottom:1px solid <%=border%>;border-left:1px solid <%=border%>;border-right:1px solid <%=border%>;">
            <div id="scrollDiv" style="overflow:scroll; width:100%;">
            <div id="ftpConsole" class="normal" style="display:none; font-family:courier,monospace;">&nbsp;</div><div id="ftpTransfers" class="normal" style="display:inline;">&nbsp;</div><!--<div id="ftpListing" class="normal" style="display:none; font-family:courier,monospace;">&nbsp;</div>-->
            </div>
        </td></tr>
    </table>
</td>
</tr></table>
