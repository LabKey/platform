<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.springframework.web.servlet.ModelAndView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    List<HttpView> _views = (List<HttpView>) this.getModelBean();
    boolean showFolders = getViewContext().isShowFolders();
%>
<div class="menu-wrapper">
    <table class="labkey-expandable-nav-panel">
    <%
        if (showFolders)
        {
            List<HttpView> notNullViews = new ArrayList<>();
            for (ModelAndView possibleView : _views)
            {
                if (possibleView instanceof HttpView && ((HttpView)possibleView).isVisible())
                    notNullViews.add((HttpView)possibleView);
            }
            for (HttpView view : notNullViews)
            {
                %>
                <tr><td colspan="1">
                    <!-- menuview element -->
                    <% include(view, out); %>
                    <!--/ menuview element -->
                </td></tr>
                <%
            }
        }
    %>
    </table>
</div>
