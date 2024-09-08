<%
/*
 * Copyright (c) 2024 LabKey Corporation
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
<%@ page import="jakarta.servlet.jsp.JspWriter" %>
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidationProviderFactory" %>
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidationService" %>
<%@ page import="org.labkey.api.util.DOM" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.core.admin.AdminController.SiteValidationAction" %>
<%@ page import="org.labkey.core.admin.AdminController.SiteValidationBackgroundAction" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.lang.String" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    private void renderProviderList(String title, Map<String, Set<SiteValidationProviderFactory>> factoryMap, JspWriter out) throws IOException
    {
        if (!factoryMap.isEmpty())
        {
            renderTitle(title, out);

            List<SiteValidationProviderFactory> factories = factoryMap.values().stream()
                .flatMap(Collection::stream)
                .toList();

            for (SiteValidationProviderFactory provider : factories)
            {
                out.println(
                    DOM.createHtmlFragment(
                        input().type("checkbox").name("providers").value(provider.getName()).checked(true),
                        provider.getName() + ": " + provider.getDescription(),
                        DOM.BR()
                    )
                );
            }
            out.println(HtmlString.BR);
        }
    }

    private void renderTitle(String title, JspWriter out) throws IOException
    {
        out.println(DOM.createHtmlFragment(DOM.B(title), DOM.BR()));
    }
%>
<%
    SiteValidationService validationService = SiteValidationService.get();
    if (null == validationService)
    {
%><span>SiteValidationService has not been registered.</span><%
    }
    else
    {
%>
Clicking the "Validate" button will run the selected validators in the designated folder(s). Producing the results could take some time, especially with many folders, providers, and/or objects that need to be validated.<br><br>
<labkey:form id="form" action="<%=urlFor(SiteValidationAction.class)%>" method="get">
    <%
        if (getContainer().isRoot())
            renderProviderList("Site Validation Providers", validationService.getSiteFactories(), out);
        renderProviderList("Folder Validation Providers", validationService.getContainerFactories(), out);
        renderTitle("Folder Validation Options", out);
        if (getContainer().isRoot())
        {
            out.println(
                DOM.createHtmlFragment(
                    input().name("includeSubfolders").type("radio").value("true").checked(true),
                    "The root plus all projects and folders in this site",
                    DOM.BR(),
                    input().name("includeSubfolders").type("radio").value("false").checked(false),
                    "Just the projects",
                    DOM.BR(),
                    DOM.BR()
                )
            );
        }
        else
        {
            out.println(
                DOM.createHtmlFragment(
                    input().type("checkbox").checked(true).value("true").name("includeSubfolders"),
                    "Include subfolders",
                    DOM.BR()
                )
            );
        }
        out.println(
            DOM.createHtmlFragment(
                input().id("background").type("checkbox").checked(false).value("true").name("background").onChange("change()"),
                "Run in the background",
                helpPopup("Validating many folders can take a long time. Running in a background pipeline job avoids proxy timeouts. Once the job completes, click the \"Data\" button to see the report."),
                DOM.BR(),
                DOM.BR()
            )
        );
    %>
    <%=button("Validate").submit(true)%>
    <%=generateBackButton("Cancel")%>
    <labkey:csrf/>
</labkey:form>
<%
    }
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    // We want to GET SiteValidationAction for foreground render (keep parameters on the URL for link sharing)
    // or POST to SiteValidationBackgroundAction for background render (handle CSRF and mutating SQL)
    function change()
    {
        const background = document.getElementById("background").checked;
        const form = document.getElementById("form");
        form.action = background ? <%=q(urlFor(SiteValidationBackgroundAction.class))%> : <%=q(urlFor(SiteValidationAction.class))%>;
        form.method = background ? "post" : "get";
    }
</script>