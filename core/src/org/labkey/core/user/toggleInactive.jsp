<%
/*
 * Copyright (c) 2008-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String inactiveCaption;
    String temporaryCaption;
    UserController.ShowUsersForm form = (UserController.ShowUsersForm) HttpView.currentModel();
    ActionURL inactiveUrl = getViewContext().cloneActionURL();
    ActionURL temporaryUrl = getViewContext().cloneActionURL();

    if (!form.isInactive())
    {
        inactiveUrl.addParameter("inactive", true);
        inactiveCaption = "include inactive users";
    }
    else
    {
        inactiveUrl.deleteParameter("inactive");
        inactiveCaption = "hide inactive users";
    }

    if (!form.isTemporary())
    {
        temporaryUrl.addParameter("temporary", true);
        temporaryCaption = "show temporary accounts";
    }
    else
    {
        temporaryUrl.deleteParameter("temporary");
        temporaryCaption = "show all accounts";
    }

    boolean showTemporaryLink = getContainer().isRoot() && AuthenticationManager.isAccountExpirationEnabled();

%>
<table>
    <tr>
        <td>
            <%=textLink(inactiveCaption, inactiveUrl)%>
        </td>
    </tr>

    <% if (showTemporaryLink)
    {
    %>
    <tr>
        <td>
            <%=textLink(temporaryCaption, temporaryUrl)%>
        </td>
    </tr>
    <%
    }
    %>

</table>
