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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.study.controllers.StudyController"%>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    HttpView me = (HttpView) HttpView.currentView();
    StudyController.VisitForm form = (StudyController.VisitForm) me.getViewContext().get("form");
    VisitImpl v = form.getBean();
%>
<labkey:errors/>
Use this form to create a new timepoint. A timepoint is a range of days defined in the study protocol. All subject data uploaded
to this study is assigned to a timepoint using the Date field. The assignment happens by computing the number of days between the Date
field in the uploaded data and that subject's StartDate.
<br>
<form action="createVisit.post" method="POST">
    <table>
<%--        <tr>
            <th align="right">Name&nbsp;<%=helpPopup("Name", "Short unique name, e.g. 'Enroll'")%></th>
            <td>
                <input type="text" size="50" name="name" value="<%=h(v.getName())%>">
            </td> 
        </tr> --%>
        <tr>
            <th align="right">Label&nbsp;<%=helpPopup("Label", "Descriptive label, e.g. '2 Weeks'")%></th>
            <td>
                <input type="text" size="50" name="label" value="<%=h(v.getLabel())%>">
            </td>
        </tr>
        <tr>
            <th align="right">Day Range</th>
            <td>
                <input type="text" size="20" name="sequenceNumMin" value="<%=v.getSequenceNumMin()>0?v.getSequenceNumMin():""%>">--<input type="text" size="20" name="sequenceNumMax" value="<%=v.getSequenceNumMin()==v.getSequenceNumMax()?"":v.getSequenceNumMax()%>">
            </td>
        </tr>
        <tr>
            <th align="right">Type</th>
            <td>
                <select name="typeCode">
                    <option value="">[None]</option>
                    <%
                        char visitTypeCode = v.getTypeCode() == null ? '\t' : v.getTypeCode();
                        for (Visit.Type type : Visit.Type.values())
                        {
                            %>
                            <option value="<%= type.getCode() %>" <%=type.getCode()==visitTypeCode?"selected":""%>><%= type.getMeaning() %></option>
                            <%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <th align="right">Show In Study Overview</th>
            <td>
                <input type="checkbox" name="showByDefault" <%=v.isShowByDefault()?"checked":""%>>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><%= this.generateSubmitButton("Save")%>&nbsp;<%= this.generateButton("Cancel", "manageVisits.view")%></td>
        </tr>
    </table>
</form>