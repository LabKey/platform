<%
    /*
     * Copyright (c) 2006-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.issue.query.IssuesQuerySchema" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuesController.BuildSummaryBean> me = (JspView<IssuesController.BuildSummaryBean>)HttpView.currentView();
    IssuesController.BuildSummaryBean bean = me.getModelBean();
    ActionURL cancelURL = QueryService.get().urlFor(getUser(), getContainer(), QueryAction.executeQuery, "issues", IssuesQuerySchema.TableType.IssueListDef.name());

%>

<script type="text/javascript">

    function expandCollapse(id){
        var imgId = 'img_' + id;
        if(document.getElementById(id).className == 'collapsed'){
            document.getElementById(id).className = 'expanded';
            document.getElementById(imgId).src=LABKEY.contextPath + "/_images/minus.gif";
        }
        else{
            document.getElementById(id).className = 'collapsed';
            document.getElementById(imgId).src = LABKEY.contextPath + "/_images/plus.gif";
        }
    }
</script>

<style type="text/css">
    .collapsed {
        display:none;
    }
    .expanded {
        display:block;
    }

</style>

<p>Note: This list is missing secure issues. If you're waiting for one of these issues to get fixed, please follow up with the LabKey support team </p>
    <br>
    <%

    if (!bean.getSummarizedIssues().isEmpty())
    {
        String sectionExpandoId = "summary_" + GUID.makeGUID();
        String imgSectionExpandoId = "img_" + sectionExpandoId;
    %>
        <div style="display:block; margin-top:10px"></div>
        <a href="javascript:expandCollapse(<%=q(sectionExpandoId)%>);">
            <img style="padding-top:8px;padding-right:5px" border="0" align="left" class="expandIcon" id="<%=h(imgSectionExpandoId)%>" src="/labkey/_images/plus.gif">
        </a>
        <h3>Issues</h3>
        <br style="clear:both">
        <div class="collapsed" style="margin-top:-20px" id="<%=h(sectionExpandoId)%>">
        <ul style="list-style-type: none;">

        <%
            for (String verificationType : bean.getSummarizedIssues().keySet())
            {
                String verificationTypeExpandoId = GUID.makeGUID();
                String verificationTypeExpandoImgId = "img_" + verificationTypeExpandoId;
                Map<String, List<IssuesController.BuildIssue>> areaIssues = bean.getSummarizedIssues().get(verificationType);
                if (!areaIssues.isEmpty())
                {
        %>
                <li>
                    <a href="javascript:expandCollapse(<%=q(verificationTypeExpandoId)%>);">
                        <img src="/labkey/_images/plus.gif" id="<%=h(verificationTypeExpandoImgId)%>" align="left" border="0" class="expandIcon" style="padding-top:4px;padding-right:5px"></a>
                    <h3><%=h(verificationType)%></h3>
                    <br style="clear:both">
                    <div class="collapsed" style="margin-top:-20px" id="<%=h(verificationTypeExpandoId)%>">
                        <ul style="list-style-type: none;">
                            <%
                                for (String area : areaIssues.keySet())
                                {
                                    List<IssuesController.BuildIssue> issues = areaIssues.get(area);
                                    if (!issues.isEmpty())
                                    {
                                        String areaExpandoId = GUID.makeGUID();
                                        String areaExpandoImgId = "img_" + areaExpandoId;
                            %>
                                    <li>
                                        <a href="javascript:expandCollapse(<%=q(areaExpandoId)%>);">
                                            <img src="/labkey/_images/plus.gif" id="<%=h(areaExpandoImgId)%>" align="left" border="0" class="expandIcon" style="padding-top:4px;padding-right:5px"></a>
                                        <h3><%=h(area)%></h3>
                                        <br style="clear:both">
                                        <div class="collapsed" style="margin-top:-20px" id="<%=h(areaExpandoId)%>">
                                            <ul class="collapsiblelist">
                                                <%
                                                    for (IssuesController.BuildIssue issue : issues)
                                                    {
                                                %>
                                                        <li><%=h(issue.getTitle())%></li>
                                                <%
                                                    }
                                                %>
                                            </ul>
                                        </div>
                                    </li>
                            <%
                                    }
                                }
                            %>
                        </ul>
                    </div>
                </li>
        <%
                }
            }

        %>

        </ul>
        </div>

    <%
    }

    %>
