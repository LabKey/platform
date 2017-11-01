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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.QCState" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.List" %>
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
    JspView<StudyController.UpdateQCStateForm> me = (JspView<StudyController.UpdateQCStateForm>) HttpView.currentView();
    StudyController.UpdateQCStateForm bean = me.getModelBean();
    Container container = getContainer();
    List<QCState> states = StudyManager.getInstance().getQCStates(container);
%>
<%
    FrameFactoryClassic.startTitleFrame(out, "QC State Change", null, null, null);
%>
<labkey:errors/>
<labkey:form action="<%=h(buildURL(StudyController.UpdateQCStateAction.class))%>" method="POST">
    <input type="hidden" name="update" value="true" />
    <input type="hidden" name="datasetId" value="<%= bean.getDatasetId() %>" />
    <input type="hidden" name="<%= h(DataRegionSelection.DATA_REGION_SELECTION_KEY) %>" value="<%= h(bean.getDataRegionSelectionKey()) %>" />
    <table>
        <tr>
            <th>New QC State</th>
            <td>
                <select name="newState">
                    <option value="">[Unspecified]</option>
                <%
                    for (QCState state : states)
                    {
                        boolean selected = bean.getNewState() != null && bean.getNewState() == state.getRowId();
                %>
                    <option value="<%= state.getRowId() %>"<%=selected(selected)%>><%= h(state.getLabel()) %></option>
                <%
                    }
                %>
                </select>
            </td>
        </tr>
        <tr>
            <th>Comments</th>
            <td>
                <textarea rows="10" cols="60" name="comments"><%= h(bean.getComments()) %></textarea><br>

            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><%= button("Update Status").submit(true) %> <%= button("Cancel").href(new ActionURL(StudyController.DatasetAction.class,
                    container).addParameter(DatasetDefinition.DATASETKEY, bean.getDatasetId())) %></td>
        </tr>
    </table>
</labkey:form>
<%
    FrameFactoryClassic.endTitleFrame(out);
    FrameFactoryClassic.startTitleFrame(out, "Selected Data Rows", null, null, null);
%>
<% me.include(bean.getQueryView(), out); %>
<%
    FrameFactoryClassic.endTitleFrame(out);
%>
