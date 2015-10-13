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
<%@ page import="org.labkey.authentication.cas.CasController.CasConfigureForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<CasConfigureForm> me = (JspView<CasConfigureForm>)HttpView.currentView();
    CasConfigureForm form = me.getModelBean();
%>
<labkey:form action="configure.post" method="post">
    <table>
        <%=formatMissedErrorsInTable("form", 2)%>
        <tr>
            <td class="labkey-form-label">Apereo CAS Server URL<%= PageFlowUtil.helpPopup("Server URL", "Enter a valid HTTP URL to your Apereo CAS server. The URL should end with \"/cas\", for example: http://test.org/cas")%></td>
            <td><input type="text" name="serverUrl" size="50" value="<%=h(form.getServerUrl())%>"></td>
        </tr>
        <tr><td colspan="2">&nbsp;</td></tr>
        <tr>
            <td colspan=2>
                <%= button("Save").submit(true) %>
                <%= button("Cancel").href(form.getReturnActionURL(urlProvider(LoginUrls.class).getConfigureURL()))%>
            </td>
        </tr>
        <tr><td colspan="2">&nbsp;</td></tr>
    </table>
</labkey:form>