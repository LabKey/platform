<%@ page import="org.labkey.study.model.Visit"%>
<%@ page import="org.labkey.study.model.Cohort" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    Cohort[] cohorts = StudyManager.getInstance().getCohorts(getStudy().getContainer(), getViewContext().getUser());
%>
<form action="visitVisibility.post" method="POST">
    <table class="normal">
        <tr>
            <th align="left">ID</th>
            <th align="left">Label</th>
            <th align="left">Cohort</th>
            <th align="left">Type</th>
            <th align="left">Show By Default</th>
        </tr>
    <%
        for (Visit visit : getVisits())
        {
    %>
        <tr>
            <td><%= visit.getRowId() %></td>
            <td>
                <input type="text" size="40" name="label" value="<%= visit.getLabel() != null ? visit.getLabel() : "" %>">
            </td>
            <td>
                <%
                    if (cohorts == null || cohorts.length == 0)
                    {
                %>
                    <em>No cohorts defined</em>
                <%
                    }
                    else
                    {
                    %>
                    <select name="cohort">
                        <option value="-1">All</option>
                    <%

                        for (Cohort cohort : cohorts)
                        {
                    %>
                        <option value="<%= cohort.getRowId()%>" <%= visit.getCohortId() != null && visit.getCohortId() == cohort.getRowId() ? "SELECTED" : ""%>>
                            <%= h(cohort.getLabel())%>
                        </option>
                    <%
                        }
                    %>
                    </select>
                    <%
                    }
                %>
            </td>
            <td>
                <select name="extraData">
                    <option value="">[None]</option>
                    <%
                        for (Visit.Type type : Visit.Type.values())
                        {
                            String selected = (visit.getType() == type ? "selected" : "");
                            %>
                            <option value="<%= type.getCode() %>" <%= selected %>><%= type.getMeaning() %></option>
                            <%
                        }
                    %>
                </select>
            </td>
            <td>
                <input type="checkbox" name="visible" <%= visit.isShowByDefault() ? "Checked" : "" %> value="<%= visit.getRowId() %>">
                <input type="hidden" name="ids" value="<%= visit.getRowId() %>">
            </td>
        </tr>
    <%
        }
    %>
    </table>
    <%= buttonImg("Save") %>&nbsp;<%= buttonLink("Cancel", "manageVisits.view")%>
</form>
