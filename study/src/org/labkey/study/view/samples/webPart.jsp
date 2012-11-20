<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page import="org.labkey.study.samples.SamplesWebPart" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.study.security.permissions.RequestSpecimensPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext currentContext = HttpView.currentContext();
    SamplesWebPart.SamplesWebPartBean bean = (SamplesWebPart.SamplesWebPartBean) HttpView.currentView().getModelBean();

    Container c = currentContext.getContainer();
    User user = currentContext.getUser();
    String contextPath = currentContext.getContextPath();
%>
<script type="text/javascript">
    LABKEY.requiresScript("study/redesignUtils.js", true);
</script>
<script type="text/javascript">

    function populateTypeSummaryContent(typeList, hidden)
    {
        var innerHTML = '<table width="100%" class="labkey-study-expandable-nav">';
        for (var i = 0; i < typeList.length; i++)
        {
            var details = typeList[i];
            innerHTML += '<tr class="labkey-header">';
            if (details.children)
                innerHTML += '<td class="labkey-nav-tree-node"><a onclick="return toggleLink(this, false);" href="#"><img src="<%=contextPath%>/_images/plus.gif"></a></td>';
            else
                innerHTML += '<td class="labkey-nav-tree-node"><img width="9" src="<%=contextPath%>/_.gif"></td>';

            innerHTML += '<td class="labkey-nav-tree-text" width="100%"><a href=\"' + details.url + '\">' + details.label + '</a></td><td align="right" class="labkey-nav-tree-total">' + details.count + '</td</tr>';
            if (details.children)
                innerHTML += '<tr style="display:none;"><td></td><td colspan="2">' + populateTypeSummaryContent(details.children, true) + '</td></tr>';
        }
        innerHTML += '</table>';
        return innerHTML;
    }

    function populateTypeSummary(typeList, elementId)
    {
        var innerHTML = populateTypeSummaryContent(typeList, false);
        document.getElementById(elementId).innerHTML = innerHTML;
    }

    function handleTypeSummary(allTypes)
    {
        if (allTypes.primaryTypes.length == 0)
        {
            var html = '<i>No specimens found.</i>';
        <%
            if (c.hasPermission(user, AdminPermission.class))
            {
        %>
            var importUrl = LABKEY.ActionURL.buildURL('study-samples', 'showUploadSpecimens', LABKEY.ActionURL.getContainer());
            html += '<p><a href="' + importUrl + '">Import Specimens</a></p>';
        <%
            }
        %>
            document.getElementById('specimen-browse-webpart-content').innerHTML = html;
        }
        else
        {
            populateTypeSummary(allTypes.primaryTypes, 'primaryTypes');
            populateTypeSummary(allTypes.derivativeTypes, 'derivativeTypes');
        }
        document.getElementById('specimen-browse-webpart-content').setAttribute('style', 'display: inline');
    }

    Ext.onReady(function() {
        LABKEY.Specimen.getVialTypeSummary({
            success: handleTypeSummary
        });
    });
</script>
<span id="specimen-browse-webpart-content" style="display: none">
<table class="labkey-manage-display" style="width: 100%;">
    <tbody>
    <tr><!-- removed lines beneath headings --> <!-- using labkey nav tree markup, which probably doesn't display in wikis --> <!-- hardcoding plus minus images for looks only --> <!-- removed search links, as that's now handled by a new webpart --> <!-- left column -->
        <td valign="top" width="45%">
            <table class="labkey-nav-tree" style="width: 100%;">
                <tbody>
                <tr class="labkey-nav-tree-row labkey-header">
                    <td class="labkey-nav-tree-text" align="left">
                        <a  style="color:#000000;" onclick="return toggleLink(this, false);" href="#"><img src="<%=contextPath%>/_images/minus.gif" alt="" />
                            <span>View All Specimens<a onmouseover="return showHelpDivDelay(this, 'Vial Viewing Options', 'Vials may be viewed individually, with one row per vial, or by vial group, with one row per subject, time point, and sample type combination.');"
                               onmouseout="return hideHelpDivDelay();"
                               onclick="return showHelpDiv(this, 'Vial Viewing Options', 'Vials may be viewed individually, with one row per vial, or by vial group, with one row per subject, time point, and sample type combination.');"
                               tabindex="-1"
                               href="#"><span class="labkey-help-pop-up">?</span></a></span>
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
                        <a style="color:#000000;" onclick="return toggleLink(this, false);" href="#"><img src="<%=contextPath%>/_images/minus.gif" alt="" />
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
                        <a style="color:#000000;" onclick="return toggleLink(this, false);" href="#"><img src="<%=contextPath%>/_images/plus.gif" alt="" />
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
    if (SampleManager.getInstance().isSampleRequestEnabled(c))
    {
%>
            <table class="labkey-nav-tree" style="width: 100%;margin-top:0.5em">
                <tbody>
                <tr class="labkey-nav-tree-row labkey-header">
                    <td class="labkey-nav-tree-text" align="left">
                        <a  style="color:#000000;" onclick="return toggleLink(this, false);" href="#">
                            <img src="<%=contextPath%>/_images/plus.gif" alt="" />
                            <span>Specimen Requests</span>
                        </a>
                    </td>
                </tr>
                <tr style="display:none">
                    <td style="padding-left:1em">
                        <table class="labkey-nav-tree-child">
                            <tbody>
                            <% if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), RequestSpecimensPermission.class)) { %>
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
            <table class="labkey-nav-tree" style="width: 100%">
                <tbody>
                <tr class="labkey-nav-tree-row labkey-header" >
                    <td class="labkey-nav-tree-text" align="left">
                        <a  style="color:#000000;" onclick="return toggleLink(this, false);" href="#">
                            <img src="<%=contextPath%>/_images/minus.gif" alt="" />
                            <span>Vials by Primary Type</span>
                        </a>
                    </td>
                </tr>
                <tr>
                    <td style="padding-left:1em;width: 100%;">
                        <span id="primaryTypes">Loading...</span>
                    </td>
                </tr>
                </tbody>
            </table>
            <table class="labkey-nav-tree" style="width: 100%;;margin-top:1em">
                <tbody>
                <tr class="labkey-nav-tree-row labkey-header">
                    <td class="labkey-nav-tree-text" align="left">
                        <a  style="color:#000000;" onclick="return toggleLink(this, false);" href="#">
                            <img src="<%=contextPath%>/_images/plus.gif" alt="" />
                            <span>Vials by Derivative</span>
                        </a>
                    </td>
                </tr>
                <tr style="display:none">
                    <td style="padding-left:1em;width: 100%;">
                        <span id="derivativeTypes">Loading...</span>
                    </td>
                </tr>
                </tbody>
            </table>
<%
    if (c.hasPermission(user, AdminPermission.class))
    {
%>
            <table class="labkey-nav-tree" style="width: 100%;margin-top:0.5em">
                <tbody>
                <tr class="labkey-nav-tree-row labkey-header">
                    <td class="labkey-nav-tree-text" align="left">
                        <a style="color:#000000;" onclick="return toggleLink(this, false);" href="#"><img src="<%=contextPath%>/_images/plus.gif" alt="" />
                            <span>Administration</span>
                        </a>
                    </td>
                </tr>
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
