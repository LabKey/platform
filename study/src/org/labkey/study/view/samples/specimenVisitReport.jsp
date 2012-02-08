<%
/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.samples.report.SpecimenReportTitle" %>
<%@ page import="org.labkey.study.samples.report.SpecimenVisitReport" %>
<%@ page import="org.labkey.study.samples.report.SpecimenVisitReportParameters" %>
<%@ page import="java.util.Collection" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenVisitReportParameters> me = (JspView<SpecimenVisitReportParameters>) HttpView.currentView();
    SpecimenVisitReportParameters bean = me.getModelBean();
    Study study = StudyManager.getInstance().getStudy(me.getViewContext().getContainer());

    if (study == null)
    {
%>
This folder does not contain a study.
<%
        return;
    }

    for (SpecimenVisitReport report : bean.getReports())
    {
        VisitImpl[] visits = report.getVisits();
        int colCount = visits.length + report.getLabelDepth();
%>
<table class="labkey-data-region labkey-show-borders"><colgroup>
    <%
    for (int i = 0; i < colCount; i++)
    {
        %><col><%
    }
    %></colgroup>
    <tr>
        <th style="text-align:left; border-bottom:solid 1px #AAAAAA;" class="labkey-data-region-title" colspan="<%= colCount %>"><%= h(report.getTitle())%></th>
    </tr>

    <tr class="labkey-alternate-row">
        <%
        if (report.getLabelDepth() > 0)
        {
            %><td class="labkey-column-header" colspan="<%= report.getLabelDepth() %>">&nbsp;</td><%
        }
        for (VisitImpl visit : visits)
        {
            String label = visit.getDisplayString();
            %><td class="labkey-column-header" align="center"><%= h(label) %></td><%
        }
    %>
    </tr>
    <%
        if (report.getRows() == null || report.getRows().isEmpty())
        {
            %><tr>
                <td colspan="<%= colCount %>"><em>No data to show.</em></td>
            </tr><%
        }
        else
        {
            // pre-compute layout for header rows (Pair<text,rowspan>)
            int width = report.getLabelDepth();
            int rows = report.getRows().size();
            Pair<SpecimenReportTitle, Integer>[][] rowtitles = new Pair[rows][];
            int rowIndex = -1;

            for (SpecimenVisitReport.Row row : (Collection<SpecimenVisitReport.Row>) report.getRows())
            {
                rowIndex++;
                SpecimenReportTitle[] currentTitleHierarchy = row.getTitleHierarchy();
                rowtitles[rowIndex] = new Pair[width];
                for (int i=0 ; i<width ; i++)
                    rowtitles[rowIndex][i] = new Pair<SpecimenReportTitle, Integer>(currentTitleHierarchy[i], 1);
            }

            for (int row = rows-1 ; row > 0 ; row--)
            {
                for (int col=0 ; col < width ; col++)
                {
                    if (!StringUtils.equals(rowtitles[row][col].first.getValue(), rowtitles[row-1][col].first.getValue()))
                        break;
                    rowtitles[row-1][col].second += rowtitles[row][col].second;
                    rowtitles[row][col].second = 0;
                }
            }

            rowIndex = -1;
            for (SpecimenVisitReport.Row row : (Collection<SpecimenVisitReport.Row>) report.getRows())
            {
                rowIndex++;
                %><tr class="<%=rowIndex%2==1 ? "labkey-alternate-row" : "labkey-row" %>" style="vertical-align:top"><%
                for (int col = 0; col<width ; col++)
                {
                    String title = rowtitles[rowIndex][col].first.getDisplayValue();
                    if (title == null || title.length() == 0)
                        title = "[unspecified]";
                    int rowspan = rowtitles[rowIndex][col].second;
                    if (rowspan==0)
                        continue;
                    String className="";
                    String style=col<width-1 ? "background:#FFFFFF" : "";
                    %><td class="<%=className%>" style="<%=style%>" rowspan="<%=rowspan%>" nowrap>
                        <%= h(title) %>
                    </td><%
                }

                for (VisitImpl visit : visits)
                {
                    %><td align="center"><%
                        %><%= row.getCellHtml(visit) %><%
                    %></td><%
                }
                %></tr><%
            }
        }
%>
        </table><br><br>
<%
    }
%>