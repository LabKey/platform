<%@ page import="org.labkey.announcements.AnnouncementsController.RssView.RssBean" %>
<%@ page import="org.labkey.announcements.model.Announcement" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<RssBean> me = (HttpView<RssBean>) HttpView.currentView();
    RssBean bean = me.getModelBean();
%>
<rss version="2.0">
<channel>
    <title><%=h(bean.app.getSystemShortName())%>: <%=h(bean.app.getSystemDescription())%></title>
    <link><%=h(ActionURL.getBaseServerURL())%></link>
    <description><%=h(bean.app.getSystemShortName())%>: <%=h(bean.app.getSystemDescription())%></description>
<%
    for (org.labkey.announcements.model.Announcement ann : bean.announcements)
    {%>
    <item>
        <title><%=h(ann.getTitle())%></title>
        <link><%=h(bean.url)%><%=ann.getRowId()%>&amp;_print=1</link>
        <description><%=h(ann.getBody())%></description>
        <pubDate><%=ann.getCreated()%></pubDate>
    </item><%
    }%>
</channel>
</rss>
