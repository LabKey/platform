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
<%@ page import="org.labkey.api.study.Plate" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.assay.nab.RenderAssayBean" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<RenderAssayBean> me = (JspView<RenderAssayBean>) HttpView.currentView();
    RenderAssayBean bean = me.getModelBean();
    DilutionAssayRun assay = bean.getAssay();
    ViewContext context = me.getViewContext();
%>
<table cellspacing="5px">
<%
    int plateIndex = 0;
    List<Plate> plates = assay.getPlates();
    boolean multiPlate = plates.size() > 1;
    for (Plate plate : plates)
    {
%>
<tr>
    <td valign=top>
        <table class="labkey-data-region labkey-show-borders">
            <%
                if (multiPlate)
                {
            %>
            <tr>
                <td class="labkey-data-region-header-container" style="text-align:center;" colspan="<%= plate.getColumns() + 1 %>">
                    Plate <%= ++plateIndex %>
                </td>
            </tr>
            <%
                }
            %>
            <tr>
                <td>&nbsp;</td>
                <%
                    for (int c = 1; c <= plate.getColumns(); c++)
                    {
                %>
                <td style="border-bottom:1px solid;text-align:center;"><%=c %></td>
                <%
                    }
                %>
            </tr>
            <%
                for (int row = 0; row < plate.getRows(); row++)
                {
            %>
            <tr>
                <td style="border-right:1px solid"><%=(char) ('A' + row)%></td>

                <%
                    for (int col = 0; col < plate.getColumns(); col++)
                    {
                %>
                <td align=right>
                    <%=Luc5Assay.intString(plate.getWell(row, col).getValue())%></td>
                <%
                    }
                %>
            </tr>
            <%
                }
            %>
        </table>
    </td>
</tr>
<%
    }
%>
</table>