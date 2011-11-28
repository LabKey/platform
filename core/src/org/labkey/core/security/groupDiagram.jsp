<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ActionURL groupDiagramURL = new ActionURL(SecurityController.GroupDiagramAction.class, getViewContext().getContainer());
%>
<div id="groupDiagram"></div>
<script type="text/javascript">
    Ext.onReady(function() {
        if (Ext.isIE && (Ext.isIE6 || Ext.isIE7 || Ext.isIE8))
        {
            render("This feature is not supported on older versions of Internet Explorer. " +
                "Please upgrade to the latest version of Internet Explorer, or switch to using Firefox, Chrome, or Safari. " +
                "See <a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=supportedBrowsers' target='browserVersions'>this page</a> for more information.");
        }
        else if (Ext.isGecko2 || Ext.isGecko3)
        {
            render("This feature is not supported on older versions of Firefox; please upgrade to the latest version of Firefox. " +
                "See <a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=supportedBrowsers' target='browserVersions'>this page</a> for more information.");
        }
        else
        {
            refreshDiagram();
            securityCache.principalsStore.on("add", refreshDiagram, this);
            securityCache.principalsStore.on("remove", refreshDiagram, this);
            securityCache.principalsStore.on("update", refreshDiagram, this);
        }
    });

    // TODO: This is getting called twice for each group add/remove... filter? different listener?
    function refreshDiagram(s, record, type)
    {
        Ext.Ajax.request({
            url: <%=q(groupDiagramURL.toString())%>,
            success: renderGroupDiagram,
            failure: onError
        });
    }

    function renderGroupDiagram(response)
    {
        var bean = Ext.util.JSON.decode(response.responseText);

        render(bean.html);
    }

    function onError(response)
    {
        if (response.responseText)
        {
            var bean = Ext.util.JSON.decode(response.responseText);

            if (bean.exception)
            {
                render("Error: " + bean.exception);
                return;
            }
        }

        render("Error generating or retrieving diagram");
    }

    function render(html)
    {
        Ext.fly("groupDiagram").update(html);
    }
</script>