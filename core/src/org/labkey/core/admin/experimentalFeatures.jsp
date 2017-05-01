<%
/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="java.util.Collections" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }
%>
<% if (PageFlowUtil.useExperimentalCoreUI()) { %>
<p class="labkey-error">
    <strong>WARNING</strong>:
    These experimental features may change, break, or disappear at any time.
    We make absolutely no guarantees about what may happen if you turn on these experimental
    features.  Enabling or disabling some features will require a restart of the server.
</p>
<div class="list-group">
<% } else { %>
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
        text-indent: 0;
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
<p>
    <span class="labkey-error">WARNING</span>:
    These experimental features may change, break, or disappear at any time.
    We make absolutely no guarantees about what may happen if you turn on these experimental
    features.  Enabling or disabling some features will require a restart of the server.
</p>
<% } %>
<%
    for (AdminConsole.ExperimentalFeatureFlag flag : AdminConsole.getExperimentalFeatureFlags())
    {
        if (PageFlowUtil.useExperimentalCoreUI()) {
%>
    <div class="list-group-item">
        <h4 class="list-group-item-heading" style="font-weight: bold"><%=h(flag.getTitle())%></h4>
        <p class="list-group-item-text"><%=h(flag.getDescription())%></p>
        <% if (flag.isRequiresRestart()) { %>
        <div>Restart required after toggling feature.</div>
        <% } %>
        <%= PageFlowUtil.textLink(
                flag.isEnabled() ? "Disable" : "Enable",
                "javascript:void(0);", null, null, Collections.singletonMap("data-exp-flag", h(flag.getFlag()))) %>
    </div>
<%
        } else {
%>
<div class="labkey-experimental-feature labkey-indented">
    <div class="labkey-experimental-title"><%=h(flag.getTitle())%></div>
    <div><%=h(flag.getDescription())%></div>
    <% if (flag.isRequiresRestart()) { %>
    <div class="labkey-experimental-restart">Restart required after toggling feature.</div>
    <% } %>
    <%= PageFlowUtil.textLink(
            flag.isEnabled() ? "Disable" : "Enable",
            "javascript:void(0);", null, null, Collections.singletonMap("data-exp-flag", h(flag.getFlag()))) %>
</div>
<%
        }
    }
%>
</div>
<script type="application/javascript">
    +function($) {
        $(function() {
            $('a[data-exp-flag]').click(function(evt) {
                var el = $(evt.target);
                var flag = el.attr('data-exp-flag');
                if (flag) {
                    LABKEY.Ajax.request({
                        url: LABKEY.ActionURL.buildURL('admin', 'experimentalFeature.api'),
                        method: 'POST',
                        params: { feature: flag, enabled: el.text() == 'Enable' },
                        success: LABKEY.Utils.getCallbackWrapper(function(json) {
                            el.text(json.enabled ? 'Disable' : 'Enable');
                        }),
                        failure: LABKEY.Utils.getCallbackWrapper(null, null, true)
                    });
                }
            });
        });
    }(jQuery);
</script>

