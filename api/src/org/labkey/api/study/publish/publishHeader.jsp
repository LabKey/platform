<%
/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.publish.AbstractPublishConfirmAction" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AbstractPublishConfirmAction.PublishConfirmBean> me = (JspView<AbstractPublishConfirmAction.PublishConfirmBean>) HttpView.currentView();
    AbstractPublishConfirmAction.PublishConfirmBean bean = me.getModelBean();

    String dateOrVisit;
    if (bean.getTimepointType() == TimepointType.VISIT)
    {
        dateOrVisit = "Visit IDs";
    }
    else if (bean.getTimepointType() == TimepointType.DATE)
    {
        dateOrVisit = "Dates";
    }
    else
    {
        dateOrVisit = "either Dates or Visit IDs";
    }
%>
<labkey:errors/>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    window.onbeforeunload = LABKEY.beforeunload();
    LABKEY.setDirty(true);
</script>
<% if (bean.isMismatched()) { %>
<p>
    To reshow this form with Participant IDs and <%=h(dateOrVisit)%> associated with
    the specimens in the study, click on the Reset with Specimen Data button. To reshow with information from the assay,
    click on the Reset with Assay Data button.
</p>
<% } %>
    Participant IDs and <%=h(dateOrVisit)%> are required for all rows.
