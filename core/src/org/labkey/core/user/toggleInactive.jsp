<%
/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String caption;
    UserController.ShowUsersForm form = (UserController.ShowUsersForm) HttpView.currentModel();
    ActionURL url = HttpView.currentContext().getActionURL().clone();
    if(!form.isInactive())
    {
        url.addParameter("inactive", true);
        caption = "include inactive users";
    }
    else
    {
        url.deleteParameter("inactive");
        caption = "hide inactive users";
    }
%>
<table>
    <tr>
        <td>
            <%=textLink("caption", url.getLocalURIString())%>
        </td>
    </tr>
</table>
