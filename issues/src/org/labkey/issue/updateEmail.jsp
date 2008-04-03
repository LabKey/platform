<%@ page import="org.labkey.issue.model.Issue"
%><%@ page import="java.util.Iterator"
%><%@ page import="org.labkey.issue.IssuesController"
%><%@ page import="org.labkey.api.view.HttpView"
%><%@ page import="org.labkey.api.view.JspView" 
%><%@ page extends="org.labkey.api.jsp.JspBase"
%><%
    JspView<IssuesController.UpdateEmailPage> me = (JspView<IssuesController.UpdateEmailPage>)HttpView.currentView();
    IssuesController.UpdateEmailPage bean = me.getModelBean();
    String changeComment = "(No change comment)";
    String modifiedBy = "(unknown)";
    Iterator<Issue.Comment> it = bean.issue.getComments().iterator();
    Issue.Comment lastComment = null;
    while (it.hasNext())
        lastComment = it.next();

    if (lastComment != null)
    {
        modifiedBy = lastComment.getCreatedByName(me.getViewContext());
        changeComment = lastComment.getComment();
    }

    if (bean.isPlain)
    {
        %>You can review this issue from this URL: <%=bean.url%><%
    }
    else
    {
        %>You can review this issue here: <a href="<%=h(bean.url)%>"><%=h(bean.url)%></a><br/><%
        %>Modified by: <%=h(modifiedBy)%><br/><%
        %><%=changeComment%><%
    }
%>