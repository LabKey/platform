<%
/*
 * Copyright (c) 2012-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.experiment.types.TypesController" %>
<%@ page import="org.labkey.api.exp.api.StorageProvisioner" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<labkey:errors/>
<%
TypesController.RepairForm form = (TypesController.RepairForm) HttpView.currentModel();
StorageProvisioner.ProvisioningReport.DomainReport report = form.report;

if (null == report)
{
    %>Could not generate report.  Contact LabKey support for more help.<%
}
else
{
    %><table><tr style="background-color:#cccccc;"><th align=left>Property</th><th align=left>mv</th><th align=left>Column</th><th align=left>mv Column</th><th>Proposed fix</th></tr><%
    for (StorageProvisioner.ProvisioningReport.ColumnStatus st : report.getColumns())
    {
        String fix = null;
        if (st.hasProblem)
        {
            %><tr style="background-color:#ffcccc;"><%
        }
        else if (null != st.spec)
        {
            %><tr style="background-color:#eeeeee;"><%
        }
        else
        {
            %><tr style=""><%
        }
        %><td><%=toCell(st.getName())%></td>
        <td><%=st.hasMv()?"X":"&nbsp;"%></td>
        <td><%=toCell(st.colName)%></td>
        <td><%=toCell(st.mvColName)%></td>
        <td><%=toCell(st.fix)%></td>
        </tr><%
    }
    %></table>

    <form method=POST><input type=submit name=submit value="FIX (NYI)"></form>

    <p></p>
    <div stype="border:solid 1px #888888;"><span class="labkey-error">errors</span><ul style="margin:0;"><%
    for (String error : report.getErrors())
    {
        if (-1 != error.indexOf("repair.view?")) continue;
        %><li><%=h(error)%></li><%
    }
    %></ul></div><%
}
%>
<%!
    String toCell(String s)
    {
        if (StringUtils.isEmpty(s))
            return "&nbsp;";
        return h(s);
    }
%>