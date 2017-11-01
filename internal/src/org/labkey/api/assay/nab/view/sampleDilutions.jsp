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
<%@ page import="org.labkey.api.assay.dilution.DilutionSummary" %>
<%@ page import="org.labkey.api.assay.dilution.SampleInfoMethod" %>
<%@ page import="org.labkey.api.assay.nab.Luc5Assay" %>
<%@ page import="org.labkey.api.assay.nab.RenderAssayBean" %>
<%@ page import="org.labkey.api.study.WellData" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.text.DecimalFormat" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<RenderAssayBean> me = (JspView<RenderAssayBean>) HttpView.currentView();
    RenderAssayBean bean = me.getModelBean();
%>
<style type="text/css">
    .lk-sample-dilutions-table {
        border: solid #d3d3d3 1px;
    }

    .lk-sample-dilutions-table td {
        padding: 0 3px;
    }

    .lk-sample-dilutions-header {
        background-color: #eeeeee;
        border: solid #d3d3d3 1px;
        padding: 5px !important;
    }
</style>

<table>
    <tr>
        <%
            int count = 0;
            int maxPerRow = 5;
            for (DilutionAssayRun.SampleResult results : bean.getSampleResults())
            {
                DilutionSummary summary = results.getDilutionSummary();
        %>
        <td style="padding: 0 10px 10px 0;" valign="top">
            <table class="labkey-data-region-legacy lk-sample-dilutions-table">
                <tr>
                    <td colspan="4" align="center" class="lk-sample-dilutions-header">
                        <%= h(results.getCaption(bean.getDataIdentifier())) %></div>
                    </td>
                </tr>
                <tr>
                    <td align="right" style="text-decoration:underline; padding-right: 10px;"><%= summary.getMethod().getAbbreviation() %></td>
                    <td align="center" colspan="3"  style="text-decoration:underline"><%=h(bean.getNeutralizationAbrev())%></td>
                </tr>
                <%
                    List<WellData> dataList = summary.getWellData();
                    for (int dataIndex = dataList.size() - 1; dataIndex >= 0; dataIndex--)
                    {
                        WellData data = dataList.get(dataIndex);
                        double dilution = summary.getDilution(data);
                        DecimalFormat shortDecFormat;
                        if (summary.getMethod() == SampleInfoMethod.Concentration || (dilution > -1 && dilution < 1))
                            shortDecFormat = new DecimalFormat("0.###");
                        else
                            shortDecFormat = new DecimalFormat("0");
                %>
                <tr>
                    <td align="right" style="padding-right: 10px;"><%= shortDecFormat.format(dilution) %></td>
                    <td align="right"><%= Luc5Assay.percentString(summary.getPercent(data)) %></td>
                    <td>&plusmn;</td>
                    <td align="right"><%= Luc5Assay.percentString(summary.getPlusMinus(data)) %></td>
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