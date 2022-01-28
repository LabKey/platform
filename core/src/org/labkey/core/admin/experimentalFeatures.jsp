<%
/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }
%>
<style>
    .toggle-label-text {
        font-size: 1.1em;
        font-weight: bold;
        padding-top: 0.1em;
        padding-bottom: 0.1em;
        display: inline-block;
    }

    .list-group-item-text {
        margin-left: 1.5em;
    }
</style>

<p class="labkey-error">
    <strong>WARNING</strong>:
    These experimental features may change, break, or disappear at any time.
    We make absolutely no guarantees about what may happen if you turn on these experimental
    features. Enabling or disabling some features will require a restart of the server.
</p>
<div class="list-group">
<% for (AdminConsole.ExperimentalFeatureFlag flag : AdminConsole.getExperimentalFeatureFlags()) { %>
<div class="list-group-item">
    <label>
        <input id="<%=h(flag.getFlag())%>" type="checkbox" <%=checked(flag.isEnabled())%>>
        <span class="toggle-label-text"><%=h(flag.getTitle())%></span>
    </label>
    <div class="list-group-item-text"><%=h(flag.getDescription())%></div>
    <% if (flag.isRequiresRestart()) { %>
    <div>Restart required after toggling feature.</div>
    <% } %>
</div>
<% } %>
</div>
<script type="application/javascript">
    (function () {
        let inputList = document.querySelectorAll('div.list-group-item input');
        inputList.forEach(function (input) {
            input.addEventListener('change', function (e) {
                let flag = input.id;
                let enabled = input.checked;
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('admin', 'experimentalFeature.api'),
                    method: 'POST',
                    params: { feature: flag, enabled: enabled },
                    success: LABKEY.Utils.getCallbackWrapper(function(json) {
                        console.log((json.enabled ? 'Enabled' : 'Disabled') + ' experimental feature');
                    }),
                    failure: LABKEY.Utils.getCallbackWrapper(null, null, true)
                });
            });
        });
    })();
</script>

