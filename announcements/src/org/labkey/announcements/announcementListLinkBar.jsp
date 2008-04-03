<%@ page import="org.labkey.announcements.AnnouncementsController.ListLinkBar.ListBean" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<ListBean> me = (HttpView<ListBean>) HttpView.currentView();
    ListBean bean = me.getModelBean();
%>
<!--ANNOUNCEMENTS-->
<table width="100%">
<tr>
<td align="left" class="normal" style="padding-top:2px;width:33%;"><%
if (null != bean.insertURL)
    {
    %>[<a href="<%=bean.insertURL%>">new <%=h(bean.settings.getConversationName().toLowerCase())%></a>]&nbsp;<%
    }
%></td>
<td align="center" class="normal" style="padding-top:2px;width:33%;"><%=h(bean.filterText)%></td>
<td align="right" class="normal"  style="padding-top:2px;width:33%;"><%
if (null != bean.emailPrefsURL)
    {
    %>[<a href="<%=bean.emailPrefsURL%>">email&nbsp;preferences</a>]<%
    }
if (null != bean.emailManageURL)
    {
    %>&nbsp;[<a href="<%=bean.emailManageURL%>">email&nbsp;admin</a>]<%
    }
if (null != bean.customizeURL)
    {
    %>&nbsp;[<a href="<%=bean.customizeURL%>">customize</a>]<%
    }
%></td>
</tr><%
if (null != bean.urlFilterText)
{
    %><tr><td colspan=3><br>Filter: <%=h(bean.urlFilterText)%></td></tr><%
}
%></table>
