<%
    /*
     * Copyright (c) 2018 LabKey Corporation
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
<%@ page import="org.labkey.api.reports.ExternalScriptEngineDefinition" %>
<%@ page import="org.labkey.api.reports.LabkeyScriptEngineManager" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.AdminController.RConfigForm" %>
<%@ page import="javax.script.ScriptEngine" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.Arrays" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("Ext4");
    }
%>
<%
    JspView<RConfigForm> me = (JspView<RConfigForm>) HttpView.currentView();
    RConfigForm form = me.getModelBean();

    ActionURL postURL = new ActionURL(AdminController.RConfigurationAction.class, getContainer());

    LabkeyScriptEngineManager mgr = ServiceRegistry.get().getService(LabkeyScriptEngineManager.class);
    List<ExternalScriptEngineDefinition> engineDefinitions = new ArrayList<>();

    boolean isFolderScoped = false;
    LabkeyScriptEngineManager svc = ServiceRegistry.get().getService(LabkeyScriptEngineManager.class);
    for (ExternalScriptEngineDefinition def : svc.getScopedEngines(getContainer()))
    {
        if (def.isEnabled() && Arrays.asList(def.getExtensions()).contains("r"))
        {
            isFolderScoped = true;
            break;
        }
    }

    ScriptEngine currentEngine = null;
    if (null != mgr)
    {
        engineDefinitions.addAll(mgr.getEngineDefinitions(ExternalScriptEngineDefinition.Type.R, true));
        currentEngine = mgr.getEngineByExtension(getContainer(), "r");
    }
    String currentName = ((currentEngine != null) ? currentEngine.getFactory().getEngineName() : null);
%>
<%
ScriptEngine parentEngine = mgr.getEngineByExtension(getContainer().getParent(), "r");
if (parentEngine != null)
{
    String parentName = parentEngine.getFactory().getEngineName();
%>
<h4>Available R Configurations</h4>
<hr/>
<div style="max-width: 768px; margin-bottom: 20px">
    Overriding the default R configuration defined at the Site level or in a parent folder allows R reports to be
    run under a different R configuration in this folder and in child folders.
</div>
<labkey:form id="configForm" action="<%=postURL%>" method="POST">
<div style="max-width: 600px">
    <div class="row" style="height: 25px;">
        <div class="col-xs-5">
            Use parent R configuration:
        </div>
        <div id="parentConfig" class="col-xs-7">
            <labkey:radio name="overrideDefault" value="parent" currentValue="parent"/><%=h(parentName)%>
        </div>
    </div>
    <div class="row" style="height: 30px;">
        <div class="col-xs-5">
            <label for="overrideDefault">Use folder level R Configuration</label>
        </div>
        <div class="col-xs-7 form-inline">
            <labkey:radio name="overrideDefault" value="override" currentValue="parent"/>
            <labkey:select name="engineRowId">
                <option disabled <%= selected(!isFolderScoped) %> value="">
                    Select a configuration...
                </option>
            <%
                for (ExternalScriptEngineDefinition def : engineDefinitions)
                {
            %>
                    <option <%= selected(isFolderScoped && currentName.equals(def.getName())) %> value="<%=h(def.getRowId())%>">
                        <%=h(def.getName())%>
                    </option>
            <%
                }
            %>
            </labkey:select>

        </div>
    </div>
    <div class="row" id="rButtonGroup" style="margin-left: 0">
        <div class="btn-group" role="group" aria-label="Form Buttons">
            <%= button("Save").id("saveBtn").enabled(false).submit(false).primary(true) %>
        </div>
    </div>
</div>
</labkey:form>

<script type="text/javascript">
(function($) {
    var initialOverride = "<%=h(parentName)%>";
    var useParent = $("input[name='overrideDefault'][value='parent']");
    var overrideParent = $("input[name='overrideDefault'][value='override']");
    var engineSelect = $("select[name='engineRowId']");
    var saveBtn = $("#saveBtn");

    window.onload = function() {
        if (<%=parentName.equals(currentName) && !isFolderScoped%>) {
            useParent.prop("checked", true);
            engineSelect.prop("disabled", true);
        }
        else {
            overrideParent.prop("checked", true);
            initialOverride = engineSelect.find(":selected").text();
        }
    };

    useParent.click(function() {
        engineSelect.prop("disabled", true);
        setSaveDisableStatus();
    });

    overrideParent.click(function() {
        engineSelect.prop("disabled", false);
        setSaveDisableStatus();
    });

    engineSelect.change(setSaveDisableStatus);

    // enables/disables the "Save" button if current state differs/matches the initial state respectively
    function setSaveDisableStatus() {
        if (((initialOverride == "<%=h(parentName)%>") && useParent.prop("checked")) ||
                ((initialOverride == engineSelect.find(":selected").text()) && overrideParent.prop("checked")) ||
                ((engineSelect.find(":selected").text().trim() == "Select a configuration...") && overrideParent.prop("checked"))) {
            saveBtn.addClass("labkey-disabled-button");
        } else {
            saveBtn.removeClass("labkey-disabled-button");
        }
    }

    saveBtn.click(function() {
        if (!saveBtn.hasClass("labkey-disabled-button")) {
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
    <a href="https://www.labkey.org/Documentation/wiki-page.view?name=configureScripting&_docid=wiki%3A32d70ce8-ed56-1034-b734-fe851e088836">here</a>.
</div>
<%
}
%>




