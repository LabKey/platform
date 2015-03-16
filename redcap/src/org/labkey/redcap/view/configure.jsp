<%
    /*
     * Copyright (c) 2015 LabKey Corporation
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
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.redcap.RedcapController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.redcap.RedcapManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4"));
        resources.add(ClientDependency.fromPath("redcap/settings.js"));
        return resources;
    }
%>
<%
    JspView<RedcapManager.RedcapSettings> me = (JspView<RedcapManager.RedcapSettings>)HttpView.currentView();
    RedcapManager.RedcapSettings bean = me.getModelBean();

    ObjectMapper jsonMapper = new ObjectMapper();
%>

<labkey:errors/>
<script type="text/javascript">

    Ext4.onReady(function() {

        new LABKEY.ext4.RedcapSettings({
            renderTo    : 'rc-config-div',
            bean        : <%=text(jsonMapper.writeValueAsString(bean))%>
        });
    });

</script>

<div>
    <div id='rc-config-div'></div>
</div>
