<%
/*
 * Copyright (c) 2026 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page import="org.labkey.api.pipeline.PipelineStatusUrls" %>
<%@ page import="org.labkey.api.study.SpecimenService" %>
<%@ page import="org.labkey.api.study.StudyUrls" %>
<%@ page import="org.labkey.api.util.ButtonBuilder" %>
<%@ page import="org.labkey.api.util.InputBuilder" %>
<%@ page import="org.labkey.api.util.InputBuilder.Input" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.SelectBuilder" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ReloadQueryBasedImportAction" %>
<%@ page import="org.labkey.specimen.importer.QueryBasedSpecimenTransform" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    Container c = getContainer();
    String selected = SpecimenService.get().getActiveSpecimenImporter(c);
    boolean active = selected == null || selected.equals(QueryBasedSpecimenTransform.NAME);
    String QBSpecimenImportKey = QueryBasedSpecimenTransform.PROPERTY_MAP_KEY;

    PropertyManager.PropertyMap props = PropertyManager.getProperties(c, QBSpecimenImportKey);

    String schemaName = props.get("schemaName");
    String queryName = props.get("queryName");
    String viewName = props.get("viewName");
    boolean enabled = ("on").equals(props.get("enabled"));
%>

<style type="text/css">
    .QBSpecimenImportFormFields {
        max-width: 80%;
    }
</style>

<labkey:errors/>

<div>
    <p>
        Specimen data can be automatically loaded from an existing query using the specimen import configuration
        defined on this page. The schema you specify must already be defined as an external schema, and the query
        and view selected must map and filter rows as expected by the LabKey specimen repository.
    </p>

    <p>
        Learn more about configuring <%=helpLink("querySpecimenImport", "Query-based specimen reload")%>.
    </p>

    <br>
    <labkey:form method="post">
        <labkey:panel id="overview" className="lk-sg-section">
            <h4 class="labkey-page-section-header">Configure connection</h4>

            <div class="QBSpecimenImportFormFields">
                <%=InputBuilder.checkbox()
                        .name("enabled")
                        .label("Enable reload")
                            .layout(Input.Layout.HORIZONTAL)
                        .checked(enabled)
                        .formGroup(true)
                %>

                <%= new SelectBuilder()
                        .name("schemaName")
                        .id("schemaName")
                        .label("Schema")
                        .layout(Input.Layout.HORIZONTAL)
                        .formGroup(true)
                %>

                <%= new SelectBuilder()
                        .name("queryName")
                        .id("queryName")
                        .label("Query")
                        .layout(Input.Layout.HORIZONTAL)
                        .formGroup(true)
                %>

                <%= new SelectBuilder()
                        .name("viewName")
                        .id("viewName")
                        .label("View")
                        .layout(Input.Layout.HORIZONTAL)
                        .formGroup(true)
                %>
            </div>
        </labkey:panel>

            <%=  new ButtonBuilder("Cancel")
                    .href(PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(getContainer()))
                    .build()
            %>

            <%=  new ButtonBuilder("Save")
                    .submit(true)
                    .build()
            %>

            <%=  new ButtonBuilder("Reload Now")
                    .id("reloadNow")
                    .enabled(enabled && active)
                    .onClick("reloadNow();")
                    .build()
            %>
    </labkey:form>

    <script type="text/javascript" nonce="<%=getScriptNonce()%>">
        LABKEY.Query.schemaSelectInput({renderTo: 'schemaName', initValue: <%=q(schemaName)%>});
        LABKEY.Query.querySelectInput({renderTo: 'queryName', schemaInputId: 'schemaName', initValue: <%=q(queryName)%>});
        LABKEY.Query.queryViewSelectInput({renderTo: 'viewName', schemaInputId: 'schemaName', queryInputId: 'queryName', initValue: <%=q(viewName)%>});

        function reloadNow() {
            LABKEY.Ajax.request({
                url    : <%=q(urlFor(ReloadQueryBasedImportAction.class))%>,
                method  : 'POST',
                success : function(){
                    window.location.href = <%=q(PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(getContainer()))%>;
                },
                failure : LABKEY.Utils.getCallbackWrapper(function(response)
                {
                    LABKEY.Utils.alert("Error", response.exception);
                })
            });
        }
    </script>
</div>
