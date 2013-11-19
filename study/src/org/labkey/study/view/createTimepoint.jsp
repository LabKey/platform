<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Visit"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController.VisitForm" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<VisitForm> me = (JspView<VisitForm>)HttpView.currentView();
    VisitForm form = me.getModelBean();
    VisitImpl v = form.getBean();
%>
<labkey:errors/>
Use this form to create a new timepoint. A timepoint is a range of days defined in the study protocol. All subject data uploaded
to this study is assigned to a timepoint using the Date field. The assignment happens by computing the number of days between the Date
field in the uploaded data and that subject's StartDate.
<br>
<form action="<%=h(buildURL(StudyController.CreateVisitAction.class))%>" method="POST">
    <table>
<%--        <tr>
            <td class="labkey-form-label">Name&nbsp;<%=helpPopup("Name", "Short unique name, e.g. 'Enroll'")%></td>
            <td>
                <input type="text" size="50" name="name" value="<%=h(v.getName())%>">
            </td> 
        </tr> --%>
        <tr>
            <td class="labkey-form-label">Label&nbsp;<%=helpPopup("Label", "Descriptive label, e.g. '2 Weeks'")%></td>
            <td>
                <input type="text" size="50" name="label" value="<%=h(v.getLabel())%>">
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Day Range</td>
            <td>
                <input type="text" size="26" name="sequenceNumMin" value="<%=v.getSequenceNumMin()>0?v.getSequenceNumMin():""%>">--<input type="text" size="26" name="sequenceNumMax" value="<%=v.getSequenceNumMin()==v.getSequenceNumMax()?"":v.getSequenceNumMax()%>">
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Description&nbsp;<%=helpPopup("Description", "A short description of the visit, appears as hovertext on visit headers in study navigator and visit column in datasets.")%></td>
            <td>
                <textarea name="description" cols="50" rows="3"><%= h(v.getDescription()) %></textarea>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Type</td>
            <td>
                <select name="typeCode">
                    <option value="">[None]</option>
                    <%
                        char visitTypeCode = v.getTypeCode() == null ? '\t' : v.getTypeCode();
                        for (Visit.Type type : Visit.Type.values())
                        {
                            %>
                            <option value="<%= type.getCode() %>"<%=selected(type.getCode() == visitTypeCode)%>><%= type.getMeaning() %></option>
                            <%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Show By Default</td>
            <td>
                <input type="checkbox" name="showByDefault"<%=checked(!form.isReshow() || v.isShowByDefault())%>>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><%= this.generateSubmitButton("Save")%>&nbsp;<%= this.generateButton("Cancel", StudyController.ManageVisitsAction.class)%></td>
        </tr>
    </table>
</form>