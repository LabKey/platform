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
Use this form to create a new visit. A visit is a point in time defined in the study protocol. All data uploaded
to this study must be assigned to a visit. The assignment happens using a "Sequence Number" (otherwise known as Visit Id) that
is uploaded along with the data. This form allows you to define a range of sequence numbers that will be correspond to the visit.
<br>
<form action="<%=h(buildURL(StudyController.CreateVisitAction.class))%>" method="POST">
    <table>
<%--        <tr>
            <th align="right">Name&nbsp;<%=helpPopup("Name", "Short unique name, e.g. 'Enroll'")%></th>
            <td>
                <input type="text" size="50" name="name" value="<%=h(v.getName())%>">
            </td> 
        </tr> --%>
        <tr>
            <td class=labkey-form-label>Label&nbsp;<%=helpPopup("Label", "Descriptive label, e.g. 'Enrollment interview'")%></td>
            <td>
                <input type="text" size="50" name="label" value="<%=h(v.getLabel())%>">
            </td>
        </tr>
        <tr>
            <td class=labkey-form-label>Sequence Range</td>
            <td>
                <input type="text" size="20" name="sequenceNumMin" value="<%=v.getSequenceNumMin()>0?v.getSequenceNumMin():""%>">--<input type="text" size="20" name="sequenceNumMax" value="<%=v.getSequenceNumMin()==v.getSequenceNumMax()?"":v.getSequenceNumMax()%>">
            </td>
        </tr>
        <tr>
            <td class=labkey-form-label>Type</td>
            <td>
                <select name="typeCode">
                    <option value="">[None]</option>
                    <%
                        char visitTypeCode = v.getTypeCode() == null ? '\t' : v.getTypeCode();
                        for (Visit.Type type : Visit.Type.values())
                        {
                            %>
                            <option value="<%= type.getCode() %>"<%=selected(type.getCode() == visitTypeCode)%>><%=h(type.getMeaning())%></option>
                            <%
                        }
                    %>
                </select>
            </td>
        </tr>
    <tr>
        <%-- UNDONE: duplicated in editVisit.jsp --%>
        <td class=labkey-form-label>Visit Handling (advanced)<%=
            helpPopup("SequenceNum handling",
                    "You may specificy that unique sequence numbers should be based on visit date.<br>"+
                    "This is for special handling of some log/unscheduled events.<p>"+
                    "Make sure that the sequence number range is adequate (e.g #.0000-#.9999)",
                    true)
        %></td>
        <td>
            <select name="sequenceNumHandling">
              <option selected value="<%=text(Visit.SequenceHandling.normal.name())%>">Normal</option>
              <option value="<%=text(Visit.SequenceHandling.logUniqueByDate.name())%>">Unique Log Events by Date</option>
            </select>
        </td>
    </tr>
        <tr>
            <td class=labkey-form-label>Show By Default</td>
            <td>
                <input type="checkbox" name="showByDefault"<%=checked(v.isShowByDefault())%>>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><%= this.generateSubmitButton("Save")%>&nbsp;<%=generateButton("Cancel", StudyController.ManageVisitsAction.class)%></td>
        </tr>
    </table>
</form>