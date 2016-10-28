<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.security.permissions.AdminPermission"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.SpecimenManager" %>
<%@ page import="org.labkey.study.security.permissions.RequestSpecimensPermission" %>
<%@ page import="org.labkey.study.specimen.SpecimenWebPart" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext3");
        dependencies.add("study/redesignUtils.js");
    }
%>
<%
    SpecimenWebPart.SpecimenWebPartBean bean = (SpecimenWebPart.SpecimenWebPartBean) HttpView.currentView().getModelBean();

    Container c = getContainer();
    User user = getUser();
    String time = Long.toString(System.currentTimeMillis());
    boolean isAdmin = c.hasPermission(user, AdminPermission.class);

    String contentSpanName = "specimen-browse-webpart-content" + time;
    String groupHeading1 = "groupHeading1-" + time;
    String groupHeading2 = "groupHeading2-" + time;
    String group1 = "group1-" + time;
    String group2 = "group2-" + time;
    String groupControl1 = "groupControl1-" + time;
    String groupControl2 = "groupControl2-" + time;
%>
<script type="text/javascript">

    function populateGroupingContent(grouping, hidden)
    {
        var values = grouping.values;
        var innerHTML = '<table width="100%" class="labkey-study-expandable-nav">';
        for (var i = 0; i < values.length; i++)
        {
            var details = values[i];
            innerHTML += '<tr class="labkey-header">';
            if (details.group)
                innerHTML += '<td class="labkey-nav-tree-node"><a onclick="return LABKEY.Utils.toggleLink(this, false);" href="#"><img src="<%=getWebappURL("_images/plus.gif")%>"></a></td>';
            else
                innerHTML += '<td class="labkey-nav-tree-node"><img width="9" src="<%=getWebappURL("_.gif")%>"></td>';

            var nextByGroup = '';
            innerHTML += '<td class="labkey-nav-tree-text" width="100%"><a href=\"' + details.url + '\">' +
                    details.label + '</a><span style="font-size: x-small;"> ' + nextByGroup + '</span></td><td align="right" class="labkey-nav-tree-total">' + details.count + '</td></tr>';
            if (details.group)
                innerHTML += '<tr style="display:none;"><td></td><td colspan="2">' + populateGroupingContent(details.group, true) + '</td></tr>';
        }
        innerHTML += '</table>';
        return innerHTML;
    }

    function populateGrouping(grouping, elementId, names)
    {
        var groupingName = grouping.name;
        if (elementId == names.group1)
            document.getElementById(names.heading1).innerHTML = 'Vials by ' + groupingName;
        else
            document.getElementById(names.heading2).innerHTML = 'Vials by ' + groupingName;

        var innerHTML = populateGroupingContent(grouping, false);
        document.getElementById(elementId).innerHTML = innerHTML;
    }

    function handleGroupings(resp, names)
    {
        if (resp.groupings.length == 0 || !resp.groupings[0] || (resp.groupings[0].values && resp.groupings[0].values.length == 0))
        {
            var html = '<i>No specimens found.</i>';
            <% if (isAdmin && !c.isDataspace()) {%>
                var importUrl = LABKEY.ActionURL.buildURL('study-samples', 'showUploadSpecimens', LABKEY.ActionURL.getContainer());
                html += '<p><a href="' + importUrl + '">Import Specimens</a></p>';
            <% } %>
            document.getElementById(names.content).innerHTML = html;
        }
        else if (null == resp.groupings[0].dummy || !resp.groupings[0].dummy)
        {
            populateGrouping(resp.groupings[0], names.group1, names);
            if (resp.groupings.length > 1)
            {
                populateGrouping(resp.groupings[1], names.group2, names);
            }
            else
            {
                document.getElementById(names.control2).innerHTML = '';
            }
        }
        else
        {
            document.getElementById(names.control1).innerHTML = '';
            document.getElementById(names.control2).innerHTML = '';
        }
        document.getElementById(names.content).setAttribute('style', 'display: inline');
    }

    Ext.onReady(function() {
            LABKEY.Specimen.getSpecimenWebPartGroups({
                success: function (resp) {handleGroupings(resp,
                        {content:  '<%=text(contentSpanName)%>',
                         heading1: '<%=text(groupHeading1)%>',
                         heading2: '<%=text(groupHeading2)%>',
                         group1:   '<%=text(group1)%>',
                         group2:   '<%=text(group2)%>',
                         control1: '<%=text(groupControl1)%>',
                         control2: '<%=text(groupControl2)%>'
                        })}
        });
    });
</script>
<span id="<%=text(contentSpanName)%>" style="display: none">
<table class="labkey-manage-display" style="width: 100%;">
    <tbody>
    <tr><!-- removed lines beneath headings --> <!-- using labkey nav tree markup, which probably doesn't display in wikis --> <!-- hardcoding plus minus images for looks only --> <!-- removed search links, as that's now handled by a new webpart --> <!-- left column -->
        <td valign="top" width="45%">
            <table class="labkey-nav-tree" style="width: 100%;">
                <tbody>
                <tr class="labkey-nav-tree-row labkey-header">
                    <td class="labkey-nav-tree-text" align="left">
                        <a  style="color:#000000;width:auto" onclick="return LABKEY.Utils.toggleLink(this, false);" href="#"><img src="<%=getWebappURL("_images/minus.gif")%>" alt="" />
                            <span>View All Specimens</span></a><a style="width:auto"
                               onmouseover="return showHelpDivDelay(this, 'Vial Viewing Options', 'Vials may be viewed individually, with one row per vial, or by vial group, with one row per subject, time point, and sample type combination.');"
                               onmouseout="return hideHelpDivDelay();"
                               onclick="return showHelpDiv(this, 'Vial Viewing Options', 'Vials may be viewed individually, with one row per vial, or by vial group, with one row per subject, time point, and sample type combination.');"
                               tabindex="-1"
                               href="#"><span class="labkey-help-pop-up">?</span>
                        </a>
                    </td>
                </tr>
                <tr>
                    <td style="padding-left:1em">
                        <table class="labkey-nav-tree-child">
                            <tbody>
                            <tr class="labkey-nav-tree-row labkey-header">
                                <td class="labkey-nav-tree-text"><a href="#" onclick="return clickLink('study-samples', 'samples', {showVials: 'false'})">By Vial Group</a>
                                </td>
                            </tr>
                            <tr class="labkey-nav-tree-row labkey-header">
                                <td class="labkey-nav-tree-text"><a href="#" onclick="return clickLink('study-samples', 'samples', {showVials: 'true'})">By Individual Vial</a></td>
                            </tr>
                            </tbody>
                        </table>
                    </td>
                </tr>
                </tbody>
            </table>

            <table class="labkey-nav-tree" style="width: 100%;margin-top:0.5em">
                <tbody>
                <tr class="labkey-nav-tree-row labkey-header">
                    <td class="labkey-nav-tree-text" align="left">
                        <a style="color:#000000;" onclick="return LABKEY.Utils.toggleLink(this, false);" href="#"><img src="<%=getWebappURL("_images/minus.gif")%>" alt="" />
                            <span>Search Specimens</span>
                        </a>
                    </td>
                </tr>
                <tr>
                    <td style="padding-left:1em">
                        <table class="labkey-nav-tree-child">
                            <tbody>
                            <tr class="labkey-nav-tree-row labkey-header">
                                <td class="labkey-nav-tree-text">
                                    <a href="#" onclick="return clickLink('study-samples', 'showSearch', {showVials: 'false'})">For Vial Groups</a></td>
                            </tr>
                            </tbody>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td style="padding-left:1em">
                        <table class="labkey-nav-tree-child">
                            <tbody>
                            <tr class="labkey-nav-tree-row labkey-header">
                                <td class="labkey-nav-tree-text">
                                    <a href="#" onclick="return clickLink('study-samples', 'showSearch', {showVials: 'true'})">For Individual Vials</a></td>
                            </tr>
                            </tbody>
                        </table>
                    </td>
                </tr>
                </tbody>
            </table>



            <table class="labkey-nav-tree" style="width: 100%;margin-top:0.5em">
                <tbody>
                <tr class="labkey-nav-tree-row labkey-header">
                    <td class="labkey-nav-tree-text" align="left">
                        <a style="color:#000000;" onclick="return LABKEY.Utils.toggleLink(this, false);" href="#"><img src="<%=getWebappURL("_images/plus.gif")%>" alt="" />
                            <span>Specimen Reports</span>
                        </a>
                    </td>
                </tr>
                <tr style="display:none">
                    <td style="padding-left:1em">
                        <table class="labkey-nav-tree-child">
                            <tbody>
                            <tr class="labkey-nav-tree-row labkey-header">
                                <td class="labkey-nav-tree-text">
                                    <a href="#" onclick="return clickLink('study-samples', 'autoReportList')">View Available Reports</a></td>
                            </tr>
                            </tbody>
                        </table>
                    </td>
                </tr>
                </tbody>
            </table>
<%
    if (SpecimenManager.getInstance().isSampleRequestEnabled(c))
    {
%>
            <table class="labkey-nav-tree" style="width: 100%;margin-top:0.5em">
                <tbody>
                <tr class="labkey-nav-tree-row labkey-header">
                    <td class="labkey-nav-tree-text" align="left">
                        <a  style="color:#000000;" onclick="return LABKEY.Utils.toggleLink(this, false);" href="#">
                            <img src="<%=getWebappURL("_images/plus.gif")%>" alt="" />
                            <span>Specimen Requests</span>
                        </a>
                    </td>
                </tr>
                <tr style="display:none">
                    <td style="padding-left:1em">
                        <table class="labkey-nav-tree-child">
                            <tbody>
                            <% if (getContainer().hasPermission(getUser(), RequestSpecimensPermission.class)) { %>
                            <tr class="labkey-nav-tree-row labkey-header">
                                <td class="labkey-nav-tree-text"><a href="#" onclick="return clickLink('study-samples', 'showCreateSampleRequest')">Create New Request</a></td>
                            </tr>
                            <% } %>
                            <tr class="labkey-nav-tree-row labkey-header">
                                <td class="labkey-nav-tree-text"><a href="#" onclick="return clickLink('study-samples', 'viewRequests')">View Current Requests</a></td>
                            </tr>
                            </tbody>
                        </table>
                    </td>
                </tr>
                </tbody>
            </table>
<%
    }
%>
        </td>
<%
    if (!bean.isWide())
    {
%>
        </tr><tr>
<%
    }
%>
        <!-- end left column --> <!-- right column -->
        <td valign="top" width="55%">
            <span id="<%=text(groupControl1)%>">
            <table class="labkey-nav-tree" style="width: 100%">
                <tbody>
                <tr class="labkey-nav-tree-row labkey-header" >
                    <td class="labkey-nav-tree-text" align="left">
                        <a  style="color:#000000;" onclick="return LABKEY.Utils.toggleLink(this, false);" href="#">
                            <img src="<%=getWebappURL("_images/minus.gif")%>" alt="" />
                            <span id="<%=text(groupHeading1)%>"></span>
                        </a>
                    </td>
                </tr>
                <tr>
                    <td style="padding-left:1em;width: 100%;">
                        <span id="<%=text(group1)%>"></span>
                    </td>
                </tr>
                </tbody>
            </table>
            </span>
            <span id="<%=text(groupControl2)%>">
            <table class="labkey-nav-tree" style="width: 100%;;margin-top:1em">
                <tbody>
                <tr class="labkey-nav-tree-row labkey-header">
                    <td class="labkey-nav-tree-text" align="left">
                        <a  style="color:#000000;" onclick="return LABKEY.Utils.toggleLink(this, false);" href="#">
                            <img src="<%=getWebappURL("_images/plus.gif")%>" alt="" />
                            <span id="<%=text(groupHeading2)%>"></span>
                        </a>
                    </td>
                </tr>
                <tr style="display:none">
                    <td style="padding-left:1em;width: 100%;">
                        <span id="<%=text(group2)%>"></span>
                    </td>
                </tr>
                </tbody>
            </table>
            </span>
<%
    if (isAdmin)
    {
%>
            <table class="labkey-nav-tree" style="width: 100%;margin-top:0.5em">
                <tbody>
                <tr class="labkey-nav-tree-row labkey-header">
                    <td class="labkey-nav-tree-text" align="left">
                        <a style="color:#000000;" onclick="return LABKEY.Utils.toggleLink(this, false);" href="#"><img src="<%=getWebappURL("_images/plus.gif")%>" alt="" />
                            <span>Administration</span>
                        </a>
                    </td>
                </tr>
<%
        if (!c.isDataspace())
        {
%>
                <tr style="display:none">
                    <td style="padding-left:1em">
                        <table class="labkey-nav-tree-child">
                            <tbody>
                            <tr class="labkey-nav-tree-row labkey-header">
                                <td class="labkey-nav-tree-text">
                                    <a href="#" onclick="return clickLink('study-samples', 'showUploadSpecimens')">Import Specimens</a></td>
                            </tr>
                            </tbody>
                        </table>
                    </td>
                </tr>
<%
        }
%>
                <tr style="display:none">
                    <td style="padding-left:1em">
                        <table class="labkey-nav-tree-child">
                            <tbody>
                            <tr class="labkey-nav-tree-row labkey-header">
                                <td class="labkey-nav-tree-text">
                                    <a href="#" onclick="return clickLink('study', 'manageStudy')">Study Settings</a></td>
                            </tr>
                            </tbody>
                        </table>
                    </td>
                </tr>
                </tbody>
            </table>
<%
    }
%>
        </td>
        <!-- end right column --></tr>
    </tbody>
</table>
</span>
