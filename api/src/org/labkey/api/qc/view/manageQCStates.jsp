<%
/*
 * Copyright (c) 2019 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.qc.AbstractManageQCStatesAction" %>
<%@ page import="org.labkey.api.qc.AbstractManageQCStatesBean" %>
<%@ page import="org.labkey.api.qc.DataState" %>
<%@ page import="org.labkey.api.qc.DataStateHandler" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AbstractManageQCStatesBean> me = (JspView<AbstractManageQCStatesBean>) HttpView.currentView();

    Container container = getContainer();
    AbstractManageQCStatesBean bean = me.getModelBean();
    AbstractManageQCStatesAction manageAction = bean.getManageAction();
    ActionURL cancelUrl = bean.getReturnUrl() != null ? bean.getReturnUrl() :
            new ActionURL(manageAction.getClass(), container);
    DataStateHandler qcStateHandler = bean.getQCStateHandler();
    String currentQCPanelTitle = "Currently Defined " + StringUtils.capitalize(bean.getNoun()) + " QC States";
    String defaultStatesPanelTitle = "Default states for " + bean.getNoun() + " data";
    ActionURL baseDeleteStateURL = new ActionURL(bean.getDeleteAction(), container);
%>
<labkey:errors/><br>
<labkey:form action="<%=urlFor(manageAction.getClass())%>" name="manageQCStates" method="POST">
<input type="hidden" name="reshowPage" value="true">
<%=generateReturnUrlFormField(bean.getReturnUrl())%>
    <labkey:panel title="<%=currentQCPanelTitle%>">
        <table id="qcStatesTable" class="lk-fields-table">
            <tr>
                <th><b>State Name</b></th>
                <th><b>State Description</b></th>
                <th nowrap><b>Public Data<%= helpPopup("Public Data", "The 'Public Data' setting determines whether data in each QC state is shown to users by default.") %></b></th>
                <th nowrap><b>In Use</b></th>
            </tr>
                <td>[none]</td>
                <td>Applies to data that has not been assigned an explicit QC State</td>
                <td align="center"><input name="blankQCStatePublic" value="true" type="checkbox"<%=checked(qcStateHandler.isBlankStatePublic(container))%>/></td>
                <td><span style="color:black;padding-left: 30%;" class="fa fa-check-circle"><%= helpPopup("Blank QC State", "This QC state is provided by the system and cannot be deleted.") %></span></td>
            <tr>
            </tr>
            <%
                baseDeleteStateURL.addParameter("manageReturnUrl", cancelUrl.getLocalURIString());
                for (Object stateObj : qcStateHandler.getStates(container))
                {
                    DataState state = (DataState)stateObj;
            %>
            <tr>
                <td>
                    <input type="hidden" name="ids" value="<%= state.getRowId() %>">
                    <input type="text" name="labels" size="30"
                           value="<%= h(state.getLabel() != null ? state.getLabel() : "") %>">
                </td>
                <td>
                    <input type="text" name="descriptions" size="50"
                           value="<%= h(state.getDescription() != null ? state.getDescription() : "") %>">
                </td>
                <td align="center"><input name="publicData" value="<%= state.getRowId() %>" id="<%= h(state.getLabel()) %>_public" type="checkbox"<%=checked(state.isPublicData())%>/></td>
                <td>
                    <%
                        if (qcStateHandler.isStateInUse(container, state))
                        {
                    %>
                    <span style="color:black; padding-left: 30%;" class="fa fa-check-circle"></span>
                    <%=helpPopup("QC state in use", "This QC state cannot be deleted because it is currently a default state (see below) or is referenced by at least one " + bean.getNoun() + " row.")%>
                    <%
                        }
                        else
                        {
                    %>
                    <span style="color:black;padding-left: 30%;" class="fa fa-circle-o"></span>
                    <span style="cursor:pointer; color:red;" class="fa fa-times" onclick="LABKEY.Utils.confirmAndPost(<%=q("Delete this QC state? No additional " + bean.getDataNoun() + " data will be deleted.")%>, <%=q(baseDeleteStateURL.clone().addParameter("id", state.getRowId()))%>)"></span>
                    <%
                        }
                    %>
                </td>
            </tr>
            <%
                }
            %>
        </table>
        <span style="cursor:pointer;color:green" class="fa fa-plus-circle" onclick="addRow()"></span>&nbsp<span>Add State</span>
            &nbsp&nbsp&nbsp&nbsp
            <%= button("Delete Unused QC States")
                    .onClick("return LABKEY.Utils.confirmAndPost('Delete all unused QC states? No additional "+ bean.getDataNoun() + " data will be deleted.', " + q(baseDeleteStateURL.clone().addParameter("all", "true")) + ")") %>
    </labkey:panel>

    <%
        if (manageAction.hasQcStateDefaultsPanel())
        {
    %>
            <labkey:panel title="<%=defaultStatesPanelTitle%>">
                <%= text(manageAction.getQcStateDefaultsPanel(container, qcStateHandler)) %>
            </labkey:panel>
    <%
        }
        if (manageAction.hasDataVisibilityPanel())
        {
    %>
            <labkey:panel title="Data visibility">
                <%= text(manageAction.getDataVisibilityPanel(container, qcStateHandler)) %>
            </labkey:panel>
    <%
        }
        if (manageAction.hasRequiresCommentPanel())
        {
    %>
            <labkey:panel title="QC State Comments">
                <%= text(manageAction.getRequiresCommentPanel(container, qcStateHandler)) %>
            </labkey:panel>
    <%
        }
    %>

    <%= button("Save").submit(true).onClick("document.manageQCStates.reshowPage.value='false'; return true;") %>
    <%= button("Cancel").href(cancelUrl.getLocalURIString()) %>
</labkey:form>
<script language="javascript">
    function addRow() {
        var table = document.getElementById("qcStatesTable");
        var numberOfRows = table.rows.length;  // we'll use this as an ID later too
        var newRow = table.insertRow(numberOfRows);
        var nameCell = newRow.insertCell(newRow.cells.length);
        var descriptionCell = newRow.insertCell(newRow.cells.length);
        var publicDataCell = newRow.insertCell(newRow.cells.length);
        var inUseCell = newRow.insertCell(newRow.cells.length);

        nameCell.innerHTML = '<input type="hidden" name="newIds" value="' + numberOfRows + '"><input type="text" name="newLabels" size="30">';
        descriptionCell.innerHTML = '<input type="text" name="newDescriptions" size="50"></td>';
        publicDataCell.innerHTML = '<input type="checkbox" value="' + numberOfRows + '" id="' + numberOfRows +'_public" name="newPublicData" CHECKED>';
        publicDataCell.style.textAlign = 'center';
        inUseCell.innerHTML = '<span style="color:black;padding-left: 30%;" class="fa fa-circle-o"></span> <span style="color:red;" class="fa fa-times" onclick="deleteRow(this)"></span>';
    }

    function deleteRow(deleteButton) {
        var row = deleteButton.parentNode.parentNode;
        row.parentNode.removeChild(row);
    }
</script>
