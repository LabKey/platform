<%
/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.util.DateUtil"%>
<%@ page import="org.labkey.api.util.MemTracker" %>
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
    boolean hasAdminPerm = getUser().hasRootAdminPermission();

    if (!bean.activeThreads.isEmpty()) { %>
        <div class="labkey-error">Active thread(s) may have objects in use:
            <ul> <%
                for (String activeThread : bean.activeThreads) { %>
                    <li><%= h(activeThread) %></li> <%
                } %>
            </ul>
        </div> <%
    }
%>
<% if (hasAdminPerm) { %>
<p>
    <%=textLink("Clear Caches, GC and Refresh", AdminController.getMemTrackerURL(true, true))%>
    <%=textLink("GC and Refresh", AdminController.getMemTrackerURL(false, true))%>
    <%=textLink("Refresh", AdminController.getMemTrackerURL(false, false))%>
</p>
<% } %>
<table class="labkey-wp">
    <tr class="labkey-wp-header">
        <th class="labkey-wp-title-left">Memory Graphs</th>
    </tr>
    <tr>
        <td class="labkey-wp-body">
            <%
                int i = 0;

                for (String graphName : bean.graphNames)
                {
                    // Hacky - assuming that the first one should be a different size
                    if (i == 0)
                    {
                        %><img vspace="2" hspace="2" width="800" height="100" style="border: 1px solid" src="<%=this.buildURL(AdminController.MemoryChartAction.class)%>&type=<%= PageFlowUtil.encode(graphName) %>"/> <%
                    }
                    else
                    {
                        %><img hspace="2" vspace="2" width="398" height="70" style="border: 1px solid" src="<%=this.buildURL(AdminController.MemoryChartAction.class)%>&type=<%= PageFlowUtil.encode(graphName) %>"/> <%
                    }
                    if (i % 2 == 0)
                    {
                        %><br/> <%
                    }
                    i++;
                }
            %>
        </td>
    </tr>
</table>
<p/>
<table class="labkey-wp">
    <tr class="labkey-wp-header">
        <th class="labkey-wp-title-left">Memory Stats</th>
    </tr>
    <tr>
        <td class="labkey-wp-body">
            <table class="labkey-data-region-legacy labkey-show-borders">
                <tr>
                    <th>Pool Name</th>
                    <th>Init</th>
                    <th>Used</th>
                    <th>Committed</th>
                    <th>Max</th>
                </tr>
            <%
                int counter = 1;
                for (Pair<String, AdminController.MemoryUsageSummary> property : bean.memoryUsages)
                {
            %>
                <tr class="<%=getShadeRowClass(counter % 2 == 1)%>">
                    <td><%= h(property.getKey()) %></td>
                    <td align="right"><%= h(property.getValue() == null ? "" : property.getValue().getInit()) %></td>
                    <td align="right"><%= h(property.getValue() == null ? "" : property.getValue().getUsed()) %></td>
                    <td align="right"><%= h(property.getValue() == null ? "" : property.getValue().getCommitted()) %></td>
                    <td align="right"><%= h(property.getValue() == null ? "" : property.getValue().getMax()) %></td>
                </tr>
            <%
                    counter++;
                }
            %>
            </table>
            <p/>
            <table name="systemProperties" class="labkey-data-region-legacy labkey-show-borders">
                <tr>
                    <th>System Property Name</th>
                    <th>System Property Value</th>
                </tr>
            <%
                counter = 1;
                for (Pair<String, Object> property : bean.systemProperties)
                {
            %>
                <tr class="<%=getShadeRowClass(counter % 2 == 1)%>">
                    <td><%= h(property.getKey()) %></td>
                    <td><%= h(property.getValue().toString()) %></td>
                </tr>
            <%
                    counter++;
                }
            %>
            </table>
            </td>
        </tr>
    </table>
<%
    if (bean.assertsEnabled)
    {
%>
<p/>
<table class="labkey-wp">
    <tr class="labkey-wp-header">
        <th class="labkey-wp-title-left">In-Use Objects</th>
    </tr>
    <tr>
        <td class="labkey-wp-body">
            <table class="labkey-data-region-legacy labkey-show-borders" name="leaks" id="leaks">
                <tr>
                    <th>&nbsp;</th>
                    <th align="left">Object Class</th>
                    <th align="left">Object toString()</th>
                    <th align="left">Age</th>
                    <th align="left">Allocation Stack</th>
                </tr>
            <%
                    long currentMillis = System.currentTimeMillis();
                    counter = 1;
                    for (MemTracker.HeldReference reference : bean.references)
                    {
                        String htmlStack = reference.getHtmlStack();
                        String[] split = htmlStack.split("<br>");
                        String secondLine = split.length >= 2 ? split[2] : "";
            %>
                <tr class="<%=getShadeRowClass(counter % 2 == 1)%>">
                    <td valign=top><img id="toggleImg<%=counter%>" src="<%=getWebappURL("_images/plus.gif")%>" alt="expand/collapse" onclick='toggle(<%=counter%>)'></td>
                    <td class='objectClass' valign=top><%=h(reference.getClassName())%></td>
                    <td class='objectToString' valign=top>
            <%
                        if (reference.hasShortSummary())
                        {
            %>
                        <div name='summaryTogglePanel' id='summaryTogglePanel<%= counter %>' style='cursor:pointer'>
                            <%= h(reference.getObjectSummary()) %>
                        </div>
                        <div name='descriptionPanel' id='descriptionPanel<%= counter %>' style="display:none;">
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
                    <td class='age' valign=top><%=h(DateUtil.formatDuration(currentMillis - reference.getAllocationTime()))%></td>
                    <td class='allocationStack'>
                        <div name='stackTogglePanel' id='stackTogglePanel<%= counter %>' style='cursor:pointer'><%= text(secondLine) %></div>
                        <div name='stackContentPanel' id='stackContentPanel<%= counter %>' style="display:none;"><%= text(htmlStack) %></div>
                    </td>
                </tr>
            <%
                        counter++;
                    }
            %>
            </table>
        </td>
    </tr>
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

    img.src = img.src.match(/.*plus.gif/) ? "<%=getWebappURL("_images/minus.gif")%>" : "<%=getWebappURL("_images/plus.gif")%>";
}

</script>
