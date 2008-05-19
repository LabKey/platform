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
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.study.view.ParticipantWebPartFactory" %>
<%@ page import="org.labkey.api.study.SpecimenService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart bean = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    ActionURL postUrl = new ActionURL("Project", "customizeWebPart.post", ctx.getContainer());
    String participantId = bean.getPropertyMap().get(ParticipantWebPartFactory.PARTICIPANT_ID_KEY);
    String ptidCompletionBase = SpecimenService.get().getCompletionURLBase(ctx.getContainer(), SpecimenService.CompletionType.ParticipantId);
%>
<script type="text/javascript">LABKEY.requiresScript("completion.js");</script>
<p>Each participant webpart will display datasets from a single participant.</p>

<form action="<%=postUrl%>" method="post">
<table>
        <tr>
            <td>
                <input type="hidden" name="pageId" value="<%=bean.getPageId()%>">
                <input type="hidden" name="index" value="<%=bean.getIndex()%>">
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
        <td>
            <%=buttonImg("Submit")%>
            <%=buttonLink("Cancel", "begin.view")%>
        </td>
    </tr>
</table>
</form>