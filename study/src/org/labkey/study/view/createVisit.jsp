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
<%@ page import="org.labkey.api.study.TimepointType"%>
<%@ page import="org.labkey.api.study.Visit"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.VisitForm" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<VisitForm> me = (JspView<VisitForm>)HttpView.currentView();
    VisitForm form = me.getModelBean();
    VisitImpl v = form.getBean();

    StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
    boolean isDateBased = study != null && study.getTimepointType() == TimepointType.DATE;

    ActionURL returnURL;
    if (getActionURL().getParameter("returnUrl") != null)
        returnURL = new ActionURL(getActionURL().getParameter("returnUrl"));
    else
        returnURL = new ActionURL(StudyController.ManageVisitsAction.class, getContainer());
%>
<labkey:errors/>
<p style="width: 750px;">
<%
    if (isDateBased)
    {
%>
Use this form to create a new timepoint. A timepoint is a range of days defined in the study protocol. All subject data uploaded
to this study is assigned to a timepoint using the Date field. The assignment happens by computing the number of days between the Date
field in the uploaded data and that subject's StartDate.
<%
    }
    else
    {
%>
Use this form to create a new visit. A visit is a point in time defined in the study protocol. All data uploaded
to this study must be assigned to a visit. The assignment happens using a "Sequence Number" (otherwise known as Visit Id) that
is uploaded along with the data. This form allows you to define a range of sequence numbers that corresponds to the visit.
<%
    }
%>
</p>
<labkey:form action="<%=h(buildURL(StudyController.CreateVisitAction.class))%>" method="POST">
    <table class="lk-fields-table">
        <tr>
            <td class="labkey-form-label">Label&nbsp;<%=helpPopup("Label", "Descriptive label, e.g. 'Enrollment interview' or '2 Weeks'")%></td>
            <td>
                <input type="text" size="50" name="label" value="<%=h(v.getLabel())%>">
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label"><%=h(isDateBased ? "Day Range" : "VisitId/Sequence Range")%></td>
            <td>
                <input type="text" size="26" name="sequenceNumMin" value="<%=v.getSequenceNumMin()%>">-<input type="text" size="26" name="sequenceNumMax" value="<%=v.getSequenceNumMin()==v.getSequenceNumMax()?"":v.getSequenceNumMax()%>">
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
                            <option value="<%= type.getCode() %>"<%=selected(type.getCode() == visitTypeCode)%>><%=h(type.getMeaning())%></option>
                            <%
                        }
                    %>
                </select>
            </td>
        </tr>
<%
    if (!isDateBased)
    {
%>
    <tr>
        <%-- UNDONE: duplicated in editVisit.jsp --%>
        <td class="labkey-form-label">Visit Handling (advanced)<%=
            helpPopup("Visit Handling (advanced)",
                    "You may specify that unique sequence numbers should be based on visit date." +
                            "<p>This is for special handling of some log/unscheduled events.</p>" +
                            "<p>Make sure that the sequence number range is adequate (e.g #.0000-#.9999).</p>",
                    true)
        %></td>
        <td>
            <select name="sequenceNumHandling">
              <option selected value="<%=text(Visit.SequenceHandling.normal.name())%>">Normal</option>
              <option value="<%=text(Visit.SequenceHandling.logUniqueByDate.name())%>">Unique Log Events by Date</option>
            </select>
        </td>
    </tr>
<%
    }
%>
        <tr>
            <td class="labkey-form-label">Show By Default</td>
            <td>
                <input type="checkbox" name="showByDefault"<%=checked(!form.isReshow() || v.isShowByDefault())%>>
            </td>
        </tr>
    </table>
    <br/>
    <input type="hidden" name="returnUrl" value="<%= returnURL %>">
    <%= button("Save").submit(true) %>&nbsp;<%= button("Cancel").href(returnURL) %>
</labkey:form>