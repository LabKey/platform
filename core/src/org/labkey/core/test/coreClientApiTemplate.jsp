<%--
/*
 * Copyright (c) 2014 LabKey Corporation
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
<%@ page buffer="none" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.GWTView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.WebPartFactory" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.core.test.CoreClientApiTemplate" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %><%

    CoreClientApiTemplate me = (CoreClientApiTemplate) HttpView.currentView();
    PageConfig bean = me.getModelBean();
    ActionURL url = getActionURL();
    Set<String> gwtModules = GWTView.getModulesForRootContext();
    Container c = getContainer();
    ThemeFont themeFont = ThemeFont.getThemeFont(c);
    boolean isPrint = bean.getTemplate() == PageConfig.Template.Print;
    String themeClass = themeFont.getClassName() + (isPrint ? " print": "");

    if (bean.getFrameOption() != PageConfig.FrameOption.ALLOW)
        response.setHeader("X-FRAME-OPTIONS", bean.getFrameOption().name());

    String onLoad = "";
    if (null != bean.getFocus())
    {
        onLoad += "document." + bean.getFocus() + ".focus(); ";
    }
    if (bean.getShowPrintDialog())
    {
        onLoad += "window.print(); ";
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="X-UA-Compatible" content="IE=Edge" /><%
    if (bean.getFrameOption() == PageConfig.FrameOption.DENY)
    { %>
    <script type="text/javascript">if (top != self) top.location.replace(self.location.href);</script><%
    } %>
    <title><%=h(bean.getTitle()) %></title>
    <!-- <%=h(url.getURIString())%> -->
    <%= bean.getMetaTags(url) %>
    <%= PageFlowUtil.getCoreClientApiIncludes(getViewContext(), bean.getClientDependencies()) %>
</head>

<body id="bodyElement" onload="<%=h(onLoad)%>" class="<%= text(themeClass) %>">
<%
    if (null != gwtModules && gwtModules.size() > 0)
    {   //Only include first js file?? %>
<iframe id="__gwt_historyFrame" style="width:0;height:0;border:0"></iframe><%
    }
%>
<table class="labkey-main <%= text(themeClass) %>" cellpadding="0" cellspacing="0" <% if (isPrint) { %>style="padding: 5px;"<% } %>><%

    if (bean.showHeader() != PageConfig.TrueFalse.False)
    {
%>
    <tr id="headerpanel" class="labkey-header-panel">
        <td colspan=3>
            <!-- HEADER -->
            <% me.include(me.getView("header"),out); %>
            <!-- /HEADER -->
        </td>
    </tr>
    <tr>
        <td class="labkey-proj">
            <!--content area-->
            <table class="labkey-proj">
                <%    if (me.getView("nav") instanceof HttpView && ((HttpView)me.getView("nav")).isVisible())
                { %>
                <tr>
                    <td id="navpanel" class="labkey-proj-nav-panel" colspan="2">
                        <%
                            me.include(me.getView("nav"),out);
                        %>
                    </td>
                </tr>
                <%        } %>
                <% } %>
                <tr>
                    <%

                        if (null != me.getView("moduleNav"))
                        {
                    %><td align=left valign=top class=normal width="200px" height="100%" style="padding:5;"><%
                    me.include(me.getView("moduleNav"), out);
                %></td><%
                    }

                %>
                    <td id="bodypanel" class="labkey-body-panel" style="min-width:<%=bean.getMinimumWidth()%>px;">
                        <img height=1 width=<%=bean.getMinimumWidth()%> src="<%=getWebappURL("/_.gif")%>"><br>
                        <!-- BODY -->
                        <% me.include(me.getBody(),out); %>
                        <!-- /BODY -->
                    </td><%

                    if (me.getView("right") instanceof HttpView && ((HttpView)me.getView("right")).isVisible())
                    { %>
                    <!-- RIGHT -->
                    <td class="labkey-side-panel" style="min-width:240px;">
                        <img height=1 width=240 src="<%=getWebappURL("_.gif")%>"><br>
                        <% me.include(me.getView(WebPartFactory.LOCATION_RIGHT),out); %>
                    </td>
                    <!-- /RIGHT -->
                    <%				} %>
                </tr>
                <tr><td><div id="testData"></div></td></tr>
                <%
                if (bean.showHeader() != PageConfig.TrueFalse.False)
                {  %>
            </table>
            <!--/content area-->
        </td>
    </tr><%
        }
    %>
</table><%
    String anchor = bean.getAnchor();
    if (null == StringUtils.trimToNull(anchor))
        anchor = StringUtils.trimToNull(request.getParameter("_anchor"));

    if (null != anchor)
    {
%><script type="text/javascript" for="window" event="onload">window.location.href = "#<%=h(anchor)%>"</script><%
    }
%>
<script type="text/javascript">
    LABKEY.loadScripts();
    LABKEY.showNavTrail();

    window.onload = function(){

        document.getElementById('testData').innerHTML = (this.Ext) ? '<span class="labkey-error">Boo!, Ext is defined on the page</span>' : '<span class="labkey-strong">Hooray!, no Ext on the page</span>';
    };

</script>
</body>
</html>
