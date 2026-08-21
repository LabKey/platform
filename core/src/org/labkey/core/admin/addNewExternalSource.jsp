<%
/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.EnumUtils" %>
<%@ page import="org.labkey.api.collections.LabKeyCollectors" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.Directive" %>
<%@ page import="org.labkey.api.security.permissions.ApplicationAdminPermission" %>
<%@ page import="org.labkey.api.settings.OptionalFeatureService" %>
<%@ page import="org.labkey.core.admin.AdminController.ExternalSourcesForm" %>
<%@ page import="org.labkey.filters.ContentSecurityPolicyFilter" %>
<%@ page import="org.labkey.filters.ContentSecurityPolicyFilter.ContentSecurityPolicyType" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.List" %>
<%@ page import="static org.labkey.filters.ContentSecurityPolicyFilter.FEATURE_FLAG_DISABLE_ENFORCE_CSP" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Container c = getContainer();
    boolean isTroubleshooter = !c.hasPermission(getUser(), ApplicationAdminPermission.class);

    String noEffect = "External resource hosts can be configured below, but they'll have no effect until ";
    String message;

    boolean hasEnforce = ContentSecurityPolicyFilter.hasCsp(ContentSecurityPolicyType.Enforce);
    if (hasEnforce)
    {
        message = "This server is configured with an enforce Content Security Policy (CSP) ";
        boolean disabled = OptionalFeatureService.get().isFeatureEnabled(FEATURE_FLAG_DISABLE_ENFORCE_CSP);

        if (disabled)
        {
            message += "but it's currently disabled via an experimental feature flag! " + noEffect + "the enforce CSP is re-enabled.";
        }
        else
        {
            List<String> missing = ContentSecurityPolicyFilter.getMissingSubstitutions(ContentSecurityPolicyType.Enforce);
            int count = missing.size();
            message += missing.isEmpty() ?
                "that includes all the expected substitutions." :
                "but the following substitution" + (count > 1 ? "s are" : " is") + " missing: " + String.join(", ", missing) +
                    ". External resource hosts for " + (count > 1 ? "these directives" : "this directive") + " can be configured below, but they'll have no effect.";
        }
    }
    else
    {
        message = "This server is not configured with an enforce Content Security Policy (CSP); LabKey strongly recommends " +
            "configuring a strict enforce CSP. " + noEffect + "an enforce CSP is configured.";
    }
%>
<labkey:errors/>
<div style="width: 800px;">
    <p>
        <%=h(message)%>
    </p>
    <p>
        The standard LabKey CSP restricts hosts that browsers can use as resource origins. By default, only sources from
        this server are allowed; other server hosts must be configured below to enable them to be used as external
        sources. All provided hosts are added into the CSP using the \${} substitution key shown next to each directive.
<%
    if (!isTroubleshooter)
    {
%>
        To allow a resource, pick a directive and add the associated hostname or IP address, for example: www.myexternalhost.com or 1.2.3.4.
<%
    }
%>
    </p>
    <p>
        For information about configuring CSPs with LabKey, refer to our <%=helpLink("cspConfig", "Content Security Policy Configuration page")%>.
    </p>
    <p>
        For more information on the security concern, refer to the
        <%=simpleLink("OWASP cheat sheet", "https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html#cross-origin-resource-sharing").target("_owasp")%>.
    </p>
</div>
<%
    if (!isTroubleshooter)
    {
        ExternalSourcesForm form = (ExternalSourcesForm)getModelBean();
        Directive directive = EnumUtils.getEnum(Directive.class, form.getNewDirective());
%>
<labkey:form name="addNewHost" id="form-addNewHost" method="post">
    <table>
        <tr>
            <td><label class="labkey-form-label">Directive</label></td>
            <td><%=select()
                .name("newDirective")
                .id("newDirective")
                .addStyle("width:300px")
                .selected(directive)
                .addOptions(
                    Arrays.stream(Directive.values())
                        .collect(LabKeyCollectors.toLinkedMap(Enum::name, d->d.getCspDirective() + " ${" + d.getSubstitutionKey() + "}")
                )
            )%></td>
        </tr>
        <tr>
            <td><label for="newHostTextField" class="labkey-form-label">Host</label></td>
            <td><input name="newHost" id="newHostTextField" size="75" /></td>
        </tr>
        <tr>
            <td><br/><input type="hidden" id="saveNew" name="saveNew" value="true"><%=button("Add").submit(true).onClick("_form.setClean()")%></td>
        </tr>
    </table>
</labkey:form>
<%
    }
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    let _form;

    LABKEY.Utils.onReady(function() {
        _form = new LABKEY.Form({formElement: 'form-addNewHost'});
    });
</script>
