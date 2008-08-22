<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.study.SpecimenService" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.study.view.ParticipantWebPartFactory" %>
<%@ page import="java.util.EnumSet" %>
<%@ page import="org.labkey.study.model.QCStateSet" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart bean = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    ActionURL postUrl = new ActionURL("Project", "customizeWebPart.post", ctx.getContainer());
    String participantId = bean.getPropertyMap().get(ParticipantWebPartFactory.PARTICIPANT_ID_KEY);
    String ptidCompletionBase = SpecimenService.get().getCompletionURLBase(ctx.getContainer(), SpecimenService.CompletionType.ParticipantId);

    String selectedData = bean.getPropertyMap().get(ParticipantWebPartFactory.DATA_TYPE_KEY);
    if (selectedData == null)
        selectedData = ParticipantWebPartFactory.DataType.ALL.name();
    
    boolean includePrivateData = Boolean.parseBoolean(bean.getPropertyMap().get(ParticipantWebPartFactory.QC_STATE_INCLUDE_PRIVATE_DATA_KEY));
%>
<script type="text/javascript">LABKEY.requiresScript("completion.js");</script>
<p>Each participant webpart will display datasets from a single participant.</p>

<form action="<%=postUrl%>" method="post">
<table>
    <tr>
        <td>
            <input type="hidden" name="pageId" value="<%=bean.getPageId()%>">
            <input type="hidden" name="index" value="<%=bean.getIndex()%>">
            Participant ID:
        </td>
        <td>
            <input type="text"
                   name="<%= ParticipantWebPartFactory.PARTICIPANT_ID_KEY %>"
                   value="<%= h(participantId)%>"
                   onKeyDown="return ctrlKeyCheck(event);"
                   onBlur="hideCompletionDiv();"
                   autocomplete="off"
                   onKeyUp="return handleChange(this, event, '<%= ptidCompletionBase %>');">
        </td>
    </tr>
    <tr>
        <td>Data type to display:</td>
        <td>
            <select name="<%=ParticipantWebPartFactory.DATA_TYPE_KEY%>">
                <%
                    for (ParticipantWebPartFactory.DataType type : EnumSet.allOf(ParticipantWebPartFactory.DataType.class))
                    {
                        %>
                <option value="<%=type.name()%>"<% if (selectedData.equals(type.name())) out.print(" selected=\"selected\""); %>><%=type.toString()%></option>
                        <%
                    }
                %>

            </select>
        </td>
    </tr>
    <%
        if (StudyManager.getInstance().showQCStates(ctx.getContainer()))
        {
    %>
    <tr>
        <td>QC state to display:</td>
        <td>
            <select name="<%=ParticipantWebPartFactory.QC_STATE_INCLUDE_PRIVATE_DATA_KEY%>">
                <option value="false">Public data</option>
                <option value="true" <%= includePrivateData ? "SELECTED" : "" %>>All data</option>
            </select>
        </td>
    </tr>
    <%
        }
    %>
    <tr>
        <td>
            <%=generateSubmitButton("Submit")%>
            <%=generateButton("Cancel", "begin.view")%>
        </td>
    </tr>
</table>
</form>