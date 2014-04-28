<%
/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AdminController.MaintenanceBean bean = ((JspView<AdminController.MaintenanceBean>)HttpView.currentView()).getModelBean();

%>
<labkey:errors/>

<%=text(bean.content)%>

<% if (bean.loginURL != null) { %>
<p><%= button("Site Admin Login").href(bean.loginURL) %></p>
<% } %>

<script>
(function () {
    // initial delay of 0.5 second
    var delay = 500;

    // grab the returnURL, if present
    var returnURL = LABKEY.ActionURL.getParameter("returnUrl");

    // loginURL is set if the current user is guest
    <% if (bean.loginURL == null) { %>
    var loginURL = null;
    <% } else { %>
    var loginURL = <%=q(bean.loginURL.toString())%>;
    <% } %>

    // if we have a returnURL or loginURL, check for startup complete and redirect.
    var nextURL = returnURL || loginURL;
    if (nextURL)
    {
        function checkStartupComplete()
        {
            Ext4.Ajax.request({
                url: <%=q(new ActionURL(AdminController.StartupStatusAction.class, getContainer()).toString())%>,
                success: function (response) {
                    var json = Ext4.decode(response.responseText);
                    if (json && json.startupComplete && !json.adminOnly) {
                        window.location = nextURL;
                    }
                    else {
                        if (json.adminOnly)
                            delay += 1000;
                        setTimeout(checkStartupComplete, delay);
                    }
                },
                failure: function (response) {
                    delay *= 2;
                }
            });
        }

        setTimeout(checkStartupComplete, delay);
    }

})();
</script>
