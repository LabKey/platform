<%
/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.specimen.settings.SettingsManager"%>
<%@ page import="org.labkey.api.specimen.settings.StatusSettings"%>
<%@ page import="org.labkey.api.study.Study"%>
<%@ page import="org.labkey.api.study.StudyUrls"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.specimen.SpecimenRequestManager" %>
<%@ page import="org.labkey.specimen.SpecimenRequestStatus" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.DeleteStatusAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageStatusOrderAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageStatusesAction" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Set" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Study> me = (JspView<Study>) HttpView.currentView();
    Study study = me.getModelBean();
    Collection<SpecimenRequestStatus> statuses = SpecimenRequestManager.get().getRequestStatuses(study.getContainer(), getUser());
    Set<Integer> inUseStatuses = SpecimenRequestManager.get().getRequestStatusIdsInUse(study.getContainer());
    StatusSettings settings = SettingsManager.get().getStatusSettings(study.getContainer());
    boolean showSystemStatuses = settings.isUseShoppingCart();
%>
<labkey:errors/>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
function showSystemRows(value)
{
    document.getElementById("systemStatusRow").style.display = value ? "" : "none";
    return true;
}

</script>
<labkey:form action="<%=urlFor(ManageStatusesAction.class)%>" name="manageStatuses" method="POST">
    <p>
        Request statuses help a coordinator organize and track requests through the system and
        communicate progress to requesters. All requests do not need to pass through all states.
    </p>

    <table class="lk-fields-table">
        <tr>
            <td style="font-weight: bold;"><%= unsafe(statuses != null && statuses.size()> 0 ? "Step Number" : "&nbsp;") %></td>
            <td style="font-weight: bold;">Status Name</td>
            <td style="font-weight: bold;">Final State<%= helpPopup("Final States", "States are final if they indicate no further processing will occur for a request.  For example, 'Completed', or 'Rejected' could be final states.")%></td>
            <td style="font-weight: bold;">Lock Specimens<%= helpPopup("Locked Specimen States", "Specifies whether specimens should be available for additional requests while in each status.")%></td>
            <td>&nbsp;</td>
        </tr>
        <%
        if (statuses != null && statuses.size() > 0)
        {
            for(SpecimenRequestStatus status : statuses)
            {
        %>
                <tr <%= unsafe(status.isSystemStatus() ? "id=\"systemStatusRow\"" : "") %> <%= unsafe(!showSystemStatuses && status.isSystemStatus() ? "style=\"display:none\"" : "") %>>
                    <td align="center"><%= status.isSystemStatus() ? 1 : status.getSortOrder() + 1 %></td>
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
                            <td><%=link("delete").href(urlFor(DeleteStatusAction.class).addParameter("id", status.getRowId())).usePost()%></td>
                    <%
                        }
                    %>
                </tr>
                <%
            }
        }
        %>
        <tr>
            <td>New Status:</td>
            <td><input type="text" name="newLabel" size="40"></td>
            <td align="center"><input type="checkbox" name="newFinalState"></td>
            <td align="center"><input type="checkbox" name="newSpecimensLocked" CHECKED></td>
            <td>&nbsp;</td>
        </tr>
        <tr>
            <td colspan="5">
                <br/>
                <%= button("Save").submit(true) %>&nbsp;
                <%= button("Change Order").submit(true).onClick("document.manageStatuses.nextPage.value='" + urlFor(ManageStatusOrderAction.class) + "'; return true;") %>
                <input type="hidden" name="nextPage" value="<%=h(new ActionURL(ManageStatusesAction.class, getContainer()).getLocalURIString())%>">
            </td>
        </tr>
    </table>

    <br/>

    <p>
        Allowing users to build up specimen requests over multiple
        searches is generally more convenient, but requires the coordinator to watch for abandoned unsubmitted requests.
    </p>
    <label>
    <labkey:input formGroup="false" type="checkbox" name="useShoppingCart"  checked="<%=(settings.isUseShoppingCart())%>" onClick='showSystemRows(this.checked);' />
        Allow requests to be built over multiple searches before submission</label><br/>

    <br/>

    <%= button("Done").submit(true).onClick("document.manageStatuses.nextPage.value=''; return true;")%>
    <%= button("Cancel").href(urlProvider(StudyUrls.class).getManageStudyURL(study.getContainer())) %>&nbsp;
</labkey:form>