<%
/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.AdminConsole" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>
<%
    Collection<AdminConsole.ExperimentalFeatureFlag> flags = AdminConsole.getExperimentalFeatureFlags();
%>
<%!
    String textLink(AdminConsole.ExperimentalFeatureFlag flag)
    {
        return PageFlowUtil.textLink(flag.isEnabled() ? "Disable" : "Enable",
                "javascript:void(0);",
                "toggleFeature(this, " + hq(flag.getFlag()) + ");return false;",
                "labkey-experimental-feature-" + h(flag.getFlag()));
    }
%>
<style type="text/css">
    .labkey-experimental-feature {
        border: 1px solid #d3d3d3;
        border-radius: 2px;
        background: #ffffcc;
        margin: 1em;
        padding: 0.5em 0.5em 0.5em 0.75em;
        text-indent: 1.5em;
    }

    .labkey-experimental-title {
        font-weight: bold;
        text-indent: 0em;
    }

    .labkey-experimental-description {
    }

    .labkey-experimental-restart:before {
        content: "(";
    }

    .labkey-experimental-restart:after {
        content: ")";
    }

    .labkey-experimental-restart {
        font-style: italic;
        font-size: smaller;
    }
</style>
<script>
    function toggleFeature(el, flag)
    {
        // toggle the enabled state of the feature
        var enabled = el.firstChild.textContent == "Enable";

        Ext4.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL("admin", "experimentalFeature.api"),
            params: { feature: flag, enabled: enabled },
            success: LABKEY.Utils.getCallbackWrapper(function (json) {
                el.firstChild.textContent = json.enabled ? "Disable" : "Enable";
            }),
            failure: LABKEY.Utils.getCallbackWrapper(null, null, true)
        });
    }
</script>
<p>
    <span class='labkey-error'>WARNING</span>:
    These experimental features may change, break, or disappear at any time.
    We make absolutely no guarantees about what may happen if you turn on these experimental
    features.  Enabling or disabling some features will require a restart of the server.
</p>
<%
    for (AdminConsole.ExperimentalFeatureFlag flag : flags)
    {
        %>
<div class="labkey-experimental-feature labkey-indented">
    <div class="labkey-experimental-title"><%=h(flag.getTitle())%></div>
    <div class="labkey-experimental-description"><%=h(flag.getDescription())%></div>
    <% if (flag.isRequiresRestart()) { %>
    <div class="labkey-experimental-restart">Restart required after toggling feature.</div>
    <% } %>
    <%= textLink(flag) %>
</div>
        <%
    }
%>

