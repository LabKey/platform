<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.pipeline.PipelineController" %>
<%@ page import="org.labkey.api.pipeline.TaskPipeline" %>
<%@ page import="org.labkey.api.pipeline.PipelineJobService" %>
<%@ page import="org.labkey.api.pipeline.file.FileAnalysisTaskPipeline" %>
<%@ page import="org.labkey.api.util.element.TextArea" %>
<%@ page import="org.labkey.api.pipeline.trigger.PipelineTriggerType" %>
<%@ page import="org.labkey.api.pipeline.trigger.PipelineTriggerRegistry" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    HttpView<PipelineController.PipelineTriggerForm> me = (HttpView<PipelineController.PipelineTriggerForm>) HttpView.currentView();
    PipelineController.PipelineTriggerForm bean = me.getModelBean();

    String portalTitle = "Create Pipeline Trigger";
    Integer rowId = bean.getRowId();
    //If we have a rowId, then we have to fill in input values from the table
    if (rowId != null)
        portalTitle = "Update Pipeline Trigger";
%>
<style type="text/css">
    body { overflow-y: scroll; }
    .lk-trigger-section { display: none; }
</style>
<%
    if (PipelineTriggerRegistry.get().getTypes().isEmpty()) { // PREMIUM UPSELL
%>
    <div class="alert alert-info">
        <h3>There are no pipeline trigger types available on this server.</h3>
        <hr>
        <p>Premium edition subscribers have access to powerful <a class="alert-link" href="https://www.labkey.org/Documentation/wiki-page.view?name=fileWatcher">file watcher</a>
            triggers that can automatically initiate pipeline tasks.</p>
        <p>In addition to this feature, premium editions of LabKey Server provide professional support and advanced functionality to help teams maximize the value of the platform.</p>
        <br>
        <p><a class="alert-link" href="https://www.labkey.com/platform/go-premium/" target="_blank">Go Premium <i class="fa fa-external-link"></i></a></p>
    </div>

<%
    }
    else
    {
%>
<labkey:form layout="horizontal" id="pipelineForm" method="POST" action="<%=h(buildURL(PipelineController.CreatePipelineTriggerAction.class))%>">
<labkey:panel className="panel-portal" title="<%=text(portalTitle)%>">
    <div class="row">
        <div class="col-sm-2">
            <div id="lk-trigger-nav" class="list-group">
                <a href="#details" class="list-group-item">Details</a>
                <a href="#configuration" class="list-group-item">Configuration</a>
            </div>
            <a class="list-group-item lk-pipeline-allowNavigate" style="margin-top: 2em" target="_blank" href="https://www.labkey.org/Documentation/wiki-page.view?name=fileWatcher">
                Documentation &nbsp; &nbsp;<i class="fa fa-external-link" aria-hidden="true"></i>
            </a>
        </div>
        <div class="col-sm-10">
            <labkey:errors/>
            <div id="details" class="lk-trigger-section">
                <p>Fields marked with an asterisk * are required.</p>

                <labkey:input name="name"
                              className="form-control lk-pipeline-input"
                              label="Name *"
                              value="<%=h(bean.getName())%>"
                              isRequired="true"/>

                <div class=" form-group">
                    <label class="control-label col-sm-3 col-lg-2">
                        Description
                    </label>
                    <div class="col-sm-9 col-lg-10">
                        <%= new TextArea.TextAreaBuilder()
                                .className("form-control lk-pipeline-input")
                                .name("description")
                                .value(bean.getDescription())
                                .columns(40)
                                .rows(4)
                        %>
                    </div>
                </div>

                <div class="form-group">
                    <label class="control-label col-sm-3 col-lg-2">
                        Type *
                    </label>
                    <div class="col-sm-9 col-lg-10">
                        <select name="type" class="form-control">
                            <%
                                for (PipelineTriggerType pipelineTriggerType : PipelineTriggerRegistry.get().getTypes())
                                {
                                    boolean selected = false;
                                    if (bean.getType() != null)
                                    {
                                        selected = bean.getType().equalsIgnoreCase(pipelineTriggerType.getName());
                                    }
                            %>
                            <option <%=selected(selected)%> value="<%=text(pipelineTriggerType.getName())%>"><%=text(pipelineTriggerType.getName())%></option>
                            <%
                                }
                            %>
                        </select>
                    </div>
                </div>

                <div class="form-group">
                    <label class="control-label col-sm-3 col-lg-2">
                        Pipeline Task *
                    </label>
                    <div class="col-sm-9 col-lg-10">
                        <select name="pipelineTask" class="form-control">
                            <%
                                for (TaskPipeline taskPipeline : PipelineJobService.get().getTaskPipelines(getContainer()))
                                {
                                    if (taskPipeline instanceof FileAnalysisTaskPipeline)
                                    {
                                        FileAnalysisTaskPipeline fatp = (FileAnalysisTaskPipeline) taskPipeline;
                                        if (fatp.isAllowForTriggerConfiguration())
                                        {
                                            String taskIdStr = taskPipeline.getId().getName();
                                            boolean selected = false;
                                            if (taskIdStr != null)
                                            {
                                                if (bean.getPipelineTask() != null)
                                                {
                                                    selected = bean.getPipelineTask().equalsIgnoreCase(taskIdStr);
                                                }
                                                else
                                                {
                                                    selected = taskIdStr.equalsIgnoreCase(getActionURL().getParameter("pipelineTask"));
                                                }
                                            }
                            %>
                            <option <%=selected(selected)%> value="<%=text(taskPipeline.getId().toString())%>"><%=text(taskPipeline.getDescription())%></option>
                            <%
                                        }
                                    }
                                }
                            %>
                        </select>
                    </div>
                </div>

                <labkey:input name="username"
                              className="form-control lk-pipeline-param-input"
                              label="Run as username"
                              forceSmallContext="true"
                              value="<%=h(getUser().getDisplayName(getUser()))%>"
                              contextContent="The file watcher will run as this user in the pipeline. Some tasks may require this user to have admin permissions."/>

                <labkey:input name="assay provider"
                              className="form-control lk-pipeline-param-input"
                              label="Assay Provider"
                              forceSmallContext="true"
                              contextContent="Use this provider for running assay import runs" />

                <labkey:input name="enabled"
                              id="pipeline-enabled-check"
                              type="checkbox"
                              label="Enable this trigger"
                              checked="<%=bean.isEnabled()%>" />

                <h4 style="margin-top: 20px"><a href="#configuration">Configure parameters <i class="fa fa-arrow-circle-right" aria-hidden="true"></i> </a></h4>
            </div>

            <div id="configuration" class="lk-trigger-section">
                <p>Fields marked with an asterisk * are required.</p>
                <labkey:input name="location"
                              className="form-control lk-pipeline-input"
                              label="Location *"
                              isRequired="true"
                              forceSmallContext="true"
                              contextContent="This can be an absolute path on the server's file system or a relative path under the container's pipeline root."/>

                <labkey:input name="recursive"
                              id="pipeline-recursive-check"
                              type="checkbox"
                              label="Include child folders" />

                <labkey:input name="filePattern"
                              className="form-control lk-pipeline-input"
                              label="File pattern"
                              forceSmallContext="true"
                              contextContent="A Java regular expression that captures filenames of interest and can extract and use information from the filename to set other properties"/>

                <labkey:input name="quiet"
                              className="form-control lk-pipeline-input"
                              label="Quiet period (seconds) *"
                              type="number"
                              value="1"
                              forceSmallContext="true"
                              contextContent="Number of seconds to wait after file activity before executing a job (minimum is 1)."/>

                <labkey:input name ="move"
                              className="form-control lk-pipeline-input"
                              label="Move process to"
                              forceSmallContext="true"
                              contextContent="Where the file should be moved before analysis. This can be absolute or relative to the current project/folder."/>

                <labkey:input name="copy"
                              className="form-control lk-pipeline-input"
                              label="Copy file to"
                              forceSmallContext="true"
                              contextContent="Where the file should be copied to before analysis. This can be absolute or relative to the current project/folder."/>

                <div class=" form-group">
                    <label class="control-label col-sm-3 col-lg-2">
                        Parameter function
                    </label>
                    <div class="col-sm-9 col-lg-10">
                        <%= new TextArea.TextAreaBuilder()
                                .className("form-control lk-pipeline-input")
                                .name("parameterFunction")
                                .placeholder("This is an additional way to specify parameters in the JSON configuration. It will be executed during the move.")
                                .columns(40)
                                .rows(4)
                        %>
                    </div>
                </div>
                <div id="extraParams"></div>
                <div class="col-sm-3 col-lg-2" style="text-align: right; margin-top: 4px; cursor: pointer"><a class="lk-pipeline-addParamGroup">Add custom parameter</a></div>

                <div style="margin-top: 25px">
                    &nbsp;
                </div>

                <labkey:input type="hidden" name="configJson" id="configJSON"/>
                <labkey:input type="hidden" name="customConfigJson" id="customConfigJSON"/>
                <labkey:input type="hidden" name="rowId" value="<%=h(bean.getRowId())%>"/>
                <labkey:input type="hidden" name="returnUrl" value="<%=h(bean.getReturnUrl())%>"/>
                <div style="margin-bottom: 20px">
                    <h4><a href="#details"><i class="fa fa-arrow-circle-left" aria-hidden="true"></i> Edit details</a></h4>
                </div>
                <%= button("Save").id("btnSubmit").addClass("lk-pipeline-allowNavigate") %>
                <%= button("Cancel").href(bean.getReturnUrl()).id("btnCancel").addClass("lk-pipeline-allowNavigate") %>
            </div>
        </div>
    </div>
</labkey:panel>
</labkey:form>
<script type="text/javascript">
    +function($) {

        <%
            if (StringUtils.isNotEmpty(bean.getConfigJson()) && !bean.getConfigJson().equalsIgnoreCase("{}"))
            {%>
                var configObj = JSON.parse(<%=q(bean.getConfigJson())%>);
                processConfigJson(configObj);

                var customConfigObj = JSON.parse(<%=q(bean.getCustomConfigJson())%>);
                processCustomConfigJson(customConfigObj);
        <%
        }
        else if (rowId != null) {
        %>
            function onSuccess(data) {
                var row = data.rows[0];

                //parse the incoming values and map the necessary fields
                var name = row.Name;
                if (name)
                    $("input[name='name']").val(name);

                var desc = row.Description;
                if (desc)
                    $("textarea[name='description']").val(desc);

                var type = row.Type;
                if (type) {
                    $("select[name='type'] option").map(function () {
                        if ($(this).text() === type) {
                            return this;
                        }
                    }).attr('selected', true);
                }

                var task = row.PipelineTask;
                if (task) {
                    $("select[name='pipelineTask'] option").map(function () {
                        if ($(this).text() === task) {
                            return this;
                        }
                    }).attr('selected', true);
                }

                var enabled = row.Enabled;
                if (enabled)
                    $("input[name='enabled']").prop('checked', true);

                //config objects must first be parsed as JSON
                var configObj = JSON.parse(row.Configuration);
                processConfigJson(configObj);

                var customConfigObj = JSON.parse(row.CustomConfiguration);
                processCustomConfigJson(customConfigObj);
            }

            function onFailure(data) {
                LABKEY.Utils.alert("Error", 'Error fetching row: ' + <%=q(Integer.toString(rowId))%>)
            }

            $(document).ready(function () {
                LABKEY.Query.selectRows({
                    schemaName: "pipeline",
                    queryName: "TriggerConfigurations",
                    success: onSuccess,
                    failure: onFailure,
                    filterArray: [LABKEY.Filter.create("RowId", <%=q(Integer.toString(rowId))%>)]
                })
            });
        <%}%>

        function processConfigJson(configObj) {
            if (configObj) {
                for (var k in configObj) {
                    if (configObj.hasOwnProperty(k) && configObj[k]) {
                        if (k === "parameters") {
                            var paramObject = configObj[k];
                            for (var key in paramObject) {
                                if (paramObject.hasOwnProperty(key)) {
                                    var keyName = key.indexOf("pipeline,") === 0 ? key.slice(9).trim() : key;
                                    $("input[name='" + keyName + "']").val(paramObject[key]);
                                }
                            }
                        }
                        else {
                            if (k.toLowerCase() === "quiet") {
                                configObj[k] = configObj[k] / 1000;
                            }
                            $("*[name='" + k + "']").val(configObj[k]);
                        }
                    }
                }
            }
        }

        function processCustomConfigJson(customConfigObj) {
            if (customConfigObj) {
                for (var j in customConfigObj) {
                    if (customConfigObj.hasOwnProperty(j) && customConfigObj[j]) {
                        addParameterGroup(j, customConfigObj[j])
                    }
                }
            }
        }
        window.onbeforeunload = function (e) {
            // Force confirmation when leaving the page
            return true;
        };

        var defaultRoute = "details";

        function loadRoute(hash) {
            if (!hash || hash === '#') {
                hash = '#' + defaultRoute;
            }

            $('#lk-trigger-nav').find('a').removeClass('active');
            $('#lk-trigger-nav').find('a[href=\'' + hash + '\']').addClass('active');
            $('.lk-trigger-section').hide();
            $('.lk-trigger-section[id=\'' + hash.replace('#', '') + '\']').show();
        }

        $(window).on('hashchange', function() {
            loadRoute(window.location.hash);
        });

        $('#extraParams').on('click', '.removeParamTrigger' , function() {
            $(this).parents('.lk-pipeline-customParam-group').remove();
        });

        $(function() {
            loadRoute(window.location.hash);
        });

        $(".lk-pipeline-addParamGroup").on('click', function () {
            addParameterGroup();
        });

        $(".lk-pipeline-allowNavigate").on('click', function () {
            allowNavigate();
        });

        $("#btnSubmit").on('click', (function() {
            var standardObj = {};
            var customObj = {};

            //build the configuration object
            $('.lk-pipeline-input').each(function(i, elem) {
                if (elem.nodeName === "INPUT" || elem.nodeName === "TEXTAREA") {
                    if (elem.getAttribute("type") === "checkbox") {
                        standardObj[elem.getAttribute("name")] = elem.checked;
                    }
                    else if (elem.getAttribute("name") === "quiet"){
                        standardObj[elem.getAttribute("name")] = elem.value * 1000;
                    } else if (elem.value) {
                        standardObj[elem.getAttribute("name")] = elem.value
                    }
                }
            });

            var enabledCheck = jQuery('#pipeline-enabled-check')[0];
            standardObj[enabledCheck.name] = enabledCheck.checked;

            var recursiveCheck = jQuery('#pipeline-recursive-check')[0];
            standardObj[recursiveCheck.name] = recursiveCheck.checked;

            var paramObj = {};
            var prefix = "pipeline, ";
            $('.lk-pipeline-param-input').each(function(i, elem) {
                if (elem.value) {
                    var k = prefix + elem.getAttribute("name");
                    paramObj[k] = elem.value;
                }
            });
            standardObj["parameters"] = paramObj;

            //build the custom configuration object
            $('.lk-pipeline-customParam-group').each(function(i, elem) {
                var key = elem.getElementsByClassName("lk-pipeline-custom-key")[0].value;
                var value = elem.getElementsByClassName("lk-pipeline-custom-value")[0].value;
                if (value) {
                    customObj[key] = value;
                }
            });

            $('#configJSON').val(JSON.stringify(standardObj));
            $('#customConfigJSON').val(JSON.stringify(customObj));

            $("#pipelineForm").submit();
        }));

        function addParameterGroup(key, value) {
            var elem = $("<div class='form-group lk-pipeline-customParam-group'>" +
                    "<div class='col-sm-3 col-lg-2'>" +
                    "<input type='text' class='form-control lk-pipeline-custom-key' placeholder='Name' name='customParamKey' style='float: right;'>" +
                    "</div>" +
                    "<div class='col-sm-9 col-lg-10'>" +
                    "<input type='text' class='form-control lk-pipeline-custom-value' placeholder='Value' name='customParamValue' style='display: inline-block;'>" +
                    "<a class='removeParamTrigger' style='cursor: pointer;' title='remove'><i class='fa fa-trash' style='padding: 0 8px;'></i></a>" +
                    "</div>" +
                    "</div>");

            if (key && value) {
                elem.find(".lk-pipeline-custom-key").val(key);
                elem.find(".lk-pipeline-custom-value").val(value);
            }

            elem.appendTo($("#extraParams"));
        }

        function allowNavigate() {
            window.onbeforeunload = undefined;
        }
    }(jQuery);
</script>
<%
    }
%>