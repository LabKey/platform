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
<%@ page import="org.labkey.core.webdav.WebdavResolver" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.core.webdav.DavController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
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
    boolean supportsFolderAttribute = false;        // <a folder=""> support removed in recent verions of windows
    boolean supportsWebdavScheme = userAgent.contains("Konqueror");
%>
<html>
<head>
<title><%=h(path)%> -- WebDAV: <%=h(app.getServerName())%></title>
</head>
<style type="text/css">
A {text-decoration:none; behavior: url(#default#AnchorClick);}
TR {margin-right:3px; margin-left:3px;}
BODY, TD, TH { font-family: arial sans-serif; color: black; }
</style>
<body>
<table width="100%"><tr><td align="left">
<b><%
{
    ArrayList<WebdavResolver.Resource> dirs = new ArrayList<WebdavResolver.Resource>();
    WebdavResolver.Resource dir = resource;
    while (null != dir)
    {
        dirs.add(dir);
        dir = dir.parent();
    }
    for (int i=dirs.size()-1; i>=0 ; --i)
    {
        dir = dirs.get(i);
        %><a href="<%=h(dir.getLocalHref(context))%>"><%
        if ("/".equals(dir.getPath()))
        {
            %><%=h(dir.getHref(context))%><%
        }
        else
        {
            %><%=h(dir.getName())%>/<%
        }
        %></a><%
    }
}
%></b></td><td align="right">&nbsp;<%
    if (context.getUser().isGuest())
    {
        %><a href="<%=h(listpage.loginUrl)%>">Sign in</a><%        
    }
    else
    {
        %><%=h(context.getUser().getEmail())%><%
    }
%></td></tr> </table>
<hr size="1" noshade="noshade">
<table cellspacing="1" cellpadding="2">
<thead>
<th align="left" width="300">Filename</th>
<th align="center" width="100">Size</th>
<th align="right" width="240">Last Modified</th>
</thead>
<tr><%
    TreeMap<String, WebdavResolver.Resource> dirs = new TreeMap<String, WebdavResolver.Resource>();
    TreeMap<String, WebdavResolver.Resource> files = new TreeMap<String, WebdavResolver.Resource>();
    WebdavResolver.Resource parent = resource.parent();

    if (resource.canList(user))
    {
        for (WebdavResolver.Resource info : resource.list())
        {
            if (!info.canList(user))
                continue;
            else if (info.isCollection())
                dirs.put(info.getName(), info);
            else
                files.put(info.getName(), info);
        }
    }

    boolean shade = true;
    if (parent != null)
    {
        String name = "[ up ]";
        WebdavResolver.Resource info = parent;
        shade = !shade;
        long modified = info.getLastModified();
        %><tr bgcolor="<%=shade?"#ffffff":"#eeeeee"%>"><td align="left"><a href="<%=h(info.getLocalHref(context))%>"><%=h(name)%></a></td><%
        %><td align="right">&nbsp;</td><%
        %><td align="right" nowrap><%=modified==0?"&nbsp;":dateFormat.format(new Date(modified))%></td></tr><%
        out.println();
    }
    for (Map.Entry<String, WebdavResolver.Resource> entry : dirs.entrySet())
    {
        String name = entry.getKey() + "/";
        WebdavResolver.Resource info = entry.getValue();
        shade = !shade;
        long modified = info.getLastModified();
        %><tr bgcolor="<%=shade?"#ffffff":"#eeeeee"%>"><td align="left"><a href="<%=h(info.getLocalHref(context))%>"><%=h(name)%></a></td><%
        %><td align="right">&nbsp;</td><%
        %><td align="right" nowrap><%=modified==0?"&nbsp;":dateFormat.format(new Date(modified))%></td></tr><%
        out.println();
    }
    for (Map.Entry<String, WebdavResolver.Resource> entry : files.entrySet())
    {
        String name = entry.getKey();
        WebdavResolver.Resource info = entry.getValue();
        shade = !shade;
        long modified = info.getLastModified();
        if (info.canRead(user))
        {
            %><tr bgcolor="<%=shade?"#ffffff":"#eeeeee"%>"><td align="left"><a href="<%=h(info.getLocalHref(context))%>"><%=h(name)%></a></td><%
        }
        else
        {
            %><tr bgcolor="<%=shade?"#ffffff":"#eeeeee"%>"><td align="left"><%=h(name)%></td><%
        }
        %><td align="right"><%=info.getContentLength()%></td><%
        %><td align="right" nowrap><%=modified==0?"&nbsp;":dateFormat.format(new Date(modified))%></td></tr><%
        out.println();
    }
%></table>
<hr>
<%
    String href =  resource.getHref(context);
    String folder = resource.isCollection() ? href : resource.parent().getHref(context);
%>
This is a WebDav enabled directory.<br>
<%
if (supportsDavMount) {%><%=PageFlowUtil.buttonLink("davmount","?davmount")%><br><%}
if (supportsDavScheme) {%><%=PageFlowUtil.buttonLink("dav", href.replace("http:","dav:"))%><br><%}
if (supportsWebdavScheme) {%><%=PageFlowUtil.buttonLink("webdav", href.replace("http:","webdav:"))%><br><%}
if (supportsFolderAttribute) {%><a href="<%=h(href)%>" folder="<%=h(folder)%>"><%=PageFlowUtil.buttonImg("folder")%></a><br><%}

    ArrayList<String> can = new ArrayList<String>();
    if (resource.canRead(user)) can.add("read");
    if (resource.canWrite(user)) can.add("update");
    if (resource.canCreate(user)) can.add("create");
    if (resource.canDelete(user)) can.add("delete");
    if (!can.isEmpty())
    {
        %>You have permission to <%
        String comma = "";
        for (int i=0 ; i<can.size() ; i++)
        {
            %><%=comma%><%=(i==can.size()-1 && i > 1) ? "and ":""%><%=can.get(i)%><%
            comma = ", ";
        }
        %> files in this directory.<%
    }
%>
<!--<%=request.getHeader("user-agent")%>-->
</body>
</html>