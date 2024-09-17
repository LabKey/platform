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
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.settings.AdminConsole" %>
<%@ page import="org.labkey.api.settings.AdminConsole.OptionalFeatureFlag" %>
<%@ page import="org.labkey.api.settings.OptionalFeatureService.FeatureType" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.core.admin.AdminController.OptionalFeaturesForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    OptionalFeaturesForm form = (OptionalFeaturesForm)getModelBean();
    FeatureType type = form.getTypeEnum();
    boolean showHidden = form.isShowHidden();
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
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
<%=type.getAdminGuidance()%>
</p>
<%=getTroubleshooterWarning(hasAdminOpsPerms, HtmlString.EMPTY_STRING, HtmlString.unsafe("<br>"))%>
<div class="list-group">
<%
    for (OptionalFeatureFlag flag : AdminConsole.getOptionalFeatureFlags(type))
    {
        if (!showHidden && flag.isHidden())
            continue;
%>
<div class="list-group-item">
    <label>
        <input id="<%=h(flag.getFlag())%>" type="checkbox"<%=checked(flag.isEnabled())%><%=disabled(!hasAdminOpsPerms)%>>
        <span class="toggle-label-text"><%=h(flag.getTitle())%></span>
    </label>
    <div class="list-group-item-text"><%=h(flag.getDescription())%></div>
<%
        if (flag.isRequiresRestart())
        {
%>
    <br/>
    <div class="list-group-item-text labkey-error">A restart is required after toggling this feature.</div>
<%
        }
%>
</div>
<%
    }
%>
</div>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    (function () {
        let inputList = document.querySelectorAll('div.list-group-item input');
        inputList.forEach(function (input) {
            input.addEventListener('change', function () {
                let flag = input.id;
                let enabled = input.checked;
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('admin', 'optionalFeature.api'),
                    method: 'POST',
                    params: { feature: flag, enabled: enabled },
                    success: LABKEY.Utils.getCallbackWrapper(function(json) {
                        console.log((json.enabled ? 'Enabled' : 'Disabled') + <%=q(" " + type.name().toLowerCase() + " feature: ")%> + flag);
                    }),
                    failure: LABKEY.Utils.getCallbackWrapper(null, null, true)
                });
            });
        });
    })();
</script>
