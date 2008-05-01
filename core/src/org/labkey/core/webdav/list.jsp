<%@ page import="org.apache.commons.lang.time.FastDateFormat" %>
<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.webdav.WebdavResolver" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.core.webdav.DavController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz", TimeZone.getTimeZone("GMT"), Locale.US);
%>
<%
    DavController.ListPage listpage = (DavController.ListPage) HttpView.currentModel();
    WebdavResolver.Resource resource = listpage.resource;
    String path = resource.getPath();
    ViewContext context = HttpView.currentContext();
    AppProps app = AppProps.getInstance();
    User user = context.getUser();
%>
<html>
<head>
<title><%=h(path)%> -- WebDAV: <%=h(app.getServerName())%></title>
</head>
<style type="text/css">
A {text-decoration:none;}
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
            %><%=dir.getHref(context)%><%
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
    if (resource.parent() != null)
        dirs.put("..", resource.parent());
    if (resource.canRead(user))
    {
        for (WebdavResolver.Resource info : resource.list())
        {
            if (!info.canRead(user))
                continue;
            else if (info.isCollection())
                dirs.put(info.getName(), info);
            else
                files.put(info.getName(), info);
        }
    }

    boolean shade = true;
    for (Map.Entry<String, WebdavResolver.Resource> entry : dirs.entrySet())
    {
        String name = entry.getKey();
        WebdavResolver.Resource info = entry.getValue();
        shade = !shade;
        long modified = info.getLastModified();
        if (!"..".equals(name) && !".".equals(name))
            name += "/";
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
        %><tr bgcolor="<%=shade?"#ffffff":"#eeeeee"%>"><td align="left"><a href="<%=h(info.getLocalHref(context))%>"><%=h(name)%></a></td><%
        %><td align="right"><%=info.getContentLength()%></td><%
        %><td align="right" nowrap><%=modified==0?"&nbsp;":dateFormat.format(new Date(modified))%></td></tr><%
        out.println();
    }
%></table>
<hr>
This is a WebDav enabled directory.<br>
<%
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
</body>
</html>