<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.qc.QCState" %>
<%@ page import="org.labkey.api.qc.QCStateHandler" %>
<%@ page import="org.labkey.api.qc.AbstractManageQCStatesBean" %>
<%@ page import="org.labkey.api.qc.AbstractManageQCStatesAction" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AbstractManageQCStatesBean> me = (JspView<AbstractManageQCStatesBean>) HttpView.currentView();

    Container container = getContainer();
    AbstractManageQCStatesBean bean = me.getModelBean();
    AbstractManageQCStatesAction manageAction = bean.getManageAction();
    ActionURL cancelUrl = bean.getReturnUrl() != null ? new ActionURL(bean.getReturnUrl()) :
            new ActionURL(manageAction.getClass(), container);
    QCStateHandler qcStateHandler = bean.getQCStateHandler();
    String currentQCPanelTitle = "Currently Defined " + StringUtils.capitalize(bean.getNoun()) + " QC States";
    String defaultStatesPanelTitle = "Default states for " + bean.getNoun() + " data";
%>
<labkey:errors/><br>
<labkey:form action="<%=h(buildURL(manageAction.getClass()))%>" name="manageQCStates" method="POST">
<input type="hidden" name="reshowPage" value="true">
<input type="hidden" name="returnUrl" value="<%= h(bean.getReturnUrl()) %>">
    <labkey:panel title="<%=h(currentQCPanelTitle)%>">
        <table class="lk-fields-table">
            <tr>
                <th>&nbsp;</th>
                <th><b>State Name</b></th>
                <th><b>State Description</b></th>
                <th nowrap><b>Public Data<%= helpPopup("Public Data", "The 'Public Data' setting determines whether data in each QC state is shown to users by default.") %></b></th>
                <th>&nbsp;</th>
            </tr>
                <td>&nbsp;</td>
                <td>[none]</td>
                <td>Applies to data that has not been assigned an explicit QC State</td>
                <td align="center"><input name="blankQCStatePublic" value="true" type="checkbox"<%=checked(qcStateHandler.isBlankQCStatePublic(container))%>/></td>
                <td>[in&nbsp;use]<%= helpPopup("Blank QC State", "This QC state is provided by the system and cannot be deleted.") %></td>
            <tr>
            </tr>
            <%
                ActionURL baseDeleteStateURL = new ActionURL(bean.getDeleteAction(), container);
                baseDeleteStateURL.addParameter("manageReturnUrl", bean.getReturnUrl());
                for (Object stateObj : qcStateHandler.getQCStates(container))
                {
                    QCState state = (QCState)stateObj;
            %>
            <tr>
                <td align="center">&nbsp;</td>
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
                    <%= qcStateHandler.isQCStateInUse(container, state) ? "[in&nbsp;use]" + helpPopup("QC state in use", "This QC state cannot be deleted because it is currently a default state (see below) or is referenced by at least one " + bean.getNoun() + " row.") :
                            link("Delete")
                                .onClick("return LABKEY.Utils.confirmAndPost('Delete this QC state? No additional study data will be deleted.', " + qh(baseDeleteStateURL.clone().addParameter("id", state.getRowId()).getLocalURIString()) + ")") %>
                </td>
            </tr>
            <%
                }
            %>
            <tr>
                <td nowrap>New <%= h(bean.getNoun())%> QC state:</td>
                <td><input type="text" name="newLabel" size="30"></td>
                <td><input type="text" name="newDescription" size="50"></td>
                <td align="center"><input type="checkbox" name="newPublicData" CHECKED></td>
                <td>&nbsp;</td>
            </tr>
            <tr>
                <td>&nbsp;</td>
                <td colspan="4">
                    <%= button("Save").submit(true) %>
                    <%= button("Delete Unused QC States")
                            .onClick("return LABKEY.Utils.confirmAndPost('Delete all unused QC states? No additional study data will be deleted.', " + qh(baseDeleteStateURL.clone().addParameter("all", "true").getLocalURIString()) + ")") /* TODO: make this messages configurable */%>
                    <%= button("Cancel").href(cancelUrl) %>
                </td>
            </tr>
        </table>
    </labkey:panel>

    <%
        if (manageAction.hasQcStateDefaultsPanel())
        {
    %>
    <labkey:panel title="<%=h(defaultStatesPanelTitle)%>">
        <%= manageAction.getQcStateDefaultsPanel(container, qcStateHandler) %>
    </labkey:panel>
    <%
        }
        if (manageAction.hasDataVisibilityPanel())
        {
    %>
    <labkey:panel title="Data visibility">
        <%= manageAction.getDataVisibilityPanel(container, qcStateHandler) %>
    </labkey:panel>
    <%
        }
    %>

    <%= button("Done").submit(true).onClick("document.manageQCStates.reshowPage.value='false'; return true;") %>
    <%= button("Cancel").href(cancelUrl.getLocalURIString()) %>
</labkey:form>