<%--
/*
 * Copyright (c) 2016 LabKey Corporation
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
--%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.view.template.bootstrap.BootstrapTemplate.NavigationModel" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.WebPartFactory" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }

    private String getSafeName(Portal.WebPart menu)
    {
        return (menu.getName() + menu.getIndex()).replaceAll("\\s+", "");
    }
%>
<%
    NavigationModel model = (NavigationModel) HttpView.currentView().getModelBean();
    ViewContext context = getViewContext();
    List<NavTree> tabs = model.getTabs();
%>
<nav class="labkey-page-nav">
    <div class="container">
        <div class="navbar-header">
            <%--<button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#collapsemenu" aria-expanded="false">--%>
                <%--<i class="fa fa-bars"></i>--%>
            <%--</button>--%>

            <ul class="nav">
                <li class="dropdown visible-xs">
                    <a href="#" class="dropdown-toggle" data-toggle="dropdown">
                        <i class="fa fa-bars"></i>
                    </a>
                    <ul class="dropdown-menu">
                        <li>Custom menu 1</li>
                        <li>Custom menu 2</li>
                        <li>Custom menu 3</li>
                        <li>Custom menu 4</li>
                    </ul>
                </li>
                <li class="dropdown lk-project-nav-ct" data-webpart="projectnav" data-name="projectnav">
                    <a href="#" class="dropdown-toggle" data-toggle="dropdown">
                        <i class="fa fa-folder-open"></i>&nbsp;<%=h(model.getProjectTitle())%>
                    </a>
                    <ul class="dropdown-menu"></ul>
                </li>

<%
    for (Portal.WebPart menu : model.getCustomMenus())
    {
        String caption = menu.getName();

        try
        {
            WebPartFactory factory = Portal.getPortalPart(menu.getName());
            if (null == factory)
                continue;
            WebPartView view = factory.getWebPartView(context, menu);
            if (view.isEmpty())
                continue;
            if (null != view.getTitle())
                caption = view.getTitle();
        }
        catch (Exception e)
        {
            // Use the part name...
        }
%>
                <li class="dropdown hidden-xs" data-webpart="<%=text(getSafeName(menu))%>" data-name="<%=text(menu.getName())%>">
                    <a href="#" class="dropdown-toggle" data-toggle="dropdown"><%=h(caption)%></a>
                    <ul class="dropdown-menu"></ul>
                </li>
                <% } %>
            </ul>
        </div>
        <div class="lk-nav-tabs-ct">
            <ul class="nav lk-nav-tabs hidden-sm hidden-xs pull-right">
                <%
                    for (NavTree tab : tabs)
                    {
                        if (null != tab.getText() && tab.getText().length() > 0)
                        {
                %>
                <li role="presentation" class="<%= text(tab.isSelected() ? "active" : "") %>">
                    <a href="<%=h(tab.getHref())%>"><%=h(tab.getText())%></a>
                </li>
                <%
                        }
                    }
                %>
            </ul>
            <ul class="nav lk-nav-tabs hidden-md hidden-lg pull-right">
                <%
                    for (NavTree tab : tabs)
                    {
                        if (null != tab.getText() && tab.getText().length() > 0)
                        {
                            if (tab.isSelected())
                            {
                %>
                <li role="presentation" class="dropdown active">
                    <a href="#" class="dropdown-toggle" data-toggle="dropdown">
                        <%=h(tab.getText())%>&nbsp;
                        <% if (tabs.size() > 1) { %>
                        <i class="fa fa-chevron-down" style="font-size: 12px;"></i>
                        <% } %>
                    </a>
                </li>
                <%
                            }
                        }
                    }
                %>
            </ul>
        </div>
    </div>
</nav>
<script type="application/javascript">
    (function($) {

        var menus = {};
        <%
            for (Portal.WebPart menu : model.getCustomMenus())
            {
                String safeName = getSafeName(menu);
                %>menus[<%=PageFlowUtil.jsString(safeName)%>] = {};<%
                for (Map.Entry<String,String> entry : menu.getPropertyMap().entrySet())
                {
                    %>menus[<%=PageFlowUtil.jsString(safeName)%>][<%=PageFlowUtil.jsString(entry.getKey())%>] = <%=PageFlowUtil.jsString(entry.getValue())%>;<%
                }
            }
        %>

        $(function() {
            $('[data-webpart]').click(function() {
                var partName = $(this).data('name');
                var safeName = $(this).data('webpart');
                var target = $(this).find('.dropdown-menu');

                if (partName && safeName && target) {
                    var id = target.attr('id');
                    if (!id) {
                        id = LABKEY.Utils.id();
                        target.attr('id', id);
                    }

                    var config = {
                        renderTo: id,
                        partName: partName,
                        frame: 'none'
                    };

                    if (menus[safeName]) {
                        config.partConfig = menus[safeName];
                    }

                    var wp = new LABKEY.WebPart(config);
                    wp.render();
                    $(this).unbind('click');
                }
            })
        });
    })(jQuery);
</script>