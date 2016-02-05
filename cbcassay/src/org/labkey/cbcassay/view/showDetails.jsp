<%
/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.UpdatePermission"%>
<%@ page import="org.labkey.cbcassay.CBCAssayProvider" %>
<%@ page import="org.labkey.cbcassay.data.CBCData" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    CBCData data = null;

    public String renderRow(String prop)
    {
        StringBuffer sb = new StringBuffer();
        sb.append("<tr>\n");
        sb.append("<td width='60' align='center'>").append(data.getLabel(prop)).append("</td>\n");
        sb.append("<td align='center' style='font-family:monospace;");
        if (!data.inRange(prop))
            sb.append("font-style:italic' class='labkey-form-label");
        sb.append("'>").append(data.getValue(prop)).append("</td>\n");
        sb.append("<td align='center' style='font-family:monospace;'>( ").append(data.getMinValue(prop)).append(" - ").append(data.getMaxValue(prop)).append(" )</td>\n");
        sb.append("<td>").append(data.getUnits(prop)).append("</td>\n");
        sb.append("</tr>\n");
        return sb.toString();
    }
%>
<%
    data = (CBCData)this.getModelBean();

    boolean canEdit = getViewContext().hasPermission(UpdatePermission.class);
%>

<table>
    <tr>
      <td class='labkey-form-label'>SampleID</td><td><%=data.getSampleId()%></td>
    </tr>
    <tr>
      <td class='labkey-form-label'>Sequence</td><td><%=data.getSequence()%></td>
    </tr>
    <tr>
      <td class='labkey-form-label'>Run Date</td><td><%=data.getDate()%></td>
    </tr>
</table>
<table style='border-top: solid 1px gray; min-width:400'>
    <tr>
      <th>Test</th>
      <th>Result</th>
      <th>Normals</th>
      <th>Units</th>
    </tr>
    <%
        String[] properties = new String[] {"WBC", "RBC", "HGB", "HCT", "MCV", "MCH", "PLT", "MPV" };
        for (String prop : properties)
        {
            %><%=renderRow(prop)%><%
        }
    %>
    <tr><td colspan="4">&nbsp;</td></tr>
    <%
        properties = new String[] {"PercentNEUT", "PercentLYMPH", "PercentMONO", "PercentEOS", "PercentBASO", "PercentLUC"};
        for (String prop : properties)
        {
            %><%=renderRow(prop)%><%
        }
    %>
    <tr><td colspan="4">&nbsp;</td></tr>
    <%
        properties = new String[] {"AbsNEUT", "AbsLYMPH", "AbsMONO", "AbsEOS", "AbsBASO", "AbsLUC"};
        for (String prop : properties)
        {
            %><%=renderRow(prop)%><%
        }
    %>
    <tr><td colspan="4">&nbsp;</td></tr>
    <%=renderRow("PercentTotalLYMPH")%>
    <%=renderRow("AbsTotalLYMPH")%>
</table>

<% if (canEdit) { %>
    <%= button("Edit").href(CBCAssayProvider.getResultUpdateUrl(getViewContext())) %>
<% } %>

