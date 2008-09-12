<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.actions.PublishConfirmAction" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PublishConfirmAction.PublishConfirmBean> me = (JspView<PublishConfirmAction.PublishConfirmBean>) HttpView.currentView();
    PublishConfirmAction.PublishConfirmBean bean = me.getModelBean();
%>
<labkey:errors/>
Note: Participant and <%= bean.isDateBased() ? "Date" : "Visit ID" %> are required for all rows.