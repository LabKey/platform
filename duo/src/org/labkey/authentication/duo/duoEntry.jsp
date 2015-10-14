<%
/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
%>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.authentication.duo.DuoController.DuoForm" %>
<%@ page import="org.labkey.authentication.duo.DuoManager" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("duo"));// These are equivalent as Client Dependencies without a suffix are assumed to be .lib.xml
        return resources;
    }
%>
<%
    String sig_request = ((DuoForm) HttpView.currentView().getModelBean()).getSig_request();
    JspView<DuoForm> me = (JspView<DuoForm>)HttpView.currentView();
    DuoForm form = me.getModelBean();
    String message;
    if(form.isTest())
    {
        message = "Use this page to test Duo Two-Factor authentication setting. This requires Duo Integration account. See your network administrator if you are unfamiliar with Duo.";
    }
    else
    {
        message = "Please complete the Two-Factor Authentication below:";
    }
%>
<p><h4><%=h(message)%></h4></p>

<iframe id="duo_iframe" width="620" height="330" frameborder="0"></iframe>

<%
    if(StringUtils.isNotBlank(sig_request))
    {
%>
    <script>

        postActionURL = "";

        if(<%=form.isTest()%>)
        {
            postActionURL=LABKEY.ActionURL.buildURL("duo", "testDuoResult.view");
        }

        Duo.init({
            'host': "<%=text(DuoManager.getAPIHostname())%>",
            'sig_request': "<%=text(sig_request)%>" ,
            'post_action': postActionURL
        });
    </script>
<%
    }
%>

