<%
/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.query.QueryUrls" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="static org.apache.commons.lang3.StringUtils.stripEnd" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("internal/clipboard/clipboard-1.5.9.min.js");
    }
%>
<%
    ReturnUrlForm form = ((JspView<ReturnUrlForm>) HttpView.currentView()).getModelBean();
    ActionURL alternativeURL = urlProvider(ProjectUrls.class).getBeginURL(getContainer());
    ActionURL returnURL = form.getReturnActionURL(alternativeURL);
    URLHelper baseServerURL = null;
    try
    {
        String baseUrl = stripEnd(AppProps.getInstance().getBaseServerUrl(),"/") + AppProps.getInstance().getContextPath() + "/";
        baseServerURL = new URLHelper(baseUrl);
        if (baseServerURL.getHost().equalsIgnoreCase("localhost"))
            baseServerURL = null;
        else if (baseServerURL.getHost().equals("127.0.0.1"))
            baseServerURL = null;
    }
    catch (IllegalArgumentException ex)
    {
        %><%=ex.getMessage()%><%
    }

    boolean apiKeys = AppProps.getInstance().isAllowApiKeys();
    boolean sessionKeys = AppProps.getInstance().isAllowSessionKeys();

    if (getUser().isInSiteAdminGroup())
    {
        if (!apiKeys && !sessionKeys)
        {
            %>API keys are currently disabled on this site. <%
        }
%>
As a site administrator, you can configure API keys on the <%=PageFlowUtil.unstyledTextLink("Site Settings page", urlProvider(AdminUrls.class).getCustomizeSiteURL())%>.

<%
        if (apiKeys)
        {
%>
You can manage API keys generated on the server via <%=PageFlowUtil.unstyledTextLink("this query", urlProvider(QueryUrls.class).urlExecuteQuery(ContainerManager.getRoot(), "core", "APIKeys"))%>.
<%
        }
%>
<br/><br/>
<%
    }
%>
API keys are used to authorize client code accessing LabKey Server using one of the <%=helpLink("viewApis", "LabKey Client APIs")%>. You can authenticate with
an API key to avoid using and storing your LabKey password on the client machine. An API key can be specified in .netrc, provided to API functions, or used
with external clients that support Basic authentication. API keys have security benefits over passwords (they are tied to a specific server, they're usually
configured to expire, and they can be revoked), but a valid API key provides complete access to your data and actions, so it should be kept secret.
<br/><br/>
<%
    if (apiKeys)
    {
        int expiration = AppProps.getInstance().getApiKeyExpirationSeconds();
        final String expirationMessage;

        if (-1 == expiration)
        {
            expirationMessage = "never expire, although administrators can revoke them manually.";
        }
        else
        {
            String duration = DateUtil.formatDuration(expiration * 1000L);
            duration = StringUtils.replaceEach(duration, new String[]{"s", "d"}, new String[]{" seconds", " days"});
            expirationMessage = "expire after " + duration + "; administrators can also revoke API keys before they expire.";
        }
%>
This server allows API keys. <%=text("They are currently configured to " + expirationMessage)%> (For example, if an API key is accidentally revealed
to others.) API keys are appropriate for authenticating ad hoc interactions within statistical tools (e.g., R, RStudio, SAS) or programming languages
(e.g., Java, Python), as well authenticating API use from automated scripts.
<br/><br/>

<labkey:form layout="inline">
    <%=button("Generate API key").onClick("createApiKey('apikey');")%>
    <labkey:input id="apikey-token" size="40" isReadOnly="true"/>
    <%=button("Copy to clipboard").id("apikey-token-copy").attributes("data-clipboard-target=\"#apikey-token\"") %>
</labkey:form>

<br/>
<div id="apikey-rusage" style="display: none">
    <br/>
    <h3>R usage</h3>
    <div style="padding:10pt; border:solid 1px grey">
        <code>
            library(Rlabkey)<br>
            labkey.setDefaults(apiKey="<span id="apikey-rkey"></span>")<br>
            <% if (null != baseServerURL) { %>
            labkey.setDefaults(baseUrl="<%=h(baseServerURL.getURIString())%>")<br>
            <% } %>
        </code>
    </div>
</div>
<br/><br/>
<%
    }

    if (sessionKeys)
    {
%>
This server<%=text(apiKeys ? " also" : "")%> allows session keys. A session key is tied to your current browser session, which means all API calls
execute in your current context (e.g., your user, your authorizations, your declared terms of use and PHI level, your current impersonation state,
etc.). It also means the key will no longer represent a logged in user when the session expires, e.g., when you sign out via the browser or the
server automatically times out your session. Since they expire quickly, session keys are most appropriate for deployments with regulatory
compliance requirements where interactions require specifying current role &amp; PHI level and accepting terms of use.
<br/><br/>

<labkey:form layout="inline">
    <%=button("Generate session key").onClick("createApiKey('session');")%>
    <labkey:input id="session-token" size="42" isReadOnly="true"/>
    <%=button("Copy to clipboard").id("session-token-copy").attributes("data-clipboard-target=\"#session-token\"") %>
</labkey:form>

<br/>
<div id="session-rusage" style="display: none">
<br/>
<h3>R usage</h3>
<div style="padding:10pt; border:solid 1px grey">
<code>
    library(Rlabkey)<br>
    labkey.setDefaults(apiKey="<span id="session-rkey"></span>")<br>
    <% if (null != baseServerURL) { %>
    labkey.setDefaults(baseUrl="<%=h(baseServerURL.getURIString())%>")<br>
    <% } %>
</code>
</div>
</div>
<br/><br/>
<%
    }
%>
<%= button("Done").href(returnURL) %>
<script type="application/javascript">
    (function($) {
        addCopyToClipboard($, '#apikey-token-copy');
        addCopyToClipboard($, '#session-token-copy');
    })(jQuery);

    function addCopyToClipboard($, id)
    {
        var clip = new Clipboard(id);
        clip.on('success', function(e) {
            $(e.trigger).html('Copied!'); e.clearSelection();
            setTimeout(function() { $(e.trigger).html('Copy to clipboard'); }, 2500);
        });
        clip.on('error', function(e) {
            var controlKey = navigator && navigator.appVersion && navigator.appVersion.indexOf("Mac") != -1 ? "Command" : "Ctrl";
            $(e.trigger).html('Press ' + controlKey + '+C to copy'); }
        );
    }

    function createApiKey(type) {
        LABKEY.Ajax.request({
            url: <%=q(buildURL(SecurityController.CreateApiKeyAction.class))%>,
            method: 'POST',
            params: {type: type},
            success: function (ctx) {
                var apikey = JSON.parse(ctx.response).apikey;
                document.getElementById(type + "-token").value = apikey;
                document.getElementById(type + "-rkey").innerHTML = apikey;
                document.getElementById(type + "-rusage").style.display = 'block';
            },
            failure: function(response, opts) {
                LABKEY.Utils.displayAjaxErrorResponse(response, opts);
            }
        });
    }
</script>
