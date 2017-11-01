<%
/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.PopupMenu" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.SpecimenManager" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.Vial" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.io.Writer" %>
<%@ page import="org.labkey.api.view.template.FrameFactoryClassic" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        // TODO: --Ext3-- This should be declared as part of the included views
        dependencies.add("clientapi/ext3");
    }
%>
<%
    JspView<SpecimenController.UpdateSpecimenCommentsBean> me = (JspView<SpecimenController.UpdateSpecimenCommentsBean>) HttpView.currentView();
    SpecimenController.UpdateSpecimenCommentsBean bean = me.getModelBean();
    Container container = getContainer();

    NavTree copyButton = createCopyCommentButton(bean.getParticipantVisitMap(), StudyManager.getInstance().getStudy(container), getUser());
%>
<labkey:form action="<%=h(buildURL(SpecimenController.UpdateCommentsAction.class))%>" name="updateComments" id="updateCommentForm" method="POST">
    <input type="hidden" name="copyToParticipant" value="false">
    <input type="hidden" name="deleteVialComment" value="false">
    <input type="hidden" name="copyParticipantId" value="0">
    <input type="hidden" name="copySampleId" value="-1">
<%
    if (SpecimenManager.getInstance().getDisplaySettings(container).isEnableManualQCFlagging())
    {
        FrameFactoryClassic.startTitleFrame(out, "Quality Control Flags", null, null, null);
        if (bean.isMixedFlagState())
        {
%>
        <b>Note:</b> A subset of the selected vials have quality control flags.  See vial list below for details.<p>
<%
        }
%>
        What quality control state should be applied to the selected vials?<br>
        <input type="radio" name="qualityControlFlag" value="" CHECKED> Do not change quality control state<br>
<%
        // allow users to flag if our current state is mixed or unflagged:
        if (bean.isMixedFlagState() || !bean.isCurrentFlagState())
        {
%>
          <input type="radio" name="qualityControlFlag" value="true"> Add quality control flag<br>
<%
        }
        // allow users to unflag if our current state is mixed or flagged:
        if (bean.isMixedFlagState() || bean.isCurrentFlagState())
        {
%>
            <input type="radio" name="qualityControlFlag" value="false"> Remove quality control flag<p>
<%
        }
        FrameFactoryClassic.endTitleFrame(out);
    }
    FrameFactoryClassic.startTitleFrame(out, "Comments", null, null, null);
%>
<labkey:errors/>
    <%
        if (bean.isMixedComments())
        {
    %>
    <b>Note:</b> Some or all of the selected vials have existing comments.  See vial list below for details.<p>
    What action should be performed on vials with existing comments?<br>
    <input type="radio" name="conflictResolve" value="REPLACE" CHECKED> Replace existing comments with new comments<br>
    <input type="radio" name="conflictResolve" value="APPEND"> Append new comments to existing comments<br>
    <input type="radio" name="conflictResolve" value="SKIP"> Do not change comments for vials with existing comments<p>
    New comment:<br>
    <%
        }
        else
        {
    %>
    <input type="hidden" name="conflictResolve" value="REPLACE">
    <%
        }
    %>
    <input type="hidden" name="referrer" value="<%= h(bean.getReferrer()) %>" />
    <input type="hidden" name="saveCommentsPost" value="true" />
    <%
        for (Vial vial : bean.getVials())
        {
    %>
        <input type="hidden" name="rowId" value="<%= vial.getRowId() %>">
    <%
        }
    %>
    <table>
        <tr>
            <td>
                <textarea rows="10" cols="60" name="comments"><%= h(bean.getCurrentComment()) %></textarea><br>

            </td>
        </tr>
        <tr>
            <td>
                <%= button("Save Changes").submit(true) %>
                <%
                    if (!StringUtils.isBlank(bean.getCurrentComment()) && copyButton != null)
                    {
                        PopupMenu menu = new PopupMenu(copyButton);
                        menu.render(out);
                    }
                %>
                <%= button("Cancel").href(new ActionURL(bean.getReferrer())) %>
            </td>
        </tr>
    </table>
</labkey:form>
<%
    FrameFactoryClassic.endTitleFrame(out);
    FrameFactoryClassic.startTitleFrame(out, "Selected Vials", null, null, null);
%>
<% me.include(bean.getSpecimenQueryView(), out); %>
<%
    FrameFactoryClassic.endTitleFrame(out);
%>

<%!
    private NavTree createCopyCommentButton(Map<String, Map<String, Long>> pvMap, StudyImpl study, User user)
    {
        boolean hasParticipantMenu = study.getParticipantCommentDatasetId() != null && study.getParticipantCommentDatasetId() != -1;
        boolean hasParticipantVisitMenu = study.getParticipantVisitCommentDatasetId() != null && study.getParticipantVisitCommentDatasetId() != -1;

        if (hasParticipantMenu)
        {
            DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, study.getParticipantCommentDatasetId());
            hasParticipantMenu = def != null && def.canWrite(user);
        }

        if (hasParticipantVisitMenu)
        {
            DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, study.getParticipantVisitCommentDatasetId());
            hasParticipantVisitMenu = def != null && def.canWrite(user);
        }

        if (hasParticipantMenu || hasParticipantVisitMenu)
        {
            NavTree button = new NavTree("Copy or Move Comment(s)");

            NavTree moveButton = new NavTree("Move");
            moveButton.setId("Comment:Move");
            NavTree copyButton = new NavTree("Copy");
            copyButton.setId("Comment:Copy");

            button.addChild(moveButton);
            button.addChild(copyButton);

            addParticipantMenuItems(moveButton, pvMap, hasParticipantMenu, hasParticipantVisitMenu, true);
            addParticipantMenuItems(copyButton, pvMap, hasParticipantMenu, hasParticipantVisitMenu, false);

            return button;
        }
        return null;
    }

    private void addParticipantMenuItems(NavTree button, Map<String, Map<String, Long>> pvMap,
                                         boolean hasParticipantMenu, boolean hasParticipantVisitMenu, boolean isMove)
    {
        String subjectNoun = StudyService.get().getSubjectNounSingular(getContainer());
        // participant comments
        if (hasParticipantMenu)
        {
            StringBuilder sb = new StringBuilder();
            NavTree participantItem = new NavTree("To " + subjectNoun, "#");
            participantItem.setId(isMove ? "Move:ToParticipant" : "Copy:ToParticipant");
            button.addChild(participantItem);

            for (String ptid : pvMap.keySet())
            {
                NavTree subItem = new NavTree(ptid, "#");
                sb.setLength(0);
                if (isMove)
                    sb.append("if (confirm('This will permanently remove all vial comments for the displayed vials. Continue?')){");
                sb.append("document.updateComments.copyToParticipant.value='true';");
                sb.append("document.updateComments.copyParticipantId.value='").append(ptid).append("';");
                sb.append("document.updateComments.deleteVialComment.value='").append(isMove).append("';");
                sb.append("document.updateComments.submit()");
                if (isMove)
                    sb.append("}");

                subItem.setScript(sb.toString());
                if (isMove)
                    subItem.setId("MovePtid:" + ptid);
                else
                    subItem.setId("CopyPtid:" + ptid);
                participantItem.addChild(subItem);
            }
        }

        // participant/visit comments
        if (hasParticipantVisitMenu)
        {
            StringBuilder sb = new StringBuilder();
            NavTree participantVisitItem = new NavTree("To " + subjectNoun + "/Visit", "#");
            participantVisitItem.setId(isMove ? "Move:ToParticipantVisit" : "Copy:ToParticipantVisit");
            button.addChild(participantVisitItem);
            for (Map.Entry<String, Map<String, Long>> entry : pvMap.entrySet())
            {
                NavTree ptidItem = new NavTree(entry.getKey());
                ptidItem.setId("PtidVisit:" + entry.getKey());

                for (Map.Entry<String, Long> visitEntry : entry.getValue().entrySet())
                {
                    NavTree visitItem = new NavTree(visitEntry.getKey());
                    sb.setLength(0);
                    if (isMove)
                        sb.append("if (confirm('This will permanently remove all vial comments for the displayed vials. Continue?')){");
                    sb.append("document.updateComments.copyToParticipant.value='true';");
                    sb.append("document.updateComments.copySampleId.value='").append(visitEntry.getValue()).append("';");
                    sb.append("document.updateComments.deleteVialComment.value='").append(isMove).append("';");
                    sb.append("document.updateComments.submit()");
                    if (isMove)
                        sb.append("}");

                    visitItem.setScript(sb.toString());
                    ptidItem.addChild(visitItem);
                }
                participantVisitItem.addChild(ptidItem);
            }
        }
    }
%>