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
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidationProvider" %>
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidationService" %>
<%@ page import="org.labkey.api.util.DOM" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    private void renderProviderList(String title, Map<String, Set<SiteValidationProvider>> providerMap, JspWriter out) throws IOException
    {
        if (!providerMap.isEmpty())
        {
            renderTitle(title, out);

            List<SiteValidationProvider> providers = providerMap.values().stream()
                .flatMap(Collection::stream)
                .toList();

            for (SiteValidationProvider provider : providers)
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
<labkey:form action="<%=urlFor(AdminController.SiteValidationAction.class)%>" method="post">
    <%
        if (getContainer().isRoot())
            renderProviderList("Site Validation Providers", validationService.getSiteProviders(), out);
        renderProviderList("Folder Validation Providers", validationService.getContainerProviders(), out);
        renderTitle("Folder Validation Options", out);
        if (getContainer().isRoot())
        {
            out.println(
                DOM.createHtmlFragment(
                    input().name("includeSubfolders").type("radio").value("true").checked(true),
                    "All projects and folders in this site",
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
                    input().type("checkbox").checked(true).name("includeSubfolders"),
                    "Include subfolders",
                    DOM.BR(),
                    DOM.BR()
                )
            );
        }
    %>
    <%=button("Validate").usePost().submit(true)%>
    <%=generateBackButton("Cancel")%>
</labkey:form>
<%
    }
%>