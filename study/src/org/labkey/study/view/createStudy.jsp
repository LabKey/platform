<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="java.util.Date" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    StudyController.StudyPropertiesForm form = (StudyController.StudyPropertiesForm) getModelBean();
%>
<%=PageFlowUtil.getStrutsError(request, "main")%>

<%
    if (!getViewContext().hasPermission(ACL.PERM_ADMIN))
    {%>
        A study has not been created in this folder. Please contact an administrator.
<%  } else { %>
<labkey:errors/>
<form action="createStudy.post" method="POST">
    <table cellspacing="3" class="normal">
        <tr>
            <th align="left">Study Label</th>
            <td align="left"><input type="text" size="40" name="label" value="<%= h(form.getLabel()) %>"></td>
        </tr>
            <tr>
                <th align="left">Timepoints <%=helpPopup("Timepoint Styles", "Timepoints in the study may be defined using dates, or using pre-determined Visits assigned by the study administrator.<br>When using visits, administrators assign a label and a range of numerical \"Sequence Numbers\" that are grouped into visits.<br> If using dates, data can be grouped by day or week.")%></th>
                <td align="left"><input type="radio" name="dateBased" value="true" <%=form.isDateBased() ? "CHECKED" : ""%>> Dates &nbsp;&nbsp;
                    <input type="radio" name="dateBased" value="false" <%=form.isDateBased() ? "" : "CHECKED"%>> Assigned Visits
                </td>
            </tr>
        <tr>
            <th align="left">Start Date<%=helpPopup("Start Date", "A start date is required for studies that are date based.")%></th>
            <td align="left"><input type="text" name="startDate" value="<%=h(DateUtil.formatDate(form.getStartDate()))%>">
            </td>
        </tr>
        <tr>
            <th align="left">Specimen Repository</th>
            <td align="left"><input type="radio" name="simpleRepository" value="true" <%=form.isSimpleRepository() ? "CHECKED" : "" %>> Standard Specimen Repository
                <input type="radio" name="simpleRepository" value="false" <%=form.isSimpleRepository() ? "" : "CHECKED" %>> Advanced (External) Specimen Repository</td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td align="left">The standard specimen repository allows you to upload a list of available specimens. The advanced specimen repository
                relies on an external set of tools to track movement of specimens between sites. The advanced system also enables a customizable specimen
                request system.</td>
        </tr>
        <tr>
            <th align="left">Editable Dataset Data<%=helpPopup("Editable Dataset Data", "If dataset data is editable, users with update permission will be able to edit dataset data")%></th>
            <td align="left"><input type="checkbox" name="datasetRowsEditable" <%= form.isDatasetRowsEditable() ? "checked=\"true\"" : "" %>></td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><%= buttonImg("Create Study")%>&nbsp;<%= buttonLink("Back", "#", "window.history.back();return null;")%></td>
        </tr>
    </table>
</form>
<%  } %>