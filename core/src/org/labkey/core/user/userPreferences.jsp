<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page import="org.labkey.api.security.UserUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<UserController.UserPreference> me = (JspView<UserController.UserPreference>) HttpView.currentView();
    UserController.UserPreference bean = me.getModelBean();

    ActionURL showGridLink = urlProvider(UserUrls.class).getSiteUsersURL();
    showGridLink.addParameter(".lastFilter", "true");
%>
<form action="" method="post">
    <table>
        <tr class="labkey-wp-header"><td colspan="2">Required Fields for User Information</td></tr>
    <%
        for (ColumnInfo info : bean.getColumns())
        {
    %>
        <tr><td><input type="checkbox" name="requiredFields" <%=isRequired(info.getName(), bean.getRequiredFields()) ? "checked " : ""%> value="<%=info.getName()%>"><%=info.getLabel()%></td></tr>
    <%
        }
    %>
        <tr><td></td></tr>
        <tr>
            <td><%=PageFlowUtil.generateButton("Show Grid", showGridLink)%>&nbsp;
                <%=PageFlowUtil.generateSubmitButton("Update")%></td>
        </tr>
    </table><br>
</form>

<%!
    public boolean isRequired(String name, String requiredFields)
    {
        if (requiredFields != null)
        {
            return requiredFields.indexOf(name) != -1;
        }
        return false;
    }
%>