<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.util.Search" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Search.SearchBean> me = ((JspView<Search.SearchBean>)HttpView.currentView());
    Search.SearchBean bean = me.getModelBean();
    Container c = me.getViewContext().getContainer();
%>
<form method="get" action="<%=bean.postURL%>"><%
    if (bean.showExplanatoryText)
    { %>
Search <%=h(bean.what)%> in this <%=(c.isProject() ? "project" : "folder")%><%
    } %>
<table>
<tr>
    <td colspan=2><input type="text" id="search" name="search" value="<%=h(bean.searchTerm)%>"<%=bean.textBoxWidth > 0 ? " size=\"" + bean.textBoxWidth + "\"" : ""%>></td><%

    if (bean.showSettings)
    { %>
</tr>
<tr>
    <td width="1"><input type="checkbox" name="includeSubfolders" value="on" <%=bean.includeSubfolders ? "checked" : ""%>></td><td>Search subfolders</td>
</tr>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>
<tr>
    <td colspan=2><input type="image" src="<%=PageFlowUtil.buttonSrc("Search")%>"></td>
    <%
    }
    else
    { %>
    <td><input type="hidden" name="includeSubfolders" value="<%=bean.includeSubfolders ? "on" : "off"%>"></td>
    <td colspan=2><input type="image" src="<%=PageFlowUtil.buttonSrc("Search")%>"></td>
    <%
    }
    %>
</tr>
</table>
</form>
