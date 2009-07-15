<%
/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.actions.PublishConfirmAction" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PublishConfirmAction.PublishConfirmBean> me = (JspView<PublishConfirmAction.PublishConfirmBean>) HttpView.currentView();
    PublishConfirmAction.PublishConfirmBean bean = me.getModelBean();
%>
<labkey:errors/>

<script type="text/javascript">
    function isTrue()
    {
        return true;
    }
    window.onbeforeunload = LABKEY.beforeunload(isTrue);
</script>
<% if (bean.isMismatched()) { %>
<p>
    To reshow this form with ParticipantIDs and <%= bean.isDateBased() ? "Date" : "Visit ID" %>s associated with
    the specimens in the study, click on the Reset with Specimen Data button. To reshow with information from the assay,
    click on the Reset with Assay Data button.
</p>
<% } %>
    Participant IDs and <%= bean.isDateBased() ? "Date" : "Visit ID" %>s are required for all rows.