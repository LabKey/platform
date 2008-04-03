<%@ page import="org.labkey.announcements.AnnouncementsController.ConfirmDeleteView.DeleteBean" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<DeleteBean> me = (HttpView<DeleteBean>) HttpView.currentView();
    DeleteBean bean = me.getModelBean();
%>
Are you sure you want to delete this <%=h(bean.what)%> <%=h(bean.conversationName)%>: <b><%=h(bean.title)%></b>?
