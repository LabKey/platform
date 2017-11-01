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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.QCState" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyController.ManageQCStatesBean> me = (JspView<StudyController.ManageQCStatesBean>) HttpView.currentView();
    StudyController.ManageQCStatesBean bean = me.getModelBean();
    ActionURL cancelUrl = bean.getReturnUrl() != null ? new ActionURL(bean.getReturnUrl()) :
        new ActionURL(StudyController.ManageStudyAction.class, getContainer());
%>
<labkey:errors/><br>
<labkey:form action="<%=h(buildURL(StudyController.ManageQCStatesAction.class))%>" name="manageQCStates" method="POST">
<input type="hidden" name="reshowPage" value="true">
<input type="hidden" name="returnUrl" value="<%= h(bean.getReturnUrl()) %>">
    <labkey:panel title="Currently Defined Dataset QC States">
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
                <td align="center"><input name="blankQCStatePublic" value="true" type="checkbox"<%=checked(bean.getStudy().isBlankQCStatePublic())%>/></td>
                <td>[in&nbsp;use]<%= helpPopup("Blank QC State", "This QC state is provided by the system and cannot be deleted.") %></td>
            <tr>
            </tr>
            <%
                ActionURL baseDeleteStateURL = new ActionURL(StudyController.DeleteQCStateAction.class, getContainer());
                baseDeleteStateURL.addParameter("manageReturnUrl", bean.getReturnUrl());
                for (QCState state : bean.getQCStates())
                {
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
                    <%= text(StudyManager.getInstance().isQCStateInUse(state) ? "[in&nbsp;use]" + helpPopup("QC state in use", "This QC state cannot be deleted because it is currently a default state (see below) or is referenced by at least one dataset row.") :
                            textLink("Delete", baseDeleteStateURL.clone().addParameter("id", state.getRowId()),
                                    "return confirm('Delete this QC state?  No additional study data will be deleted.')", null)) %>
                </td>
            </tr>
            <%
                }
            %>
            <tr>
                <td nowrap>New dataset QC state:</td>
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
                            .href(baseDeleteStateURL.clone().addParameter("all", "true"))
                            .onClick("return confirm('Delete all unused QC states?  No additional study data will be deleted.')") %>
                    <%= button("Cancel").href(cancelUrl) %>
                </td>
            </tr>
        </table>
    </labkey:panel>

    <labkey:panel title="Default states for dataset data">
        <table class="lk-fields-table">
            <tr>
                <td colspan="2">These settings allow different default QC states depending on data source.
                    If set, all imported data without an explicit QC state will have the selected state automatically assigned.</td>
            </tr>
            <tr>
                <th align="right" width="300px">Pipeline imported datasets:</th>
                <td>
                    <select name="defaultPipelineQCState">
                        <option value="">[none]</option>
                        <%
                            for (QCState state : bean.getQCStates())
                            {
                                boolean selected = bean.getStudy().getDefaultPipelineQCState() != null &&
                                        bean.getStudy().getDefaultPipelineQCState() == state.getRowId();
                        %>
                        <option value="<%= state.getRowId() %>"<%=selected(selected)%>>
                            <%= h(state.getLabel())%></option>
                        <%
                            }
                        %>
                    </select>
                </td>
            </tr>
            <tr>
                <th align="right" width="300px">Assay data copied to this study:</th>
                <td>
                    <select name="defaultAssayQCState">
                        <option value="">[none]</option>
                        <%
                            for (QCState state : bean.getQCStates())
                            {
                                boolean selected = bean.getStudy().getDefaultAssayQCState() != null &&
                                        bean.getStudy().getDefaultAssayQCState() == state.getRowId();
                        %>
                        <option value="<%= state.getRowId() %>"<%=selected(selected)%>><%= h(state.getLabel())%></option>
                        <%
                            }
                        %>
                    </select>
                </td>
            </tr>
            <tr>
                <th align="right" width="300px">Directly inserted/updated dataset data:</th>
                <td>
                    <select name="defaultDirectEntryQCState">
                        <option value="">[none]</option>
                        <%
                            for (QCState state : bean.getQCStates())
                            {
                                boolean selected = bean.getStudy().getDefaultDirectEntryQCState() != null &&
                                        bean.getStudy().getDefaultDirectEntryQCState() == state.getRowId();
                        %>
                        <option value="<%= state.getRowId() %>"<%=selected(selected)%>><%= h(state.getLabel())%></option>
                        <%
                            }
                        %>
                    </select>
                </td>
            </tr>
        </table>
    </labkey:panel>

    <labkey:panel title="Data visibility">
        <table class="lk-fields-table">
            <tr>
                <td colspan="2">This setting determines whether users see non-public data by default.
                    Users can always explicitly choose to see data in any QC state.</td>
            </tr>
            <tr>
                <th align="right" width="300px">Default visibility:</th>
                <td>
                    <select name="showPrivateDataByDefault">
                        <option value="false">Public data</option>
                        <option value="true"<%=selected(bean.getStudy().isShowPrivateDataByDefault())%>>All data</option>
                    </select>
                </td>
            </tr>
        </table>
    </labkey:panel>

    <%= button("Done").submit(true).onClick("document.manageQCStates.reshowPage.value='false'; return true;") %>
    <%= button("Cancel").href(cancelUrl.getLocalURIString()) %>
</labkey:form>