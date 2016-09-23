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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuesController.BuildSummaryBean> me = (JspView<IssuesController.BuildSummaryBean>)HttpView.currentView();
    IssuesController.BuildSummaryBean bean = me.getModelBean();
    ActionURL cancelURL = QueryService.get().urlFor(getUser(), getContainer(), QueryAction.executeQuery, "issues", IssuesQuerySchema.TableType.IssueListDef.name());

%>

<style type="text/css">
    .collapsed {
        display:none;
    }
    .expanded {
        display:block;
    }

</style>

<div id="issueContentDiv">
</div>

<script type="text/javascript">
    document.addEventListener('DOMContentLoaded', function() {
        Ext4.onReady(function(){
            var url = LABKEY.ActionURL.buildURL('issues', 'getBuildSummaryContent', null, {});
            Ext4.Ajax.request({
                url : url,
                method: 'POST',
                success: function(resp){
                    debugger;
                    var json = LABKEY.Utils.decode(resp.responseText);
                    if (!json)
                        return;
                    LABKEY.Utils.loadAjaxContent(resp, "issueContentDiv", function() { });
                },
                failure : function() {
                    Ext4.Msg.alert("Error", "Failed to load build summary content.");
                },
                scope : this
            });
        })

    }, false);
</script>

