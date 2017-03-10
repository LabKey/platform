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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.AppBar" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AppBar bean = (AppBar) HttpView.currentView().getModelBean();
    if (null == bean)
        return;

    Container c = getContainer();
    Container p = c.getProject();
    String projectTitle = "";
    if (null != p)
    {
        projectTitle = p.getTitle();
        if (null != projectTitle && projectTitle.equalsIgnoreCase("home"))
            projectTitle = "Home";
    }

    NavTree[] tabs = bean.getButtons();
%>
<nav class="labkey-page-nav">
    <div class="container">
        <div style="width: 60%; display: inline-block;">
            <div class="navbar-header" style="float: left; margin: 0;">
                <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#collapsemenu" aria-expanded="false">
                    <i class="fa fa-bars"></i>
                </button>

                <ul class="nav" style="display: inline-block">
                    <li id="selected-folder" class="selected-folder">
                        <a href="#" onclick="return false;">
                            <i class="fa fa-folder-open"></i>&nbsp;<%=h(projectTitle)%>
                        </a>
                    </li>
                </ul>
            </div>
            <div id="collapsemenu" class="collapse navbar-collapse">
                <ul class="nav navbar-nav">
                    <li><a href="">EHR</a></li>
                    <li class="hidden-sm"><a href="">Colonies</a></li>
                    <li class="hidden-sm"><a href="">Animals</a></li>
                    <li class="hidden-sm"><a href="">Gene Panels</a></li>

                    <li class="dropdown hidden-xs hidden-lg">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown">More <b class="caret"></b></a>
                        <ul class="dropdown-menu">
                            <li class="hidden-lg hidden-md"><a href="">Colonies</a></li>
                            <li class="hidden-lg hidden-md"><a href="">Animals</a></li>
                            <li class="hidden-lg hidden-md"><a href="">Gene Panels</a></li>
                        </ul>
                    </li>
                </ul>
            </div>
        </div>

        <div class="navbar-right">
            <ul class="nav nav-pills hidden-sm hidden-xs pull-right" role="tablist">
                <%
                    for (NavTree tab : tabs)
                    {
                        if (null != tab.getText() && tab.getText().length() > 0)
                        {
                %>
                <li role="presentation">
                    <a role="tab" data-toggle="tab" href="<%=h(tab.getHref())%>" style="border-radius: 1px; padding-top: 5px;"><%=h(tab.getText())%></a>
                </li>
                <%
                        }
                    }
                %>
            </ul>
            <div class="nav nav-pills hidden-md hidden-lg" style="float: right; margin-top: 3px;">
                <div class="nav-item dropdown">
                    <a class="nav-link dropdown-toggle" data-toggle="dropdown" href="#" role="button" aria-haspopup="true" aria-expanded="false">
                        Tabs
                        <span class="caret"></span>
                    </a>
                    <div class="dropdown-menu" style="background-color: #3495D2; right: 0; left: auto; margin-top: 7px;">
                        <ul class="nav nav-pills nav-stacked">
                            <li class="hidden-lg hidden-md"><a href="">Tab 2</a></li>
                            <li class="hidden-lg hidden-md"><a href="">Tab 3</a></li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    </div>
</nav>