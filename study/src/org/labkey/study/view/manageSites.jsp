<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.model.Study"%>
<%@ page import="org.labkey.study.model.Site"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Study> me = (JspView<Study>) HttpView.currentView();
    Study study = me.getModelBean();
%>
<%=PageFlowUtil.getStrutsError(request, "main")%>
<form action="manageSites.post" method="POST">
    <table class="normal">
        <tr>
            <th>&nbsp;</th>
            <th>Location Id</th>
            <th>Location Name</th>
            <th>Location Type</th>
        </tr>
        <%
            for (Site site : study.getSites())
            {
                String type = "Not specified";
                if (site.isClinic())
                    type = "Clinic";
                else if (site.isEndpoint())
                    type = "Endpoint Lab";
                else if (site.isRepository())
                    type = "Repository";
                else if (site.isSal())
                    type = "Site Affiliated Lab";
        %>
            <tr>
                <td>&nbsp;</td>
                <td align="center">
                    <%= site.getLdmsLabCode()%>
                    <input type="hidden" name="ids" value="<%= site.getRowId()%>">
                </td>
                <td>
                    <input type="text" name="labels" size="40" value="<%= site.getLabel() != null ? h(site.getLabel()) : "" %>">
                </td>
                <td>
                    <%= h(type) %>
                </td>
            </tr>
        <%
            }
        %>
        <tr>
            <th>Add Location:</th>
            <td><input type="text" size="8" name="newId"></td>
            <td><input type="text" size="40" name="newLabel"></td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><%= buttonImg("Save") %>&nbsp;<%= buttonLink("Cancel", "manageStudy.view")%></td>
            <td>&nbsp;</td>
        </tr>
    </table>
</form>