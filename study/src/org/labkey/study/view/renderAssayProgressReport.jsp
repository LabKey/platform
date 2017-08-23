<%
    /*
     * Copyright (c) 2017 LabKey Corporation
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

<%@ page import="com.fasterxml.jackson.databind.ObjectMapper"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.reports.AssayProgressReport" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("progress-report");
    }
%>

<%
    JspView<AssayProgressReport.AssayReportBean> me = (JspView<AssayProgressReport.AssayReportBean>)HttpView.currentView();
    AssayProgressReport.AssayReportBean form = me.getModelBean();
    List<Map<String, String>> legend = AssayProgressReport.SpecimenStatus.serialize();
    String renderId = "participant-report-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    ObjectMapper jsonMapper = new ObjectMapper();
    List<Map<Object, Object>> assays = new ArrayList<>();

    for (Map.Entry<Integer, Map<String, Object>> entry : form.getAssayData().entrySet())
    {
        assays.add(PageFlowUtil.map("name", entry.getValue().get("name"), "id", entry.getKey()));
    }
%>
<labkey:errors/>
<labkey:scriptDependency/>
<labkey:panel>
    <div id=<%=h(renderId)%>></div>
</labkey:panel>
<script type="text/javascript">
    Ext4.onReady(function(){

        new LABKEY.ext4.AssayProgressReport({
            renderTo        : <%=q(renderId)%>,
            reportId        : <%=q(form.getId().toString())%>,
            assays          : <%=text(jsonMapper.writeValueAsString(assays))%>,
            legend          : <%=text(jsonMapper.writeValueAsString(legend))%>
        });
    });
</script>