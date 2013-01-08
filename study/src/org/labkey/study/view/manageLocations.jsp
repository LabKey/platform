<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.study.model.LocationImpl"%>
<%@ page import="org.labkey.study.model.StudyImpl"%>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyImpl> me = (JspView<StudyImpl>) HttpView.currentView();
    StudyImpl study = me.getModelBean();
%>
<labkey:errors/>
<form action="<%=h(buildURL(StudyController.ManageLocationsAction.class))%>" method="POST">
    <table>
        <tr>
            <th>&nbsp;</th>
            <th>Location Id</th>
            <th>Location Name</th>
            <th>Location Type</th>
        </tr>
        <%
            for (LocationImpl location : study.getLocations())
            {
        %>
            <tr>
                <td>&nbsp;</td>
                <td align="center">
                    <%= location.getLdmsLabCode()%>
                    <input type="hidden" name="ids" value="<%= location.getRowId()%>">
                </td>
                <td>
                    <input type="text" name="labels" size="40" value="<%= text(location.getLabel() != null ? h(location.getLabel()) : "") %>">
                </td>
                <td>
                    <%= h(location.getTypeString()) %>
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
            <td><%= generateSubmitButton("Save") %>&nbsp;<%= generateButton("Cancel", StudyController.ManageStudyAction.class)%></td>
            <td>&nbsp;</td>
        </tr>
    </table>
</form>