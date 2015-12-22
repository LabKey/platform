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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.settings.ConceptURIProperties" %>
<%@ page import="org.labkey.api.exp.property.Lookup" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4"));
        return resources;
    }
%>
<%
    JspView<ExperimentController.ConceptLookupForm> me = (JspView<ExperimentController.ConceptLookupForm>) HttpView.currentView();
    ExperimentController.ConceptLookupForm bean = me.getModelBean();

    Map<String, Lookup> conceptURIs = ConceptURIProperties.getMappings(getContainer());

    String returnUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer()).toString();
%>

<style type="text/css">
    .form-title {
        font-size: 14px;
        font-weight: bold;
        padding-bottom: 10px;
    }
    .form-item, .form-buttons {
        padding: 3px 0;
    }
    .form-label {
        width: 110px;
        display: inline-block;
    }
    .form-spacer {
        padding-top: 40px;
    }

    .concept-tile {
        border: solid #C0C0C0 1px;
        border-radius: 5px;
        padding: 5px;
        background-color: white;
        /*width: 500px;*/
    }
    .concept-title {
        color: #303030;
        font-weight: bold;
    }
</style>

<div class="form-title">Insert/Update Mapping</div>
<labkey:errors/>
<labkey:form action="" method="POST">
    <div class="form-item">
        <div class="form-label"><label for="conceptURI">Concept URI:</label></div>
        <input type="text" id="conceptURI" name="conceptURI" style="width: 300px;">
    </div>
    <div class="form-item">
        <div class="form-label"><label for="containerId">Container Id:</label></div>
        <input type="text" id="containerId" name="containerId" style="width: 300px;">
    </div>
    <div class="form-item">
        <div class="form-label"><label for="schemaName">Schema Name:</label></div>
        <input type="text" id="schemaName" name="schemaName">
    </div>
    <div class="form-item">
        <div class="form-label"><label for="queryName">Query Name:</label></div>
        <input type="text" id="queryName" name="queryName">
    </div>
    <div class="form-buttons">
        <%=button("Submit").submit(true)%> <%=button("Cancel").href(returnUrl)%>
    </div>
</labkey:form>

<%
    if (conceptURIs.size() > 0)
    {
%>
        <div class="form-title form-spacer">Existing Mappings</div>
<%
        for (String uri : conceptURIs.keySet())
        {
            Lookup lookup = conceptURIs.get(uri);
%>
            <div class="concept-tile">
                <div class="concept-title"><%=h(uri)%></div>
                <ul>
                    <li>
                        Container: <%=h(lookup.getContainer() != null ? lookup.getContainer().getPath() : "[current folder]")%></li>
                    <li>Schema Name: <%=h(lookup.getSchemaName())%></li>
                    <li>Query Name: <%=h(lookup.getQueryName())%></li>
                </ul>
                <%=textLink("remove", (URLHelper) null, "removeConceptMapping(" + q(uri) + "); return false;", "removeConceptId")%>
            </div>
            <br/>
<%
        }
    }
%>

<script type="text/javascript">
    var removeConceptMapping = function(uri)
    {
        Ext4.Msg.confirm('Confirm deletion', 'Are you sure you want to remove the Concept URI mapping for "' + uri + '"?',
            function(buttonId)
            {
                if (buttonId == 'yes')
                {
                    LABKEY.Ajax.request({
                        url: LABKEY.ActionURL.buildURL('experiment', 'removeConceptMapping.api'),
                        params: {conceptURI: uri},
                        scope: this,
                        success: LABKEY.Utils.getCallbackWrapper(function (response)
                        {
                            window.location.reload();
                        }, this, false),
                        failure: LABKEY.Utils.getCallbackWrapper(function (response)
                        {
                            Ext4.Msg.alert('Error', response.exception);
                        }, this, true)
                    });
                }
            }
        );
    }
</script>