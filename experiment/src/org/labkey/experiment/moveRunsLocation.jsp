<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.MoveRunsBean" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<MoveRunsBean> me = (JspView<MoveRunsBean>) HttpView.currentView();
    MoveRunsBean bean = me.getModelBean();
%>
<script type="text/javascript">
    function moveTo(targetContainerId)
    {
        document.forms["moveForm"].targetContainerId.value = targetContainerId;
        document.forms["moveForm"].submit();
    }
</script>
<labkey:form name="moveForm" action="<%=h(buildURL(ExperimentController.MoveRunsAction.class))%>" method="POST">
    <%
        for (String id : DataRegionSelection.getSelected(getViewContext(), false))
        { %>
            <input type="hidden" name="<%= DataRegion.SELECT_CHECKBOX_NAME%>" value="<%= h(id) %>" /><%
        }
    %>
    <input type="hidden" name="<%= DataRegionSelection.DATA_REGION_SELECTION_KEY %>" value="<%= bean.getDataRegionSelectionKey() %>" />
    <input type="hidden" name="targetContainerId" />
<table class="labkey-data-region">
<tr>
    <td style="padding-left:0">
        Folders must be configured with a pipeline root to be valid destinations.
        Those without pipeline roots are still shown in the list, but are not linked.
    </td>
</tr>
<tr><td>&nbsp;</td></tr>
<%=bean.getContainerTree().render()%>
</table>

</labkey:form>
