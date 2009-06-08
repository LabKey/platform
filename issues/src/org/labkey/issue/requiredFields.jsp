<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.labkey.issue.IssuesController"%>
<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.util.HString" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuesController.IssuesPreference> me = (JspView<IssuesController.IssuesPreference>) HttpView.currentView();
    IssuesController.IssuesPreference bean = me.getModelBean();
%>
<form action="updateRequiredFields.view" method="post" name="requiredFieldsForm">
    <table>
        <tr><td colspan=2 align=center><div class="labkey-form-label"><b>Required Fields for <%=bean.getEntryTypeNames().pluralName%></b></div></td></tr>
        <tr><td colspan=2>Select fields to be required when entering or updating <%=bean.getEntryTypeNames().getIndefiniteSingularArticle()%> <%=bean.getEntryTypeNames().singularName%>:</td></tr>
    <%
        List<ColumnInfo> columns = bean.getColumns();
        for (int i = 0; i < columns.size(); i++)
        {
            ColumnInfo info = columns.get(i);
            boolean startNewRow = i % 2 == 0;
            if (startNewRow)
            {
    %>
        <tr>
    <%
            }
    %>
            <td><input type="checkbox" name="requiredFields" <%=isRequired(info.getName(), bean.getRequiredFields()) ? "checked " : ""%> value="<%=info.getName()%>"><%=getCaption(info)%></td>
    <%
            if (!startNewRow)
            {
    %>
        </tr>
    <%
            }
        }
    %>
        <tr><td></td></tr>
        <tr>
            <td colspan="2"><%=PageFlowUtil.generateSubmitButton("Update Required Fields")%></td>
        </tr>
    </table><br>
</form>

<%!
    public boolean isRequired(String name, HString requiredFields) {
        if (requiredFields != null) {
            return requiredFields.indexOf(name.toLowerCase()) != -1;
        }
        return false;
    }

    public String getCaption(ColumnInfo col) throws SQLException
    {
        final IssueManager.CustomColumnConfiguration ccc = IssueManager.getCustomColumnConfiguration(HttpView.getRootContext().getContainer());
        if (ccc.getColumnCaptions().containsKey(col.getName()))
        {
            return ccc.getColumnCaptions().get(col.getName());
        }
        return col.getCaption();
    }
%>
