<%@ page import="org.labkey.announcements.AnnouncementsController" %><%@ page import="org.labkey.announcements.model.Announcement" %><%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.announcements.DailyDigestPage" %>The following new posts were made yesterday in folder: <%=c.getPath()%>

<%
    String previousThread = null;
    String threadUrl = null;

    for (Announcement ann : announcements)
    {
        if (null == ann.getParent() || !ann.getParent().equals(previousThread))
        {
            if (null == ann.getParent())
                previousThread = ann.getEntityId();
            else
                previousThread = ann.getParent();

            if (null != threadUrl)
            {
                %>
View this <%=conversationName%> here:

<%=threadUrl%>


<%
            }

            threadUrl = AnnouncementsController.getThreadURL(c, previousThread, ann.getRowId()).getURIString();
%><%=ann.getTitle()%>

<%
        }
%><%=ann.getCreatedByName(includeGroups, HttpView.currentContext())%><% if (null == ann.getParent()) { %> created this <%=conversationName%><% } else { %> responded<% } %> at <%=DateUtil.formatDateTime(ann.getCreated())%><%

        int attachmentCount = ann.getAttachments().size();

        if (attachmentCount > 0)
            out.println(" and attached " + attachmentCount + " document" + (attachmentCount > 1 ? "s" : ""));
        else
            out.println();
    }

    if (null != threadUrl)
    {
       %>
View this <%=conversationName%> here:

<%=threadUrl%>

<%  }  %>


You have received this email because you are signed up for a daily digest of new posts to <%=boardPath%> at <%=siteUrl%>.
If you no longer wish to receive these notifications, please change your email preferences here: <%=removeUrl%>