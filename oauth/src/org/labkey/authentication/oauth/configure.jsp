<%
/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.authentication.oauth.OAuthController" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    PropertyManager.PropertyMap map = (PropertyManager.PropertyMap)HttpView.currentModel();
    String clientId = StringUtils.defaultString(map.get("client_id"), "");
    String clientSecret = StringUtils.defaultString(map.get("client_secret"), "");

    String redirectURI = new ActionURL(OAuthController.RedirectAction.class, ContainerManager.getRoot()).getURIString();
%>
<p>Configure your Google API credentials as described here: <a href="https://developers.google.com/accounts/docs/OpenIDConnect#getcredentials">Google Accounts</a></p>
<p>You should set your "REDIRECT URIS" to <b><%=h(redirectURI)%></b></p>

<labkey:form method="POST">
    <table>
        <tr><td><label for="clientId">Client Id</label></td><td><input id="clientId" name="clientId" size=80 value="<%=h(clientId)%>"></td></tr>
        <tr><td><label for="clientSecret">Client Secret</label></td><td><input id="clientSecret" name="clientSecret" size=40 value="<%=h(clientSecret)%>"></td></tr>
    </table>
    <input type="submit" value="Save">
</labkey:form>