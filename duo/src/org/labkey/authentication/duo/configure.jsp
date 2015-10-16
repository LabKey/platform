<%
/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.authentication.duo.DuoController" %>
<%@ page import="org.labkey.authentication.duo.DuoController.Config" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Config> me = (JspView<Config>)HttpView.currentView();
    Config bean = me.getModelBean();
%>
<labkey:form action="configure.post" method="post">
    <table>
        <%=formatMissedErrorsInTable("form", 2)%>
        <%--'Integration key', 'Secret key', 'API hostname' was obtained by creating a "New Integration" (Integration type: Web SDK)--%>
        <%--by going to Duo's Admin Dashboard (need Duo account). 'Application key' was created via executing these commands from terminal (on Mac OSX):--%>
        <%--python--%>
        <%--import os, hashlib--%>
        <%--print hashlib.sha1(os.urandom(32)).hexdigest()--%>

        <tr>
            <td class="labkey-form-label">Integration Key<%= PageFlowUtil.helpPopup("Integration Key", "Your Labkey Admin with a Duo administrative account should have generated this key.")%></td>
            <td><input type="text" name="integrationKey" size="50" value="<%=h(bean.getIntegrationKey())%>"></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Secret Key<%= PageFlowUtil.helpPopup("Secret Key", "Your Labkey Admin with a Duo administrative account should have generated this key.")%></td>
            <td><input type="text" name="secretKey" size="50" value="<%=h(bean.getSecretKey())%>"></td>
        </tr>
        <tr>
            <td class="labkey-form-label">API Hostname<%=PageFlowUtil.helpPopup("API Hostname", "Your Labkey Admin with a Duo administrative account should have the hostname along with Integration and Secret Key.")%></td>
            <td><input type="text" name="apiHostname"  size="50" value="<%=h(bean.getApiHostname())%>"></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Option<%=PageFlowUtil.helpPopup("Option", "Choose the way LabKey will match with Duo accounts.")%></td>
            <td>
                <select name="option">
                    <option value="UserID" <%= selected("UserID".equals(bean.getOption()))%> >User ID</option>
                    <option value="UserName" <%= selected("UserName".equals(bean.getOption()))%> >User Name</option>
                    <option value="FullEmailAddress" <%= selected("FullEmailAddress".equals(bean.getOption()))%> >Full Email Address</option>
                </select>
            </td>
        </tr>
        <tr><td colspan="2">&nbsp;</td></tr>
        <tr>
            <td colspan=2>
                <%= button("Test").href(urlFor(DuoController.TestDuoAction.class).addReturnURL(getActionURL()))%>
            </td>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td colspan=2>
                <%= button("Save").submit(true) %>
                <%= button("Cancel").href(urlProvider(LoginUrls.class).getConfigureURL()) %>
            </td>
        </tr>
        <tr><td colspan="2">&nbsp;</td></tr>
    </table>
</labkey:form>