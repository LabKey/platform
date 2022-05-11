<%
/*
 * Copyright (c) 2022 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
%>
<%@ page import="org.labkey.api.admin.AdminUrls"%>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission"%>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.core.user.LimitActiveUsersSettings" %>
<%@ page import="org.labkey.core.user.UserController.LimitActiveUsersAction" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    LimitActiveUsersSettings settings = new LimitActiveUsersSettings();
    // Troubleshooters and App Admins can view these settings, but only Site Admins can save them
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
%>
<labkey:form action="<%=urlFor(LimitActiveUsersAction.class)%>" method="post">
    <table>
        <%=formatMissedErrorsInTable("form", 2)%>
        <tr>
            <td class="labkey-form-label">Warn based on the number of active users</td>
            <td><select name="userWarning" id="userWarning">
                <%  boolean userWarning = settings.isUserWarning(); %>
                <option value="0"<%=selected(!userWarning)%>>No</option>
                <option value="1"<%=selected(userWarning)%>>Yes</option>
            </select></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Warning level user count</td>
            <td><input type="text" name="userWarningLevel" id="userWarningLevel" value="<%=settings.getUserWarningLevel()%>" size="6"></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Warning level message</td>
            <td><textarea id="userWarningMessage" name="userWarningMessage" cols="100" rows="3"><%=h(settings.getUserWarningMessage())%></textarea></td>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td class="labkey-form-label">Limit the number of active users</td>
            <td><select name="userLimit" id="limitActiveUsers">
                <%  boolean userLimit = settings.isUserLimit(); %>
                <option value="0"<%=selected(!userLimit)%>>No</option>
                <option value="1"<%=selected(userLimit)%>>Yes</option>
            </select></td>
        </tr>
        <tr>
            <td class="labkey-form-label">User limit</td>
            <td><input type="text" name="userLimitLevel" id="userLimitLevel" value="<%=settings.getUserLimitLevel()%>" size="6"></td>
        </tr>
        <tr>
            <td class="labkey-form-label">User limit message</td>
            <td><textarea id="userLimitMessage" name="userLimitMessage" cols="100" rows="3"><%=h(settings.getUserLimitMessage())%></textarea></td>
        </tr>
        <tr><td colspan="2">&nbsp;</td></tr>
        <tr>
            <td colspan=2>
                <%= hasAdminOpsPerms ? button("Save").submit(true) : HtmlString.EMPTY_STRING %>
                <%= button("Cancel").href(urlProvider(AdminUrls.class).getAdminConsoleURL())%>
            </td>
        </tr>
        <tr><td colspan="2">&nbsp;</td></tr>
    </table>
</labkey:form>

<%
    Map<String, Integer> props = LimitActiveUsersSettings.getPropertyMap();
%>
The two messages above must be well-formed, properly escaped HTML. The messages support string substitution of specific
user limit properties. For example, the message:<br><br>

<code>&nbsp;&nbsp;You can add or reactivate \${RemainingUsers} more users</code><br><br>

will currently result in this displayed text:<br><br>

<code>&nbsp;&nbsp;You can add or reactivate <%=props.get("RemainingUsers")%> more users</code><br><br>

The supported properties and their current values are listed in the table below.<br><br>
<table class="labkey-data-region-legacy labkey-show-borders">
    <tr class="labkey-frame"><th>Property</th><th>Current Value</th></tr>

    <%=unsafe(
        props.entrySet().stream()
            .map(e -> "<tr valign=top class=\"labkey-row\"><td>" + h(e.getKey()) + "</td><td>" + h(e.getValue()) + "</td></tr>\n")
            .collect(Collectors.joining()))%>

</table>