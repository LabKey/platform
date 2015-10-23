<%
/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.duo.DuoController" %>
<%@ page import="org.labkey.duo.DuoController.DuoForm" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DuoForm> me = (JspView<DuoForm>)HttpView.currentView();
    DuoForm form = me.getModelBean();
%>
<labkey:form name="testDuoResult" method="post" action="testDuoResult.post">
    <%--<labkey:csrf />--%>
    <table>
        <tr><td><h2>Duo Authentication Result</h2></td></tr>
        <%
            if(form.isStatus())
            {
        %>
                <tr><td font style="color: forestgreen" size="14"><b>Test Success!</b></td></tr>
        <%
            }
            else
            {
        %>
                <tr><td font style="color: red" size="14"><b>Test Failed!</b></td></tr>
        <%
            }
        %>
        <tr><td>&nbsp;</td></tr>
        <tr><td> <%= button("Back to Configuring Duo").href(DuoController.getConfigureURL())%></td></tr>

  </table>
</labkey:form>

