<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.assay.dilution.DilutionAssayRun" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.assay.nab.Luc5Assay" %>
<%@ page import="java.text.DecimalFormat" %>
<%@ page import="org.labkey.api.assay.dilution.SampleInfo" %>
<%@ page import="org.labkey.api.study.WellData" %>
<%@ page import="org.labkey.api.assay.dilution.DilutionSummary" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.assay.nab.RenderAssayBean" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<RenderAssayBean> me = (JspView<RenderAssayBean>) HttpView.currentView();
    RenderAssayBean bean = me.getModelBean();
%>
<table cellspacing="5px">
    <tr>
        <%
            int count = 0;
            int maxPerRow = 5;
            for (DilutionAssayRun.SampleResult results : bean.getSampleResults())
            {
                DilutionSummary summary = results.getDilutionSummary();
        %>
        <td>
            <table class="labkey-data-region">
                <tr>
                    <td colspan="4" class="labkey-data-region-header-container" style="text-align:center;"><%= h(results.getCaption()) %></td>
                </tr>
                <tr>
                    <td align="right" style="text-decoration:underline"><%= summary.getMethod().getAbbreviation() %></td>
                    <td align="center" colspan="3"  style="text-decoration:underline">Neut.</td>
                </tr>
                <%
                    List<WellData> dataList = summary.getWellData();
                    for (int dataIndex = dataList.size() - 1; dataIndex >= 0; dataIndex--)
                    {
                        WellData data = dataList.get(dataIndex);
                        double dilution = summary.getDilution(data);
                        DecimalFormat shortDecFormat;
                        if (summary.getMethod() == SampleInfo.Method.Concentration || (dilution > -1 && dilution < 1))
                            shortDecFormat = new DecimalFormat("0.###");
                        else
                            shortDecFormat = new DecimalFormat("0");
                %>
                <tr>
                    <td align=right><%= shortDecFormat.format(dilution) %></td>
                    <td
                        align=right><%= Luc5Assay.percentString(summary.getPercent(data)) %></td>
                    <td>&plusmn;</td>
                    <td
                        align=right><%= Luc5Assay.percentString(summary.getPlusMinus(data)) %></td>
                </tr>
                <%
                    }
                %>
            </table>
        </td>
        <%
                if (++count % maxPerRow == 0)
                {
        %>
                    </tr><tr>
        <%
                }
            }
        %>
    </tr>
</table>