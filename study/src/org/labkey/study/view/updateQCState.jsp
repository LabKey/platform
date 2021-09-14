<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.qc.DataState" %>
<%@ page import="org.labkey.api.qc.QCStateManager" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.template.FrameFactoryClassic" %>
<%@ page import="org.labkey.study.controllers.StudyController.DatasetAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.UpdateQCStateAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.UpdateQCStateForm" %>
<%@ page import="java.util.List" %>
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
    JspView<UpdateQCStateForm> me = (JspView<UpdateQCStateForm>) HttpView.currentView();
    UpdateQCStateForm bean = me.getModelBean();
    Container container = getContainer();
    List<DataState> states = QCStateManager.getInstance().getStates(container);
%>
<%
    FrameFactoryClassic.startTitleFrame(out, "QC State Change", null, null, null);
%>
<labkey:errors/>
<labkey:form action="<%=urlFor(UpdateQCStateAction.class)%>" method="POST">
    <input type="hidden" name="update" value="true" />
    <input type="hidden" name="datasetId" value="<%= bean.getDatasetId() %>" />
    <input type="hidden" name="dataRegionName" value="<%= h(bean.getDataRegionName()) %>" />
    <input type="hidden" name="<%= h(DataRegionSelection.DATA_REGION_SELECTION_KEY) %>" value="<%= h(bean.getDataRegionSelectionKey()) %>" />
    <table>
        <tr>
            <th>New QC State</th>
            <td>
                <select name="newState">
                    <option value="">[Unspecified]</option>
                <%
                    for (DataState state : states)
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
            <td><%= button("Update Status").submit(true) %> <%= button("Cancel").href(new ActionURL(DatasetAction.class,
                    container).addParameter(Dataset.DATASETKEY, bean.getDatasetId())) %></td>
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
