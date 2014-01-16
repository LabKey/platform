<%
/*
 * Copyright (c) 2005-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.util.MemTracker"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.AdminController.MemBean" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<MemBean> me = (JspView<MemBean>)HttpView.currentView();
    MemBean bean = me.getModelBean();

    for (String active : bean.activeThreads)
        out.print("<div class=\"labkey-error\">Warning: active thread \"" + active + "\" may have objects in use</div><br>\n");

    if (!bean.activeThreads.isEmpty())
        out.print("<br>\n");
%>
<%=textLink("Clear Caches, GC and Refresh", AdminController.getMemTrackerURL(true, true))%>
<%=textLink("GC and Refresh", AdminController.getMemTrackerURL(false, true))%>
<%=textLink("Refresh", AdminController.getMemTrackerURL(false, false))%>
<br><hr size="1">
<%
    int i = 0;

    for (String graphName : bean.graphNames)
    {
        // Hacky - assuming that the first one should be a different size
        if (i == 0)
        {
            %><img vspace="2" hspace="2" width="800" height="100" src="memoryChart.view?type=<%= PageFlowUtil.encode(graphName) %>"> <%
        }
        else
        {
            %><img hspace="2" vspace="2" width="398" height="70" src="memoryChart.view?type=<%= PageFlowUtil.encode(graphName) %>"> <%
        }
        if (i % 2 == 0)
        {
            %><br/> <%
        }
        i++;
    }
%>
<hr size="1">
<table name="systemProperties">
<%
    for (Pair<String, Object> property : bean.systemProperties)
    {
%>
    <tr>
        <td><b><%= h(property.getKey()) %></b></td>
        <td><%= h(property.getValue().toString()) %></td>
    </tr>
<%
    }
%>
</table><p>
<%
    if (bean.assertsEnabled)
    {
%>
<hr size="1">
<h3>In-Use Objects</h3>
<p><table name="leaks" class="spaced normal">
    <tr>
        <th>&nbsp;</th>
        <th align="left">Object Class</th>
        <th align="left">Object toString()</th>
        <th align="left">Allocation Stack</th>
    </tr>
<%
        int counter = 1;
        for (MemTracker.HeldReference reference : bean.references)
        {
            String htmlStack = reference.getHtmlStack();
            String[] split = htmlStack.split("<br>");
            String secondLine = split.length >= 2 ? split[2] : "";
%>
    <tr class="<%=getShadeRowClass(counter % 2 == 1)%>">
        <td valign=top><img id="toggleImg<%=counter%>" src="<%=getWebappURL("_images/plus.gif")%>" alt="expand/collapse" onclick='toggle(<%=counter%>)'></td>
        <td valign=top><%=h(reference.getClassName())%></td>
        <td valign=top>
<%
            if (reference.hasShortSummary())
            {
%>
            <div id='summaryTogglePanel<%= counter %>' style='cursor:pointer'>
                <%= h(reference.getObjectSummary()) %>
            </div>
            <div id="descriptionPanel<%= counter %>" style="display:none;">
                <%= h(reference.getObjectDescription()) %>
            </div>
<%
            }
            else
            {
                %><%= h(reference.getObjectDescription()) %><%
            }
%>
        </td>
        <td>
            <div id='stackTogglePanel<%= counter %>' style='cursor:pointer'><%= secondLine %></div>
            <div id="stackContentPanel<%= counter %>" style="display:none;"><%= htmlStack %></div>
        </td>
    </tr>
<%
            counter++;
        }
%>
</table>
<%
    }
%>
<script type="text/javascript">

function toggle(i)
{
    image = document.getElementById("toggleImg" + i);
    descriptionPanel = document.getElementById("descriptionPanel" + i);
    summaryTogglePanel = document.getElementById("summaryTogglePanel" + i);
    stackTogglePanel = document.getElementById("stackTogglePanel" + i);
    stackContentPane = document.getElementById("stackContentPanel" + i);

    toggleImg(image);
    toggleDiv(descriptionPanel);
    toggleDiv(summaryTogglePanel);
    toggleDiv(stackTogglePanel);
    toggleDiv(stackContentPane);
}

function toggleDiv(div)
{
    if (!div)
        return;
    display = div.style.display;
    div.style.display = display == "none" ? "block" : "none";
}

function toggleImg(img)
{
    if (!img)
        return;

    img.src = img.src.match(/.*plus.gif/) ? "<%=getContextPath()%>/_images/minus.gif" : "<%=getContextPath()%>/_images/plus.gif";
}

</script>
