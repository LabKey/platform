<%@ page import="org.labkey.study.model.DataSetDefinition"%>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.Cohort" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    Cohort[] cohorts = StudyManager.getInstance().getCohorts(getStudy().getContainer(), getViewContext().getUser());
%>
<p>Datasets can be hidden on the study overview screen.</p>
<p>Hidden data can always be viewed, but is not shown by default.</p>
<form action="dataSetVisibility.post" method="POST">
    <table class="normal">
        <tr>
            <th align="left">ID</th>
            <th align="left">Label</th>
            <th align="left">Category</th>
            <th align="left">Cohort</th>
            <th align="left">Visible</th>
        </tr>
    <%
        for (DataSetDefinition def : getDataSets())
        {
    %>
        <tr>
            <td><%= def.getDataSetId() %></td>
            <td>
                <input type="text" size="20" name="label" value="<%= def.getLabel() != null ? def.getLabel() : "" %>">
            </td>
            <td>
                <input type="text" size="20" name="extraData" value="<%= def.getCategory() != null ? def.getCategory() : "" %>">
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
                        <option value="<%= cohort.getRowId()%>" <%= def.getCohortId() != null && def.getCohortId() == cohort.getRowId() ? "SELECTED" : ""%>>
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
            <td align="center">
                <input type="checkbox" name="visible" <%= def.isShowByDefault() ? "Checked" : "" %> value="<%= def.getDataSetId() %>">
                <input type="hidden" name="ids" value="<%= def.getDataSetId() %>">
            </td>
        </tr>
    <%
        }
    %>
    </table>
    <%= buttonImg("Save") %>&nbsp;<%= buttonLink("Cancel", "manageTypes.view")%>
</form>
