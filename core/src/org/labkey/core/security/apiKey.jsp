<%
/*
 * Copyright (c) 2016 LabKey Corporation
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
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("internal/ZeroClipboard/ZeroClipboard.js"));
        return resources;
    }
%>
<%
    ReturnUrlForm form = ((JspView<ReturnUrlForm>) HttpView.currentView()).getModelBean();
    ActionURL alternativeURL = urlProvider(ProjectUrls.class).getBeginURL(getContainer());
    ActionURL returnURL = form.getReturnActionURL(alternativeURL);
    String id = "session:" + getViewContext().getRequest().getSession().getId();
%>
This API key can be used to authorize client code accessing LabKey Server using one of the <%=helpLink("viewApis", "LabKey Client APIs")%>. Using an API key avoids
copying and storing your credentials on the client machine. Also, all client API access is tied to the current browser session, which means the code runs under the
current context (e.g., your user, your authorizations, your declared terms of use and PHI level, your impersonation state, etc.). It also means the API key will
likely lose authorization when the session expires, e.g., when you sign out via the browser or the server automatically times out your session.
<br><br>
<%=h(id)%><br><br>
<%=button("Done").href(returnURL).build()%><%=button("Copy to Clipboard").id("clipboard").attributes("data-clipboard-text=\"" + h(id) + "\"").build()%>
<script type="application/javascript">
    var target = document.getElementById("clipboard");
    // These styles make the copy-to-clipboard button act like a normal button, even though the flash movie is receiving all the events
    ZeroClipboard.config({hoverClass: "zeroclipboard-button-hover", activeClass: "zeroclipboard-button-active"});
    new ZeroClipboard(target).on("error", function(e)
    {
        // Disable the button at the first sign of trouble (flash not installed, plug-in not installed, plug-in not enabled, etc.)
        // TODO: Would be nice to add a hover over the button explaining that flash is required. Could also supply a reason (e.message).
        target.className = "labkey-disabled-button";
//        target.style.display = "none";  // Alternative is to hide the button altogether
    });
</script>
