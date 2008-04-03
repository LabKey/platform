<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.Visit" %>
<%@ page import="org.labkey.study.samples.report.SpecimenVisitReport" %>
<%@ page import="org.labkey.study.samples.report.SpecimenVisitReportParameters" %>
<%@ page import="org.labkey.study.model.Cohort" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.util.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenVisitReportParameters> me = (JspView<SpecimenVisitReportParameters>) HttpView.currentView();
    SpecimenVisitReportParameters bean = me.getModelBean();
    String contextPath = request.getContextPath();
    String shadeColor = "#EEEEEE";
    // UNDONE: move into stylesheet
    String borderColor = "#808080";
    String styleTH=" style=\"border-right:solid 1px " + borderColor + "; border-top:solid 2px " + borderColor + ";\"";

    for (SpecimenVisitReport report : bean.getReports())
    {
        Visit[] visits = report.getVisits();
        int colCount = visits.length + report.getLabelDepth();
%>
<table border="0" cellspacing="0" cellpadding="2" class="grid">
    <tr>
        <th align="left" colspan="<%= colCount %>"><%= h(report.getTitle())%></th>
    </tr>

    <tr bgcolor="<%=shadeColor%>">
        <%
        if (report.getLabelDepth() > 0)
        {
        %>
        <td colspan="<%= report.getLabelDepth() %>">&nbsp;</td>
        <%
        }
        for (Visit visit : visits)
        {
            String label = visit.getDisplayString();
            %><td align="center" valign="top" bgcolor=<%=shadeColor%>><%= h(label) %></td><%
        }
        %>
    </tr>
        <%
            if (report.getRows() == null || report.getRows().isEmpty())
            {
                %>
            <tr>
                <td colspan="<%= colCount %>"><em>No data to show.</em></td>
            </tr>
                <%
            }
            else
            {
                int rowIndex = 0;
                String[] previousTitleHierarchy = null;
                for (SpecimenVisitReport.Row row : (Collection<SpecimenVisitReport.Row>) report.getRows())
                {
                    String[] currentTitleHierarchy = row.getTitleHierarchy();
                    %><tr <%= (rowIndex++)%2==1 ? "bgcolor=\"eeeeee\"" : "" %>  style="vertical-align:top">
                    <%
                    for (int i = 0; i < currentTitleHierarchy.length; i++)
                    {
                        String titleElement = currentTitleHierarchy[i];
                        boolean outputElement = previousTitleHierarchy == null;
                        for (int j = i; j >= 0 && !outputElement; j--)
                        {
                            String currentRow = currentTitleHierarchy[j];
                            String previousRow = previousTitleHierarchy[j];
                            if (currentRow == null)
                                currentRow = "]";
                            if (previousRow == null)
                                previousRow = "";
                            outputElement = !currentRow.equals(previousRow);
                        }
                        String style = "border-bottom:0;border-top:solid " + (outputElement ? "1px" : "0px") + " #808080";
                    %>
                        <td <%= i < currentTitleHierarchy.length - 1 ? "bgcolor=\"#FFFFFF\"" : ""%> style="<%= style %>">
                            <%= outputElement ? h(titleElement != null ? titleElement : "[unspecified]") : "&nbsp;" %>
                        </td>
                    <%
                    }

                    previousTitleHierarchy = currentTitleHierarchy;
                    for (Visit visit : visits)
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