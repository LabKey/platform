<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.AdminController.MemBean" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.Tuple3" %>
<%@ page import="java.text.DecimalFormat" %>
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
    <%=link("Clear Caches, GC and Refresh", AdminController.getMemTrackerURL(true, true))%>
    <%=link("GC and Refresh", AdminController.getMemTrackerURL(false, true))%>
    <%=link("Refresh", AdminController.getMemTrackerURL(false, false))%>
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
                        %><img alt="<%= h(graphName) %>" style="border: 1px solid black; margin: 2px;" width="804" height="100" src="<%=h(urlFor(AdminController.MemoryChartAction.class).addParameter("type", graphName))%>"/> <%
                    }
                    else
                    {
                        %><img alt="<%= h(graphName) %>" style="border: 1px solid black; margin: 2px;" width="398" height="70" src="<%=h(urlFor(AdminController.MemoryChartAction.class).addParameter("type", graphName))%>"/> <%
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
                    <th style="text-align: right">Init</th>
                    <th style="text-align: right">Used</th>
                    <th style="text-align: right">Committed</th>
                    <th style="text-align: right">Max</th>
                </tr>
            <%
                long usedTally = 0;
                long committedTally = 0;
                DecimalFormat format = new DecimalFormat("#,###");
                for (Tuple3<Boolean, String, AdminController.MemoryUsageSummary> t : bean.memoryUsages)
                {
                    if (t.first && t.third != null)
                    {
                        usedTally += Math.max(0, t.third._used);
                        committedTally += Math.max(0, t.third._committed);
                    }
            %>
                <tr class="<%=getShadeRowClass(t.getKey())%>">
                    <td><%= unsafe(t.getKey() ? "" : "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;") %><%= h(t.getValue()) %></td>
                    <td style="text-align: right"><code><%= h(t.third == null || t.third._init < 0 ? "" : format.format(t.third._init)) %></code></td>
                    <td style="text-align: right"><code><%= h(t.third == null || t.third._used < 0 ? "" : format.format(t.third._used)) %></code></td>
                    <td style="text-align: right"><code><%= h(t.third == null || t.third._committed < 0 ? "" : format.format(t.third._committed)) %></code></td>
                    <td style="text-align: right"><code><%= h(t.third == null || t.third._max < 0 ? "" : format.format(t.third._max)) %></code></td>
                </tr>
            <%
                }
            %>
                <tr>
                    <td>&nbsp;</td>
                    <td></td>
                    <td></td>
                    <td></td>
                    <td></td>
                </tr>
                <tr class="<%=getShadeRowClass(true)%>">
                    <td><strong>Total</strong></td>
                    <td></td>
                    <td style="text-align: right"><code><%= h(format.format(usedTally)) %></code></td>
                    <td style="text-align: right"><code><%= h(format.format(committedTally)) %></code></td>
                    <td></td>
                </tr>
            </table>
            <p/>
            <table name="systemProperties" class="labkey-data-region-legacy labkey-show-borders">
                <tr>
                    <th>System Property Name</th>
                    <th>System Property Value</th>
                </tr>
            <%
                int counter = 0;
                for (Pair<String, Object> property : bean.systemProperties)
                {
            %>
                <tr class="<%=getShadeRowClass(counter)%>">
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
                    <th>Object Class</th>
                    <th>Object toString()</th>
                    <th>Age</th>
                    <th>Allocation Stack</th>
                </tr>
            <%
                    long currentMillis = System.currentTimeMillis();
                    counter = 1;
                    for (MemTracker.HeldReference reference : bean.references)
                    {
                        HtmlString htmlStack = reference.getHtmlStack();
                        String[] split = htmlStack.toString().split("<br>");
                        HtmlString secondLine = split.length >= 2 ? HtmlString.unsafe(split[2]) : HtmlString.EMPTY_STRING;
            %>
                <tr class="<%=getShadeRowClass(counter + 1)%>">
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
                        <div name='stackTogglePanel' id='stackTogglePanel<%= counter %>' style='cursor:pointer'><%= secondLine %></div>
                        <div name='stackContentPanel' id='stackContentPanel<%= counter %>' style="display:none;"><%= htmlStack %></div>
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
