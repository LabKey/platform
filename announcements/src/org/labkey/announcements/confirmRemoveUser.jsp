<%@ page import="org.labkey.announcements.AnnouncementsController.RemoveUserView.RemoveUserBean" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<RemoveUserBean> me = (HttpView<RemoveUserBean>) HttpView.currentView();
    RemoveUserBean bean = me.getModelBean();
%>
Are you sure you want to remove yourself (<%=h(bean.email)%>) from the member list of this <%=h(bean.conversationName)%>?
<p/>
<b>Title: <%=(bean.title)%></b>
