<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.specimen.settings.DisplaySettings" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DisplaySettings> me = (JspView<DisplaySettings>) HttpView.currentView();
    DisplaySettings bean = me.getModelBean();
    Container container = getContainer();
%>

<labkey:errors/>
<labkey:form action="<%=h(buildURL(SpecimenController.ManageDisplaySettingsAction.class))%>" method="POST">
    <table class="labkey-manage-display" width=600>
        <tr>
            <td colspan="2" class="labkey-announcement-title" align="left">
                <span>Comments and Quality Control</span>
            </td>
        </tr>
        <tr><td colspan="2" class="labkey-title-area-line"></td></tr>
        <tr>
            <td colspan="2">The specimen system can function in two modes: request mode, where vials are requested and requests are managed,
                and comments/QC mode, where vial information is modified.  Users with appropriate permissions can always manually switch modes.</td>
        </tr>
        <tr>
            <th align="right">Default mode:</th>
            <td>
                <select name="defaultToCommentsMode">
                    <option value="false"<%=selected(!bean.isDefaultToCommentsMode())%>>Request Mode</option>
                    <option value="true"<%=selected(bean.isDefaultToCommentsMode())%>>Comments Mode</option>
                </select>
            </td>
        </tr>
        <tr>
            <th align="right">Manual QC flagging/unflagging<%= helpPopup("Manual QC Flagging", "Vials are automatically flagged for QC " +
                    "at time of import if a vial's history contains conflicting information." +
                    "<p>Manual QC flagging/unflagging allows these states to be changed without updating the underlying specimen data.  " +
                    "<p>Once a vial's QC state is set manually, it will no longer be updated automatically during the import process.", true)%>:</th>
            <td>
                <select name="enableManualQCFlagging">
                    <option value="true"<%=selected(bean.isEnableManualQCFlagging())%>>Enabled</option>
                    <option value="false"<%=selected(!bean.isEnableManualQCFlagging())%>>Disabled</option>
                </select>
            </td>
        </tr>
        <tr>
            <td colspan="2" class="labkey-announcement-title" align="left">
                <span>Low vial warnings</span>
            </td>
        </tr>
        <tr><td colspan="2" class="labkey-title-area-line"></td></tr>
        <tr>
            <td colspan="2">If enabled, the specimen request system can display warning icons when one or zero vials of any primary specimen are available for request.  The icon will appear next to all vials of that the primary specimen.</td>
        </tr>
        <tr>
            <th align="right">Display one available vial warning:</th>
            <td>
                <select name="lastVial">
                    <option value="<%= text(DisplaySettings.DisplayOption.NONE.name()) %>"
                            <%=selected(bean.getLastVialEnum() == DisplaySettings.DisplayOption.NONE)%>>Never</option>
                    <option value="<%= text(DisplaySettings.DisplayOption.ALL_USERS.name()) %>"
                            <%=selected(bean.getLastVialEnum() == DisplaySettings.DisplayOption.ALL_USERS)%>>For all users</option>
                    <option value="<%= text(DisplaySettings.DisplayOption.ADMINS_ONLY.name()) %>"
                            <%=selected(bean.getLastVialEnum() == DisplaySettings.DisplayOption.ADMINS_ONLY)%>>For administrators only</option>
                </select>
            </td>
        </tr>
        <tr>
            <th align="right">Display zero available vials warning:</th>
            <td>
                <select name="zeroVials">
                    <option value="<%= text(DisplaySettings.DisplayOption.NONE.name()) %>"
                            <%=selected(bean.getZeroVialsEnum() == DisplaySettings.DisplayOption.NONE)%>>Never</option>
                    <option value="<%= text(DisplaySettings.DisplayOption.ALL_USERS.name()) %>"
                            <%=selected(bean.getZeroVialsEnum() == DisplaySettings.DisplayOption.ALL_USERS)%>>For all users</option>
                    <option value="<%= text(DisplaySettings.DisplayOption.ADMINS_ONLY.name()) %>"
                            <%=selected(bean.getZeroVialsEnum() == DisplaySettings.DisplayOption.ADMINS_ONLY)%>>For administrators only</option>
                </select>
            </td>
        </tr>
        <tr>
            <td colspan="2">
                <br/>
                <%= button("Save").submit(true) %>&nbsp;
                <%= button("Cancel").href(new ActionURL(StudyController.ManageStudyAction.class, container)) %>
            </td>
        </tr>
    </table>
</labkey:form>
