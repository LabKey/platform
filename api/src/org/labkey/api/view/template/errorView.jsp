<%
/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.util.ErrorRenderer" %>
<%@ page import="org.labkey.api.util.ErrorView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("core/css/core.css");
        dependencies.add("clientapi");
    }
%>
<%
    ErrorView me = HttpView.currentView();
    ErrorRenderer model = me.getModelBean();

    String appId = "error-handler-app-" + UniqueID.getServerSessionScopedUID();
%>

<div id="<%=h(appId)%>"></div>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    /*
         This error page may be invoked without the themes having been loaded for this container.
         We load the theme artifact for this container here to ensure the correct theme is loaded
         as this cannot be resolved during "addClientDependencies()" for this view.
     */
    LABKEY.requiresCss(<%=q("/core/css/" + PageFlowUtil.resolveThemeName(getContainer()) + ".css")%>);

    LABKEY.requiresScript('gen/errorHandler', function() {
    // LABKEY.requiresScript('http://localhost:3001/errorHandler.js', function() {

        LABKEY.App.loadApp('errorHandler', <%=q(appId)%>, {
            errorDetails : {
                message: <%=q(model.getHeading())%>,
                errorType: <%=q(model.getErrorType())%>,
                errorCode: <%=q(model.getErrorCode())%>,
                advice: <%=q(model.getAdvice())%>
            }
        });
    });
</script>