<%
/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.apache.commons.lang3.time.FastDateFormat" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.webdav.WebdavResource" %>
<%@ page import="org.labkey.core.webdav.DavController" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz");
%>
<%
    DavController.ListPage listpage = (DavController.ListPage) HttpView.currentModel();
    WebdavResource resource = listpage.resource;
    ViewContext context = getViewContext();
    User user = getUser();
    String userAgent = StringUtils.trimToEmpty(request.getHeader("user-agent"));
    boolean supportsDavMount = false;
    boolean supportsDavScheme = false;
    boolean supportsWebdavScheme = userAgent.contains("Konqueror");
%>

<style type="text/css">
    a {text-decoration:none; behavior: url(#default#AnchorClick);}
    tr {margin-right:3px; margin-left:3px;}
    body, td, th { font-family: arial sans-serif; color: black; }
</style>
<style type="text/css" media="print">
    a.labkey-button { display: none; }
</style>
<table width="100%"><tr><td align="left">
<b><%
{
    ArrayList<WebdavResource> dirs = new ArrayList<>();
    WebdavResource dir = resource;
    while (null != dir)
    {
        dirs.add(dir);
        dir = (WebdavResource)dir.parent();
    }
    for (int i=dirs.size()-1; i>=0 ; --i)
    {
        dir = dirs.get(i);
        %><a href="<%=h(dir.getLocalHref(context))%>?listing=html"><%
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
%></b></td><td align="right"><%= button("Standard View").href("?listing=ext") %>&nbsp;<%
    if (user.isGuest())
    {
        %><a href="<%=h(listpage.loginURL)%>">Sign In</a><%
    }
    else
    {
        %><%=h(user.getEmail())%><%
    }
%></td></tr> </table>
<hr size="1" noshade="noshade">
<table>
<thead>
<th align="left" width="300">Filename</th>
<th align="center" width="100">Size</th>
<th align="right" width="240">Last Modified</th>
</thead>
<tr><%
    TreeMap<String, WebdavResource> dirs = new TreeMap<>();
    TreeMap<String, WebdavResource> files = new TreeMap<>();
    WebdavResource parent = (WebdavResource)resource.parent();

    if (resource.canList(user, true))
    {
        for (WebdavResource info : resource.list())
        {
            if (!info.canList(user, true))
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
        WebdavResource info = parent;
        shade = !shade;
        long modified = info.getLastModified();
        %><tr class="<%=getShadeRowClass(shade)%>"><td align="left"><a href="<%=h(info.getLocalHref(context))%>?listing=html"><%=h(name)%></a></td><%
        %><td align="right">&nbsp;</td><%
        %><td align="right" nowrap><%=modified==0?"&nbsp;":dateFormat.format(new Date(modified))%></td></tr><%
        out.println();
    }
    for (Map.Entry<String, WebdavResource> entry : dirs.entrySet())
    {
        String name = entry.getKey() + "/";
        WebdavResource info = entry.getValue();
        shade = !shade;
        long modified = info.getLastModified();
        %><tr class="<%=getShadeRowClass(shade)%>"><td align="left"><a href="<%=h(info.getLocalHref(context))%>?listing=html"><%=h(name)%></a></td><%
        %><td align="right">&nbsp;</td><%
        %><td align="right" nowrap><%=modified==0?"&nbsp;":dateFormat.format(new Date(modified))%></td></tr><%
        out.println();
    }
    for (Map.Entry<String, WebdavResource> entry : files.entrySet())
    {
        String name = entry.getKey();
        WebdavResource info = entry.getValue();
        shade = !shade;
        long modified = info.getLastModified();
        if (info.canRead(user,false))
        {
            %><tr class="<%=getShadeRowClass(shade)%>"><td align="left"><a href="<%=h(info.getLocalHref(context))%>?listing=html"><%=h(name)%></a></td><%
        }
        else
        {
            %><tr class="<%=getShadeRowClass(shade)%>"><td align="left"><%=h(name)%></td><%
        }
        %><td align="right"><%=info.getContentLength()%></td><%
        %><td align="right" nowrap><%=modified==0?"&nbsp;":dateFormat.format(new Date(modified))%></td></tr><%
        out.println();
    }
%></table>
<hr>
<%
    String href = resource.getHref(context);
%>
This is a WebDav enabled directory.<br>
<%
    ArrayList<String> can = new ArrayList<>();
    if (resource.canRead(user,false)) can.add("read");
    if (resource.canWrite(user,false)) can.add("update");
    if (resource.canCreate(user,false)) can.add("create");
    if (resource.canDelete(user,false)) can.add("delete");
    if (!can.isEmpty())
    {
        %>You have permission to <%
        String comma = "";
        for (int i=0 ; i<can.size() ; i++)
        {
            %><%=comma%><%=(i==can.size()-1 && i > 1) ? "and ":""%><%=can.get(i)%><%
            comma = ", ";
        }
        %> files in this directory.<br><%
    }
%>
<%
if (supportsDavMount) {%><%= button("davmount").href("?davmount") %><br><%}
if (supportsDavScheme) {%><%= button("dav").href(href.replace("http:","dav:")) %><br><%}
if (supportsWebdavScheme) {%><%= button("webdav").href(href.replace("http:","webdav:")) %><br><%}
%>
<!--<%=h(request.getHeader("user-agent"))%>-->
