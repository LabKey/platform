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
<%@ page import="org.labkey.api.settings.TemplateResourceHandler" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
%>
<div class="labkey-page-header">
    <nav>
        <div class="container">
            <div class="navbar-header">
                <a class="navbar-brand hidden-xs brand-logo" href="<%=h(laf.getLogoHref())%>">
                    <img src="<%=h(TemplateResourceHandler.LOGO.getURL(c))%>" alt="<%=h(laf.getShortName())%>" height="30">
                </a>
                <a class="navbar-brand hidden-sm hidden-md hidden-lg brand-logo" href="<%=h(laf.getLogoHref())%>">
                    <img src="<%=h(PageFlowUtil.staticResourceUrl("/_images/lk_logo_white_m.png"))%>" alt="<%=h(laf.getShortName())%>" height="30">
                </a>
                <form class="navbar-right">
                    <div class="row">
                        <div class="labkey-nav-icon">
                            <a href="#"><i class="fa fa-search"></i></a>
                        </div>
                        <div id="search-form" class="form-group hidden-sm hidden-xs labkey-nav-search">
                            <div class="input-group">
                                <input type="text" placeholder="Search LabKey Server">
                            </div>
                        </div>
                        <div class="nav labkey-nav-icon">
                            <div class="dropdown">
                                <a href="#" class="dropdown-toggle" data-toggle="dropdown"><i class="fa fa-user"></i></a>
                                <ul class="dropdown-menu">
                                    <li><a href="#">My Account</a></li>
                                    <li class="divider"></li>
                                    <li><a href="#">Sign Out</a></li>
                                </ul>
                            </div>
                        </div>
                        <div class="nav labkey-nav-icon">
                            <div class="dropdown">
                                <a href="#" class="dropdown-toggle" data-toggle="dropdown"><i class="fa fa-cog"></i></a>
                                <ul class="dropdown-menu">
                                    <li><a href="#">Help <span class="glyphicon glyphicon-cog pull-right"></span></a></li>
                                    <li class="divider"></li>
                                    <li><a href="#">Admin <span class="glyphicon glyphicon-stats pull-right"></span></a></li>
                                </ul>
                            </div>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </nav>
</div>