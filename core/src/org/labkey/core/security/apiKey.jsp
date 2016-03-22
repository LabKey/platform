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
        resources.add(ClientDependency.fromPath("internal/jQuery"));
        resources.add(ClientDependency.fromPath("internal/clipboard/clipboard-1.5.9.min.js"));
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
<br/><br/>
<input id="session-token" value="<%=h(id)%>" style="width: 300px;" readonly/>
<%= button("Copy to clipboard").attributes("data-clipboard-target=\"#session-token\"") %>
<br/><br/>
<%= button("Done").href(returnURL) %>
<script type="application/javascript">
    (function($) {
        var clip = new Clipboard('.labkey-button');
        clip.on('success', function(e) {
            $(e.trigger).html('Copied!'); e.clearSelection();
            setTimeout(function() { $(e.trigger).html('Copy to clipboard'); }, 2500);
        });
        clip.on('error', function(e) { $(e.trigger).html('Press Ctrl+C to copy'); });
    })(jQuery);
</script>
