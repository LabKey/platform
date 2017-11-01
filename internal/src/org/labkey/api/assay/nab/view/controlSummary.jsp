<%
/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
<table class="labkey-data-region-legacy labkey-show-borders">
    <tr>
        <td class="labkey-column-header">Plate</td>
        <td class="labkey-column-header">Range</td>
        <td class="labkey-column-header">Virus Control</td>
        <td class="labkey-column-header">Cell Control</td>
    </tr>
<%
        int plateNum = 1;
        for (Plate plate : assay.getPlates())
        {
%>
    <tr class="<%=h(plateNum % 2 == 1 ? "labkey-alternate-row" : "labkey-row")%>">
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
<table class="labkey-data-region-legacy labkey-show-borders">
    <tr class="labkey-row">
        <td style="text-align:left; font-weight: bold;">Range</td>
        <td align=left><%=Luc5Assay.intString(assay.getControlRange(plate, null))%></td>
    </tr>
    <tr class="labkey-alternate-row">
        <td style="text-align:left; font-weight: bold;">Virus Control</td>
        <td align="left"><%=Luc5Assay.intString(assay.getVirusControlMean(plate, null))%> &plusmn;
            <%=Luc5Assay.percentString(assay.getVirusControlPlusMinus(plate, null))%></td>
    </tr>
    <tr class="labkey-row">
        <td style="text-align:left; font-weight: bold;">Cell Control</td>
        <td align=left><%=Luc5Assay.intString(assay.getCellControlMean(plate, null))%> &plusmn;
            <%=Luc5Assay.percentString(assay.getCellControlPlusMinus(plate, null))%></td>
    </tr>
</table>
<%
        }
        else
        {
%>
<table class="labkey-data-region-legacy labkey-show-borders">
    <tr class="labkey-row">
        <td style="text-align:left; font-weight: bold;">Virus</td>
<%
        for (Map.Entry<String, Object> virusEntry : virusNames.entrySet())
        {
%>
        <td align=left><%=h(virusEntry.getValue())%></td>
<%
        }
%>
    </tr>
    <tr class="labkey-alternate-row">
        <td style="text-align:left; font-weight: bold;">Range</td>
<%
        for (Map.Entry<String, Object> virusEntry : virusNames.entrySet())
        {
%>
        <td align=left><%=Luc5Assay.intString(assay.getControlRange(plate, virusEntry.getKey()))%></td>
<%
        }
%>
    </tr>
    <tr class="labkey-row">
        <td style="text-align:left; font-weight: bold;">Virus Control</td>
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
    <tr class="labkey-alternate-row">
        <td style="text-align:left; font-weight: bold;">Cell Control</td>
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
