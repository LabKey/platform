<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.SpecimenManager"%>
<%@ page import="org.labkey.study.controllers.StudyController"%>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.model.SpecimenRequestStatus" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.samples.settings.StatusSettings" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyImpl> me = (JspView<StudyImpl>) HttpView.currentView();
    StudyImpl study = me.getModelBean();
    List<SpecimenRequestStatus> statuses = study.getSampleRequestStatuses(getUser());
    Set<Integer> inUseStatuses = study.getSampleRequestStatusesInUse();
    StatusSettings settings = SpecimenManager.getInstance().getStatusSettings(study.getContainer());
    boolean showSystemStatuses = settings.isUseShoppingCart();
%>
<labkey:errors/>
<script type="text/javascript">
function showSystemRows(value)
{
    document.getElementById("systemStatusRow").style.display = value ? "" : "none";
    return true;
}

</script>
<form action="<%=h(buildURL(SpecimenController.ManageStatusesAction.class))%>" name="manageStatuses" method="POST">
<table width="620px">
    <tr>
        <td class="labkey-form-label" style="padding-top:4px;padding-bottom:4px;">Request statuses help a coordinator organize and track requests through the system and
            communicate progress to requesters. All requests do not need to pass through all states.</td>
    </tr>
    <tr>
        <td>
        <table>
            <tr>
                <th>&nbsp;</th>
                <th><%= text(statuses != null && statuses.size()> 0 ? "Step Number" : "&nbsp;") %></th>
                <th>Status Name</th>
                <th>Final
                    State<%= helpPopup("Final States", "States are final if they indicate no further processing will occur for a request.  For example, 'Completed', or 'Rejected' could be final states.")%></th>
                <th>Lock
                    Specimens<%= helpPopup("Locked Specimen States", "Specifies whether specimens should be available for additional requests while in each status.")%></th>
            </tr>
            <%
            if (statuses != null && statuses.size() > 0)
            {
                for(SpecimenRequestStatus status : statuses)
                {
            %>
                    <tr <%= text(status.isSystemStatus() ? "id=\"systemStatusRow\"" : "") %> <%= text(!showSystemStatuses && status.isSystemStatus() ? "style=\"display:none\"" : "") %>>
                <%
                    if (status.isSystemStatus() || inUseStatuses.contains(status.getRowId()))
                    {
                %>
                        <td>In-use<%= helpPopup("In-use Status", status.isSystemStatus() ? "This is a system status that cannot be deleted." :
                                "This status cannot be deleted because one or more requests are currently in this status.") %></td>
                <%
                    }
                    else
                    {
                %>
                        <td><%= textLink("delete", buildURL(SpecimenController.DeleteStatusAction.class, "id=" + status.getRowId()))%></td>
                <%
                    }
                %>
                        <td align="center"><%= status.isSystemStatus() ? "1" : status.getSortOrder() + 1 %></td>
                        <td>
                            <%
                                if (!status.isSystemStatus())
                                {
                            %>
                            <input type="hidden" name="ids" value="<%= status.getRowId() %>">
                            <%
                                }
                            %>
                            <input type="text" name="labels" size="40"
                                   value="<%= h(status.getLabel() != null ? status.getLabel() : "") %>"
                                    <%=disabled(status.isSystemStatus()) %>>
                        </td>
                        <td align="center"><input type="checkbox" name="finalStateIds"
                                      value="<%= status.getRowId() %>"<%=checked(status.isFinalState())
                                      %><%=disabled(status.isSystemStatus()) %>>
                        </td>
                        <td align="center"><input type="checkbox" name="specimensLockedIds"
                                      value="<%= status.getRowId() %>"<%=checked(status.isSpecimensLocked())%>
                                <%=disabled(status.isSystemStatus()) %>>
                        </td>
                    </tr>
                    <%
                }
            }
            %>
            <tr>
                <th>&nbsp;</th>
                <th>New Status:</th>
                <td><input type="text" name="newLabel" size="40"></td>
                <td align="center"><input type="checkbox" name="newFinalState"></td>
                <td align="center"><input type="checkbox" name="newSpecimensLocked" CHECKED></td>
            </tr>
            <tr>
                <th>&nbsp;</th>
                <td>&nbsp;</td>
                <td colspan="3">
                    <%= button("Save").submit(true) %>&nbsp;
                    <%= button("Change Order").submit(true).onClick("document.manageStatuses.nextPage.value='" + new ActionURL(SpecimenController.ManageStatusOrderAction.class, getContainer()).getLocalURIString() + "'; return true;") %>
                    <input type="hidden" name="nextPage" value="<%=h(new ActionURL(SpecimenController.ManageStatusesAction.class, getContainer()).getLocalURIString())%>">
                </td>
            </tr>
        </table>
        </td>
    </tr>
    <tr>
        <td colspan="4">&nbsp;</td>
    </tr>
    <tr>
        <td class="labkey-form-label" style="padding-top:4px;padding-bottom:4px;">Allowing users to build up specimen requests over multiple
            searches is generally more convenient, but requires the coordinator to watch for abandoned unsubmitted requests.</td>
    </tr>
    <tr>
        <td>
            <input type="checkbox" name="useShoppingCart"<%=checked(settings.isUseShoppingCart())%> onclick='showSystemRows(this.checked);'>
            Allow requests to be built over multiple searches before submission
        </td>
    </tr>
    <tr>
        <td>&nbsp;</td>
    </tr>
    <tr>
        <td>
            <%= button("Done").submit(true).onClick("document.manageStatuses.nextPage.value=''; return true;")%>
            <%= button("Cancel").href(new ActionURL(StudyController.ManageStudyAction.class, study.getContainer())) %>&nbsp;
        </td>
    </tr>
</table>
</form>