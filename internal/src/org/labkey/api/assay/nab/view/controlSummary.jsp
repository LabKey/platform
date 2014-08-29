<%
/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.dilution.DilutionAssayRun" %>
<%@ page import="org.labkey.api.assay.nab.Luc5Assay" %>
<%@ page import="org.labkey.api.assay.nab.RenderAssayBean" %>
<%@ page import="org.labkey.api.study.Plate" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<RenderAssayBean> me = (JspView<RenderAssayBean>) HttpView.currentView();
    RenderAssayBean bean = me.getModelBean();
    DilutionAssayRun assay = bean.getAssay();
    Map<String, Object> virusNames = assay.getVirusNames();

    // Lay out the table vertically, rather than horizontally, if we have more than one plate
    // to keep the table from getting too wide.
    if (assay.getPlates().size() > 1)
    {
%>
<table width="100%" class="labkey-data-region labkey-show-borders">
    <tr>
        <th>Plate</th>
        <th>Range</th>
        <th>Virus Control</th>
        <th>Cell Control</th>
    </tr>
<%
        int plateNum = 1;
        for (Plate plate : assay.getPlates())
        {
%>
    <tr>
        <td style="font-weight:bold"><%= plateNum++ %></td>
        <td align=left><%=Luc5Assay.intString(assay.getControlRange(plate, null))%></td>
        <td align="left"><%=Luc5Assay.intString(assay.getVirusControlMean(plate, null))%> &plusmn;
            <%=Luc5Assay.percentString(assay.getVirusControlPlusMinus(plate, null))%></td>
        <td align=left><%=Luc5Assay.intString(assay.getCellControlMean(plate, null))%> &plusmn;
            <%=Luc5Assay.percentString(assay.getCellControlPlusMinus(plate, null))%></td>
    </tr>
<%
        }
%>
</table>
<%
    }
    else
    {
        Plate plate = assay.getPlates().get(0);
        if (virusNames.isEmpty())
        {
%>
<table>
    <tr>
        <th style="text-align:left">Range</th>
        <td align=left><%=Luc5Assay.intString(assay.getControlRange(plate, null))%></td>
    </tr>
    <tr>
        <th style="text-align:left">Virus Control</th>
        <td align="left"><%=Luc5Assay.intString(assay.getVirusControlMean(plate, null))%> &plusmn;
            <%=Luc5Assay.percentString(assay.getVirusControlPlusMinus(plate, null))%></td>
    </tr>
    <tr>
        <th style="text-align:left">Cell Control</th>
        <td align=left><%=Luc5Assay.intString(assay.getCellControlMean(plate, null))%> &plusmn;
            <%=Luc5Assay.percentString(assay.getCellControlPlusMinus(plate, null))%></td>
    </tr>
</table>
<%
        }
        else
        {
%>
<table>
    <tr>
        <th style="text-align:left">Virus</th>
<%
        for (Map.Entry<String, Object> virusEntry : virusNames.entrySet())
        {
%>
        <td align=left><%=h(virusEntry.getValue())%></td>
<%
        }
%>
    </tr>
    <tr>
        <th style="text-align:left">Range</th>
<%
        for (Map.Entry<String, Object> virusEntry : virusNames.entrySet())
        {
%>
        <td align=left><%=Luc5Assay.intString(assay.getControlRange(plate, virusEntry.getKey()))%></td>
<%
        }
%>
    </tr>
    <tr>
        <th style="text-align:left">Virus Control</th>
<%
        for (Map.Entry<String, Object> virusEntry : virusNames.entrySet())
        {
%>
        <td align="left"><%=Luc5Assay.intString(assay.getVirusControlMean(plate, virusEntry.getKey()))%> &plusmn;
            <%=Luc5Assay.percentString(assay.getVirusControlPlusMinus(plate, virusEntry.getKey()))%></td>
<%
        }
%>
    </tr>
    <tr>
        <th style="text-align:left">Cell Control</th>
<%
        for (Map.Entry<String, Object> virusEntry : virusNames.entrySet())
        {
%>
        <td align=left><%=Luc5Assay.intString(assay.getCellControlMean(plate, virusEntry.getKey()))%> &plusmn;
            <%=Luc5Assay.percentString(assay.getCellControlPlusMinus(plate, virusEntry.getKey()))%></td>
<%
        }
%>
    </tr>

</table>
<%
        }
%>
<%
    }
%>
