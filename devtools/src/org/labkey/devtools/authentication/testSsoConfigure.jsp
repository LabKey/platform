<%
/*
 * Copyright (c) 2017 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.action.SpringActionController" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.devtools.authentication.TestSsoController.TestSsoConfigureForm" %>
<%@ page import="org.springframework.web.servlet.ModelAndView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<TestSsoConfigureForm> me = (JspView<TestSsoConfigureForm>)HttpView.currentView();
    TestSsoConfigureForm form = me.getModelBean();
    boolean canEdit = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
%>
<%=formatMissedErrorsInTable("form", 2)%>
<labkey:form enctype="multipart/form-data" action="configure.post" method="post" layout="horizontal">
    <input type="hidden" name="configuration" value="<%=h(form.getConfiguration())%>">
    <labkey:input type="text" name="description" size="50" value="<%=h(form.getDescription())%>" label="Description" />
    <labkey:input type="checkbox" name="enabled" label="Enabled" checked="<%=form.isEnabled()%>" />
    <input type="hidden" name="<%=SpringActionController.FIELD_MARKER%>enabled">
    <%
        ModelAndView view = urlProvider(LoginUrls.class).getPickLogosView(form.getRowId(), false, false, null);
        include(view, out);
    %>
    <br/>
    <%=canEdit ? button("Save").submit(true) : ""%>
    <%=button(canEdit ? "Cancel" : "Done").href(form.getReturnActionURL(urlProvider(LoginUrls.class).getConfigureURL()))%>
</labkey:form>