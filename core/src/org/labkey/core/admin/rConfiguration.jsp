<%
    /*
     * Copyright (c) 2005-2018 LabKey Corporation
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
    ScriptEngine currentEngine = null;
    if (null != mgr)
    {
        engineDefinitions.addAll(mgr.getEngineDefinitions(ExternalScriptEngineDefinition.Type.R));
        currentEngine = mgr.getEngineByExtension(getContainer(), "r");
    }

    String currentName = currentEngine.getFactory().getEngineName();
    String parentName = form.getParentEngine().getFactory().getEngineName();

    System.out.println(currentName);
    System.out.println(parentName);
%>
<h4>Available R Configurations</h4>
<hr/>
<div style="max-width: 768px; margin-bottom: 20px">
    Overriding the default R configuration defined at the Site level or in a parent folder allows R reports to be
    run under a different R configuration in this folder and in child folders, e.g. different R version.
</div>
<labkey:form id="configForm" action="<%=postURL%>" method="POST">
<div style="max-width: 400px">
    <div class="row">
        <div class="col-xs-6">
            Parent R Configuration:
        </div>
        <% if (form.getParentEngine() != null) { %>
            <div class="col-xs-6">
                <%= h(parentName) %>
            </div>
        <%}%>
    </div>
    <div class="row" style="margin-bottom: 10px">
        <div class="col-xs-6">
            <label for="overrideInput">Override R Configuration?</label>
        </div>
        <div class="col-xs-6 form-inline">
            <labkey:input id="overrideInput" name="overrideDefault" type="checkbox"/>
            <labkey:select name="engineRowId">
                <%
                for (ExternalScriptEngineDefinition def : engineDefinitions)
                {
                    if (!parentName.equals(def.getName()))
                    {
                %>
                        <option <%= selected(currentName.equals(def.getName())) %> value="<%=h(def.getRowId())%>">
                            <%=h(def.getName())%>
                        </option>
                <%
                    }
                }
                %>
            </labkey:select>
        </div>
    </div>
    <div class="row" id="rButtonGroup" style="margin-left: 0">
        <div class="btn-group" role="group" aria-label="Form Buttons">
            <%= button("Save").id("saveBtn").attributes("disabled").primary(true).disableOnClick(true).submit(false).onClick("confirmSubmit();") %>
        </div>
    </div>
</div>
</labkey:form>
<script type="text/javascript">
    var intialOverride = null;

    var saveBtn = document.getElementById("saveBtn");
    var overrideInput = document.getElementById("overrideInput");
    var engineSelect = document.getElementsByName("engineRowId")[0];

    overrideInput.addEventListener("change", function() {
        if ((initialOverride == null) == overrideInput.checked) {
            saveBtn.removeAttribute("disabled");
        } else {
            saveBtn.setAttribute("disabled", true);
        }
    });

    engineSelect.addEventListener("change", function() {
        if (engineSelect[engineSelect.selectedIndex].value != initialOverride) {
            saveBtn.removeAttribute("disabled");
        } else {
            saveBtn.setAttribute("disabled", true);
        }
    });

    function confirmSubmit() {
        if (!saveBtn.getAttribute("disabled")) {
            Ext4.Msg.confirm('Override Default R Configuration',
                "Are you sure you wish to override the default R configuration? Existing reports may be affected by this action.", function (btn, text) {
                    if (btn == "yes") {
                        document.getElementById("configForm").submit();
                    }
                });
        }
    }

    +function($){
        $(document).ready(function() {
            initialOverride = $("select[name='engineRowId']").val();

            if(<%= !currentName.equals(parentName) %>) {
                $("#overrideInput").attr("checked", true);
            }

            if (!$("#overrideInput").attr("checked")) {
                $("select[name='engineRowId']").css('display', 'none');
            }
        });

        $('#overrideInput').click(function() {
            $("select[name='engineRowId']").toggle(this.checked);
        });
    }(jQuery)
</script>



