<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.samples.settings.DisplaySettings" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DisplaySettings> me =
            (JspView<DisplaySettings>) HttpView.currentView();
    DisplaySettings bean = me.getModelBean();
    Container container = HttpView.getRootContext().getContainer();
%>

<%=PageFlowUtil.getStrutsError(request, "main")%>
<form action="handleUpdateDisplaySettings.post" method="POST">
    <table class="labkey-manage-display" width=500>
        <tr>
            <td colspan="2">The specimen request system can display warning icons when one or zero vials of any primary specimen are available for request.  The icon will appear next to all vials of that the primary specimen.</td>
        </tr>
        <tr>
            <th align="right">Display warning icon when available vial count reaches one:</th>
            <td>
                <select name="lastVial">
                    <option value="<%= DisplaySettings.DisplayOption.NONE.name() %>"
                            <%= bean.getLastVialEnum() == DisplaySettings.DisplayOption.NONE ? "SELECTED" : ""%>>Never</option>
                    <option value="<%= DisplaySettings.DisplayOption.ALL_USERS.name() %>"
                            <%= bean.getLastVialEnum() == DisplaySettings.DisplayOption.ALL_USERS ? "SELECTED" : ""%>>For all users</option>
                    <option value="<%= DisplaySettings.DisplayOption.ADMINS_ONLY.name() %>"
                            <%= bean.getLastVialEnum() == DisplaySettings.DisplayOption.ADMINS_ONLY ? "SELECTED" : ""%>>For administrators only</option>
                </select>
            </td>
        </tr>
        <tr>
            <th align="right">Display warning icon when available vial count reaches zero:</th>
            <td>
                <select name="zeroVials">
                    <option value="<%= DisplaySettings.DisplayOption.NONE.name() %>"
                            <%= bean.getZeroVialsEnum() == DisplaySettings.DisplayOption.NONE ? "SELECTED" : ""%>>Never</option>
                    <option value="<%= DisplaySettings.DisplayOption.ALL_USERS.name() %>"
                            <%= bean.getZeroVialsEnum() == DisplaySettings.DisplayOption.ALL_USERS ? "SELECTED" : ""%>>For all users</option>
                    <option value="<%= DisplaySettings.DisplayOption.ADMINS_ONLY.name() %>"
                            <%= bean.getZeroVialsEnum() == DisplaySettings.DisplayOption.ADMINS_ONLY ? "SELECTED" : ""%>>For administrators only</option>
                </select>
            </td>
        </tr>
        <tr>
            <th>&nbsp;</th>
            <td>
                <%= generateSubmitButton("Save") %>&nbsp;
                <%= generateButton("Cancel", new ActionURL(StudyController.ManageStudyAction.class, container))%>
            </td>
        </tr>
    </table>
</form>
