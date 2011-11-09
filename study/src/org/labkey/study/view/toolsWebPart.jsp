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
<%@ page import="org.labkey.study.view.StudyToolsWebPart" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyToolsWebPart.StudyToolsBean> me = (JspView<StudyToolsWebPart.StudyToolsBean>) HttpView.currentView();
    StudyToolsWebPart.StudyToolsBean bean = me.getModelBean();
%>
<table width="100%">
    <tr>
        <%
            for (StudyToolsWebPart.Item item : bean.getItems())
            {
        %>
        <td style="text-align:center;vertical-align:bottom;padding-top:1em">
            <span class="tool-icon">
                <a href="<%= item.getUrl().getLocalURIString()%>">
                    <img src="<%= item.getIcon() %>" alt="[<%= h(item.getLabel() + " Icon") %>]"><br>
                    <%= h(item.getLabel()) %>
                </a>
            </span>
        </td>
            <%
                if (!bean.isWide())
                {
            %>
    </tr>
    <tr>
            <%
                }
            }
        %>
    </tr>
</table>