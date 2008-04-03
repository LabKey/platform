<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.TestNetworkDriveBean" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%
    JspView<TestNetworkDriveBean> me = (JspView<TestNetworkDriveBean>) HttpView.currentView();
    TestNetworkDriveBean bean = me.getModelBean();

    String errors = PageFlowUtil.getStrutsError(request, "main");
%>
<span class="labkey-error"><%=errors%></span>

<% if (bean.getFiles() != null) {  %>
    <p><b>Success!</b></p>
    Drive contents:<br/>
    <ul>
        <% for (String file : bean.getFiles()) { %>
            <li><%= file %></li>
        <% } %>
    </ul>
<% } %>
