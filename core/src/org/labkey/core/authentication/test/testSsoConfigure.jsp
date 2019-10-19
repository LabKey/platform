<%
/*
 * Copyright (c) 2017 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.authentication.test.TestSsoController.TestSsoConfigureForm" %>
<%@ page import="org.labkey.core.authentication.test.TestSsoProvider" %>
<%@ page import="org.springframework.web.servlet.ModelAndView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<TestSsoConfigureForm> me = (JspView<TestSsoConfigureForm>)HttpView.currentView();
    TestSsoConfigureForm form = me.getModelBean();

    boolean enabled = true;
    String name = TestSsoProvider.NAME;
%>
<%=formatMissedErrorsInTable("form", 2)%>
<labkey:form enctype="multipart/form-data" action="configure.post" method="post" layout="horizontal">
    <input type="hidden" name="configuration" value="<%=h(form.getConfiguration())%>">
    <labkey:input type="text" name="name" size="50" value="<%=h(name)%>" label="Name" />
    <labkey:input type="checkbox" name="enabled" label="Enabled" checked="<%=enabled%>" />
    <%
        SSOAuthenticationConfiguration configuration = AuthenticationManager.getActiveSSOConfiguration(form.getConfiguration());
        ModelAndView view = urlProvider(LoginUrls.class).getPickLogosView(configuration, false, null);
        include(view, out);
    %>
    <br/>
    <%= button("Save").submit(true) %>
    <%= button("Cancel").href(form.getReturnActionURL(urlProvider(LoginUrls.class).getConfigureURL()))%>
</labkey:form>