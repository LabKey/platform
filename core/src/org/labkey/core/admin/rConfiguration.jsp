<%
/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
<%@ page import="org.jetbrains.annotations.Nullable" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.reports.ExternalScriptEngineDefinition" %>
<%@ page import="org.labkey.api.reports.LabKeyScriptEngineManager" %>
<%@ page import="org.labkey.api.reports.report.r.RemoteRNotEnabledException" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.element.Option.OptionBuilder" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="javax.script.ScriptEngine" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }
%>
<%
    JspView<AdminController.RConfigForm> me = (JspView<AdminController.RConfigForm>) HttpView.currentView();
    AdminController.RConfigForm form = me.getModelBean();
    Container container = getContainer();
    LabKeyScriptEngineManager mgr = LabKeyScriptEngineManager.get();
    List<ExternalScriptEngineDefinition> engineDefinitions = new ArrayList<>(mgr.getEngineDefinitions(ExternalScriptEngineDefinition.Type.R, true));

    boolean isFolderScoped = form.getOverrideDefault() || (mgr.getScopedEngine(container, "r", LabKeyScriptEngineManager.EngineContext.report, false) != null);
    if (!isFolderScoped)
    {
        isFolderScoped = (mgr.getScopedEngine(container, "r", LabKeyScriptEngineManager.EngineContext.pipeline, false) != null);
    }

    // specific engine context overrides
    String currentReportEngine = getScopedEngineName(form.getReportEngine(), container, LabKeyScriptEngineManager.EngineContext.report);
    String currentPipelineEngine = getScopedEngineName(form.getPipelineEngine(), container, LabKeyScriptEngineManager.EngineContext.pipeline);

    Container parentContainer = container.getParent();
    if (parentContainer != null)
    {
        ScriptEngine parentReportEngine = null;
        ScriptEngine parentPipelineEngine = null;

        try
        {
            parentReportEngine = mgr.getEngineByExtension(parentContainer, "r", LabKeyScriptEngineManager.EngineContext.report);
            parentPipelineEngine = mgr.getEngineByExtension(parentContainer, "r", LabKeyScriptEngineManager.EngineContext.pipeline);
        }
        catch(RemoteRNotEnabledException e)
        {
            //Swallow this exception and let the UI load.
        }

        if (parentReportEngine != null && parentPipelineEngine != null)
        {
            String parentReportLabel = String.format("Reports : %s", parentReportEngine.getFactory().getEngineName());
            String parentPipelineLabel = String.format("Pipeline Jobs : %s", parentPipelineEngine.getFactory().getEngineName());

%>
<style type="text/css">
    div.engine-row {
        padding-bottom: 5px;
    }
</style>

<labkey:errors/>
<h4>Available R Configurations</h4>
<hr/>
<div style="max-width: 768px; margin-bottom: 20px">
    Overriding the default R configuration defined at the Site level or in a parent folder allows R reports to be
    run under a different R configuration in this folder and in child folders.
</div>
<labkey:form id="configForm" method="POST">
<div style="max-width: 750px">
    <div class="row engine-row">
        <div class="col-md-4">
            Use parent R configuration:
        </div>
        <div id="parentConfig" class="col-md-1">
            <labkey:radio name="overrideDefault" value="parent" currentValue="parent"/>
        </div>
        <div id="parentConfigLabel" class="col-md-7 form-inline">
            <%=h(parentReportLabel)%><br><%=h(parentPipelineLabel)%><p>
        </div>
    </div>
    <div class="row engine-row">
        <div class="col-md-4">
            <label for="overrideDefault">Use folder level R Configuration</label>
        </div>
        <div class="col-md-1 form-inline">
            <labkey:radio name="overrideDefault" value="override" currentValue="parent"/>
        </div>
        <div class="col-md-2 form-inline">
            <label class="control-label">Reports
                <i class="fa fa-question-circle context-icon" data-container="body" data-tt="tooltip" data-placement="top" title="" data-original-title="The engine that will be used to run R reports in this folder"></i>
            </label>
        </div>
        <div class="col-md-5 form-inline">
            <%=select()
                .name("reportEngine")
                .addOption(new OptionBuilder("Select a configuration...", "").disabled(true).selected(!isFolderScoped || (currentReportEngine == null)))
                .addOptions(
                    engineDefinitions.stream()
                        .map(def->new OptionBuilder(def.getName(), def.getRowId()).selected(def.getName().equals(currentReportEngine)))
                )
            %>
        </div>
    </div>
    <div class="row engine-row">
        <div class="col-md-4">
        </div>
        <div class="col-md-1 form-inline">
        </div>
        <div class="col-md-2 form-inline">
            <label class="control-label">Pipeline Jobs
                <i class="fa fa-question-circle context-icon" data-container="body" data-tt="tooltip" data-placement="top" title="" data-original-title="The engine that will be used to run R scripts in pipeline jobs and transform scripts"></i>
            </label>
        </div>
        <div class="col-md-5 form-inline">
            <%=select()
                .name("pipelineEngine")
                .addOption(new OptionBuilder("Select a configuration...", "").disabled(true).selected(!isFolderScoped || (currentPipelineEngine == null)))
                .addOptions(
                    engineDefinitions.stream()
                        .map(def->new OptionBuilder(def.getName(), def.getRowId()).selected(def.getName().equals(currentPipelineEngine)))
                )
            %>
        </div>
    </div>
    <div class="row" id="rButtonGroup" style="margin-left: 0">
        <div class="btn-group" role="group" aria-label="Form Buttons">
            <%= button("Save").id("saveBtn").enabled(false).submit(false).primary(true) %>
        </div>
    </div>
</div>
</labkey:form>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
(function($) {
    var useParent = $("input[name='overrideDefault'][value='parent']");
    var overrideParent = $("input[name='overrideDefault'][value='override']");
    var reportEngineSelect = $("select[name='reportEngine']");
    var pipelineEngineSelect = $("select[name='pipelineEngine']");
    var saveBtn = $("#saveBtn");

    window.onload = function() {
        if (<%=!isFolderScoped%>) {
            useParent.prop("checked", true);
            reportEngineSelect.prop("disabled", true);
            pipelineEngineSelect.prop("disabled", true);
        }
        else {
            overrideParent.prop("checked", true);
        }
    };

    useParent.click(function() {
        reportEngineSelect.prop("disabled", true);
        pipelineEngineSelect.prop("disabled", true);
        setDirty();
    });

    overrideParent.click(function() {
        reportEngineSelect.prop("disabled", false);
        pipelineEngineSelect.prop("disabled", false);
        setDirty();
    });

    reportEngineSelect.change(setDirty);
    pipelineEngineSelect.change(setDirty);

    function setDirty() {
        saveBtn.removeClass("labkey-disabled-button");
    }

    saveBtn.click(function() {
        if (!saveBtn.hasClass("labkey-disabled-button")) {

            // validation
            if ($("input[name='overrideDefault']:checked").val() === 'override'){

                if (($("select[name='reportEngine']").val() == null) || ($("select[name='pipelineEngine']").val() == null)){
                    LABKEY.Utils.alert("Update failed", "For folder level configurations, you must specify an engine for running both reports and pipeline jobs (they can be the same engine).");
                    return;
                }
            }
            LABKEY.Utils.modal("Override Default R Configuration", null, submitForm, null);
        }
    });

    // confirm submit modal
    var submitForm = function() {
        var html = [
            "<div style='margin-bottom: 20px;'>",
            "<div id='message'>",
            "Are you sure you wish to override the default R configuration? Existing reports may be affected by this action.",
            "</div>",
            "<a class='btn btn-default' style='float: right' id='cancelSubmitBtn'>No</a>",
            "<a class='btn btn-default' style='float: right; margin-right: 8px' id='confirmSubmitBtn'>Yes</a>",
            "</div>"
        ].join("");

        $("#modal-fn-body").html(html);

        $("#confirmSubmitBtn").on("click", function() {
            saveBtn.addClass("labkey-disabled-button");
            $("#configForm").submit();
        });

        $("#cancelSubmitBtn").on("click", function() {
            $('#lk-utils-modal').modal('hide');
        });
    };
})(jQuery);
</script>
            <%
            }
            else
            {
            %>
            <h4>No Available R Configurations</h4>
            <hr/>
            <div>
                No R engines are configured. For more information on how to configure R engines, click
                <a href="https://www.labkey.org/Documentation/wiki-page.view?name=configureScripting">here</a>.
            </div>
            <%
            }
        }
        else // override not allowed at root
        {
            %>
            <h4>R Configuration override not allowed</h4>
            <hr/>
            <div>
                R configuration is not allowed for root folder. For more information on how to configure R engines, click
                <a href="https://www.labkey.org/Documentation/wiki-page.view?name=configureScripting">here</a>.
            </div>
            <%
        }
%>

<%!
    // helper to return the name of the currently configured engine
    @Nullable
    String getScopedEngineName(Integer reshowId, Container container, LabKeyScriptEngineManager.EngineContext context)
    {
        try
        {
            LabKeyScriptEngineManager mgr = LabKeyScriptEngineManager.get();
            ExternalScriptEngineDefinition engine;
            if (reshowId != null)
            {
                // form reshow
                engine = mgr.getEngineDefinition(reshowId, ExternalScriptEngineDefinition.Type.R);
                if (engine != null)
                    return engine.getName();
            }
            else
            {
                engine = mgr.getScopedEngine(container, "r", context, false);
                if (engine != null)
                    return engine.getName();
            }
        }
        catch(RemoteRNotEnabledException e)
        {
            //Swallow this exception and let the UI load.
        }
        return null;
    }
%>



