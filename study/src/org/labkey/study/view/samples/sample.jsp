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
<%@ page import="org.labkey.api.study.Location"%>
<%@ page import="org.labkey.api.study.StudyService"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page import="org.labkey.study.controllers.samples.SpecimenController" %>
<%@ page import="org.labkey.study.model.Specimen" %>
<%@ page import="org.labkey.study.model.SpecimenComment" %>
<%@ page import="org.labkey.study.security.permissions.SetSpecimenCommentsPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenController.SpecimenEventBean> me = (JspView<SpecimenController.SpecimenEventBean>) HttpView.currentView();
    SpecimenController.SpecimenEventBean bean = me.getModelBean();
    Specimen sample = bean.getSpecimen();
    Location originatingLocation = SampleManager.getInstance().getOriginatingLocation(sample);
    SpecimenComment comment = SampleManager.getInstance().getSpecimenCommentForVial(sample);
    ActionURL commentsLink = new ActionURL(SpecimenController.UpdateCommentsAction.class, sample.getContainer());
    commentsLink.addParameter("rowId", sample.getRowId());
    commentsLink.addParameter("referrer", getViewContext().getActionURL().getLocalURIString());
%>
<table>
    <tr>
        <th align="right">Globally Unique ID</th>
        <td><%= h(sample.getGlobalUniqueId()) %></td>
    </tr>
    <tr>
        <th align="right"><%= h(StudyService.get().getSubjectNounSingular(sample.getContainer())) %></th>
        <td><%= h(id(sample.getPtid())) %></td>
    </tr>
    <tr>
        <th align="right">Visit</th>
        <td><%= h(sample.getVisitDescription()) %>&nbsp;<%= sample.getVisitValue() %></td>
    </tr>
    <tr>
        <th align="right">Volume</th>
        <td><%= h(sample.getVolume()) %>&nbsp;<%= h(sample.getVolumeUnits()) %></td>
    </tr>
    <tr>
        <th align="right">Collection Date</th>
        <td><%= h(sample.getDrawTimestamp() != null ? formatDateTime(sample.getDrawTimestamp()) : "Unknown") %></td>
    </tr>
    <tr>
        <th align="right">Collection Location</th>
        <td><%= h(originatingLocation != null ? originatingLocation.getDisplayName() : "Unknown") %></td>
    </tr>
    <tr>
        <th align="right">Comments and QC</th>
        <td>
            <%= h(comment != null ? comment.getComment() : null) %>
            <% if (sample.getContainer().hasPermission(getViewContext().getUser(), SetSpecimenCommentsPermission.class)) { %>
                <%= textLink("update", commentsLink) %>
            <% } %>
        </td>
    </tr>
</table>
<br>
<%
if (comment != null)
{
    if (comment.isQualityControlFlag())
    {
%>
    <strong><span class="labkey-error">Vial is flagged for quality control.</span></strong>
<%
    }
    if (comment.getQualityControlComments() != null && comment.getQualityControlComments().length() > 0)
    {
%>
     <%= h(comment.getQualityControlComments()) %><br>
<%
    }
    if (comment.isQualityControlFlagForced())
    {
%>
    <br><strong>NOTE</strong>: Quality control state for this vial was set manually; it will remain
    <%= text(comment.isQualityControlFlag() ? "flagged" : "unflagged") %> until manually changed.<br>
<%
    }
}
if (bean.getReturnUrl() != null && bean.getReturnUrl().length() > 0)
{
%>
<br><%= textLink("return to vial view", bean.getReturnUrl() )%>
<%
    }
%>
