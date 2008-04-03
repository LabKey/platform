<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.Cohort" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.model.Participant" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyController.ManageCohortsBean> me = (JspView<StudyController.ManageCohortsBean>) HttpView.currentView();;
    StudyController.ManageCohortsBean bean = me.getModelBean();
%>
<span class="labkey-error">
    <%
        BindException errors = bean.getErrors();
        if (errors != null)
        {
            for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
            {
                %><%=PageFlowUtil.filter(HttpView.currentContext().getMessage(e))%><br><%
            }
        }
    %>
</span>
<form action="manageCohorts.post" name="manageCohorts" method="POST">
<%= buttonImg("Done", "document.manageCohorts.reshowPage.value='false'; return true;")%>
<%= buttonLink("Cancel", new ActionURL(StudyController.ManageStudyAction.class, me.getViewContext().getContainer()))%>
<input type="hidden" name="reshowPage" value="true">
<input type="hidden" name="refreshParticipants" value="false">
<input type="hidden" name="clearParticipants" value="false">
<%
    WebPartView.startTitleFrame(out, "Currently Defined Cohorts", null, null, null);
%>
    <table>
<%
            if (bean.getCohorts() == null || bean.getCohorts().length == 0)
            {
        %>
        <tr>
            <td colspan="3">
                <em>No cohorts have been defined for this study.</em>
            </td>
        </tr>
        <%
            }
            else
            {
        %>
        <tr>
            <th>&nbsp;</th>
            <th>Cohort Name</th>
            <th>&nbsp;</th>
        </tr>
        <%
            }
            for (Cohort cohort : bean.getCohorts())
            {
        %>
        <tr>
            <td align="center">&nbsp;</td>
            <td valign="top">
                <input type="hidden" name="ids" value="<%= cohort.getRowId() %>">
                <input type="text" name="labels" size="40"
                       value="<%= cohort.getLabel() != null ? h(cohort.getLabel()) : "" %>">
            </td>
            <td>
                <%=  cohort.isInUse() ? "[in use]" + helpPopup("Cohort in use", "This cohort cannot be deleted because it is currently referenced by at least one participant, visit, or dataset.") :
                        textLink("Delete", new ActionURL(StudyController.DeleteCohortAction.class, me.getViewContext().getContainer()).addParameter("id", cohort.getRowId()).getLocalURIString(),
                                "return confirm('Delete this cohort?  No additional study data will be deleted.')", null) %>
            </td>
        </tr>
        <%
            }
        %>
        <tr>
            <th>New Cohort:</th>
            <td colspan="2"><input type="text" name="newLabel" size="40"></td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td>
                <%= buttonImg("Save")%>
                <%= buttonLink("Delete Unused Cohorts",
                        new ActionURL(StudyController.DeleteCohortAction.class, me.getViewContext().getContainer()).addParameter("all", "true"),
                        "return confirm('Delete all unused cohorts?  No additional study data will be deleted.')")%>
                <%= buttonLink("Cancel", new ActionURL(StudyController.ManageStudyAction.class, me.getViewContext().getContainer()))%>
            </td>
        </tr>
    </table>
    <%
        WebPartView.endTitleFrame(out);
        WebPartView.startTitleFrame(out, "Automatic Participant/Cohort Assignment", null, null, null);
    %>
        <table>
        <tr>
            <th align="right">Participant/Cohort Dataset<%= helpPopup("Participant/Cohort Dataset", "Participants can be assigned to cohorts based on the data in a field of a single dataset.  If set, participant's cohort assignments will be reloaded every time this dataset is re-inported.")%></th>
            <td>
                <select name="participantCohortDataSetId" onchange="document.manageCohorts.participantCohortProperty.value=''; document.manageCohorts.submit()">
                    <option value="-1">[None]</option>
                    <%
                        for (DataSetDefinition dataset : bean.getParticipantCohortDatasets())
                        {
                            String selected = (bean.getParticipantCohortDataSetId() != null &&
                                    dataset.getDataSetId() == bean.getParticipantCohortDataSetId() ? "selected" : "");
                            %><option value="<%= dataset.getDataSetId() %>" <%= selected %>><%= h(dataset.getLabel()) %></option><%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <th align="right">Cohort Column Name</th>
            <td>
                <select name="participantCohortProperty">
                    <option value="">[None]</option>
                <%
                for (PropertyDescriptor pd : bean.getParticipantCohortDatasetCols())
                {
                %>
                    <option value="<%= pd.getName() %>" <%= pd.getName().equals(bean.getParticipantCohortProperty()) ? "SELECTED" : "" %>>
                        <%= h(null == pd.getLabel() ? pd.getName() : pd.getLabel()) %>
                    </option>
                <%
                }
                %>
                </select>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td>
                <%= buttonImg("Update Assignments", "if (confirm('Refreshing will update cohort information for all participants.  Continue?')) { document.manageCohorts.refreshParticipants.value='true'; return true; } else return false;")%>
                <%= buttonImg("Clear Assignments", "if (confirm('Refreshing will clear cohort information for all participants.  Continue?')) { document.manageCohorts.clearParticipants.value='true'; return true; } else return false;")%>
            </td>
        </tr>
        </table>
    <%
        WebPartView.endTitleFrame(out);
        WebPartView.startTitleFrame(out, "Current Cohort Assignments", null, null, null);
    %>
    <table>
        <tr>
            <th>Participant ID</th>
            <th>Cohort</th>
        </tr>
    <%
        boolean showCohorts = StudyManager.getInstance().showCohorts(getViewContext().getContainer(), getViewContext().getUser());
        for (Participant participant : bean.getParticipants())
        {
            Cohort cohort = null;
            String label;
            if (showCohorts)
            {
                cohort = StudyManager.getInstance().getCohortForParticipant(getViewContext().getContainer(),
                        getViewContext().getUser(), participant.getParticipantId());
                label = cohort != null ? cohort.getLabel() : "[Unassigned]";
            }
            else
                label = "";
    %>
        <tr>
            <td><%= h(participant.getParticipantId()) %></td>
            <td><%= h(label) %></td>
        </tr>
    <%
        }
    %>
    </table>
    <%
        WebPartView.endTitleFrame(out);
    %>
</form>