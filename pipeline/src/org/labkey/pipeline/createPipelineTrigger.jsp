<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.pipeline.PipelineJobService" %>
<%@ page import="org.labkey.api.pipeline.TaskPipeline" %>
<%@ page import="org.labkey.api.pipeline.file.FileAnalysisTaskPipeline" %>
<%@ page import="org.labkey.api.pipeline.trigger.PipelineTriggerRegistry" %>
<%@ page import="org.labkey.api.pipeline.trigger.PipelineTriggerType" %>
<%@ page import="org.labkey.api.util.element.TextArea" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.pipeline.PipelineController" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.stream.Collectors" %>
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
    HttpView<PipelineController.PipelineTriggerForm> me = (HttpView<PipelineController.PipelineTriggerForm>) HttpView.currentView();
    PipelineController.PipelineTriggerForm bean = me.getModelBean();

    Map<String, FileAnalysisTaskPipeline> triggerConfigTasks = PipelineJobService.get().getTaskPipelines(getContainer())
            .stream()
            .filter(FileAnalysisTaskPipeline.class::isInstance)
            .map(FileAnalysisTaskPipeline.class::cast)
            .filter(FileAnalysisTaskPipeline::isAllowForTriggerConfiguration)
            .collect(Collectors.toMap(FileAnalysisTaskPipeline -> FileAnalysisTaskPipeline.getId().getName(),
                    FileAnalysisTaskPipeline -> (FileAnalysisTaskPipeline) PipelineJobService.get().getTaskPipeline(FileAnalysisTaskPipeline.getId())));

    final String HELP_TEXT = "Fields marked with an asterisk * are required. ";
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
    <div class="row">
        <div class="col-sm-2">
            <div id="lk-trigger-nav" class="list-group">
                <a href="#details" class="list-group-item">Details</a>
                <a href="#configuration" class="list-group-item">Configuration</a>
            </div>
            <a class="list-group-item" style="margin-top: 2em" target="_blank" href="https://www.labkey.org/Documentation/wiki-page.view?name=fileWatcher">
                Documentation &nbsp; &nbsp;<i class="fa fa-external-link" aria-hidden="true"></i>
            </a>
        </div>
        <div class="col-sm-10">
            <labkey:errors/>
            <div id="details" class="lk-trigger-section">
                <div class="alert alert-info" style="max-width: 700px;">
                    <p class="lk-trigger-help-text">
                        <%=h(HELP_TEXT)%>
                    </p>
                </div>
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
                        <select name="pipelineTask" class="form-control" id="pipelineTaskSelect">
                            <%
                                if (bean.getPipelineTask() == null)
                                {
                                    %> <option disabled selected value style="display: none"> </option><%
                                }
                                for (String key : triggerConfigTasks.keySet())
                                {
                                    boolean selected = false;

                                    if (bean.getPipelineTask() != null)
                                        selected = bean.getPipelineTask().equalsIgnoreCase(key);
                            %>
                            <option <%=selected(selected)%> value="<%=text(triggerConfigTasks.get(key).getId().toString())%>"><%=text(triggerConfigTasks.get(key).getDescription())%></option>
                            <%
                                }
                            %>
                        </select>
                    </div>
                </div>

                <labkey:input name="username"
                              className="form-control lk-pipeline-param-input"
                              label="Run As Username"
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
                              label="Enable This Trigger"
                              checked="<%=bean.isEnabled()%>" />

                <br/>
                <%= button("Cancel").href(bean.getReturnUrl()) %>
                &nbsp;&nbsp;
                <%= button("Next").primary(true).href("#configuration") %>
            </div>

            <div id="configuration" class="lk-trigger-section">
                <div class="alert alert-info" style="max-width: 700px;">
                    <p class="lk-trigger-help-text">
                        <%=h(HELP_TEXT)%>
                    </p>
                </div>
                <labkey:input name="location"
                              className="form-control lk-pipeline-input"
                              label="Location *"
                              isRequired="true"
                              forceSmallContext="true"
                              contextContent="This can be an absolute path on the server's file system or a relative path under the container's pipeline root."/>

                <labkey:input name="recursive"
                              id="pipeline-recursive-check"
                              type="checkbox"
                              label="Include Child Folders" />

                <labkey:input name="filePattern"
                              className="form-control lk-pipeline-input"
                              label="File Pattern"
                              forceSmallContext="true"
                              contextContent="A Java regular expression that captures filenames of interest and can extract and use information from the filename to set other properties"/>

                <labkey:input name="quiet"
                              className="form-control lk-pipeline-input"
                              label="Quiet Period (Seconds) *"
                              type="number"
                              value="1"
                              forceSmallContext="true"
                              contextContent="Number of seconds to wait after file activity before executing a job (minimum is 1)."/>

                <labkey:input name ="move"
                              className="form-control lk-pipeline-input"
                              label="Move Process To"
                              forceSmallContext="true"
                              contextContent="Where the file should be moved before analysis. This must be a relative or absolute container path."/>

                <labkey:input name="copy"
                              className="form-control lk-pipeline-input"
                              label="Copy File To"
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

                <div class=" form-group">
                    <div class="col-sm-12 col-lg-12">
                        <div id="extraParams"></div>
                        <a class="lk-pipeline-addParamGroup" style="cursor: pointer; color: #555;">
                            <i class="fa fa-plus-circle"></i> add custom parameter
                        </a>
                    </div>
                </div>

                <labkey:input type="hidden" name="configJson" id="configJSON"/>
                <labkey:input type="hidden" name="customConfigJson" id="customConfigJSON"/>
                <labkey:input type="hidden" name="rowId" value="<%=h(bean.getRowId())%>"/>
                <labkey:input type="hidden" name="returnUrl" value="<%=h(bean.getReturnUrl())%>"/>

                <br/>
                <%= button("Cancel").href(bean.getReturnUrl()) %>
                &nbsp;&nbsp;
                <%= button("Back").href("#details") %>
                <%= button("Save").primary(true).id("btnSubmit").addClass("lk-pipeline-allowNavigate") %>
            </div>
        </div>
    </div>
</labkey:form>
<script type="text/javascript">
    +function($) {
        var taskPipelineVariables = {};
        <%
        for (String key : triggerConfigTasks.keySet())
        {
            FileAnalysisTaskPipeline task = triggerConfigTasks.get(key);
            String helpText = HELP_TEXT + task.getHelpText();
        %>
            taskPipelineVariables[<%=q(task.getId().toString())%>] = {
                helpText: <%=q(helpText)%>,
                moveEnabled: <%=task.isMoveAvailable()%>
            };
        <%
        }

        if (triggerConfigTasks.get(bean.getPipelineTask()) != null)
        {
            String taskStr = triggerConfigTasks.get(bean.getPipelineTask()).getId().toString();
        %>
            setHelpText(<%=q(taskStr)%>);
            handleMoveField(<%=q(taskStr)%>);
        <%
        }

        if (StringUtils.isNotEmpty(bean.getConfigJson()) && !bean.getConfigJson().equalsIgnoreCase("{}"))
        {%>
            var configObj = JSON.parse(<%=q(bean.getConfigJson())%>);
            processConfigJson(configObj);

            var customConfigObj = JSON.parse(<%=q(bean.getCustomConfigJson())%>);
            processCustomConfigJson(customConfigObj);
        <%
        }
        else if (bean.getRowId() != null) {
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
                            setHelpText(this.value);
                            handleMoveField(this.value);
                            return this;
                        }
                    }).attr('selected', true);
                }

                //config objects must first be parsed as JSON
                var configObj = JSON.parse(row.Configuration);
                processConfigJson(configObj);

                var customConfigObj = JSON.parse(row.CustomConfiguration);
                processCustomConfigJson(customConfigObj);
            }

            function onFailure(data) {
                LABKEY.Utils.alert("Error", 'Error fetching row: ' + <%=bean.getRowId()%>)
            }

            $(document).ready(function () {
                LABKEY.Query.selectRows({
                    schemaName: "pipeline",
                    queryName: "TriggerConfigurations",
                    success: onSuccess,
                    failure: onFailure,
                    filterArray: [LABKEY.Filter.create("RowId", <%=bean.getRowId()%>)]
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
                        } else {
                            if (k.toLowerCase() === "quiet") {
                                configObj[k] = configObj[k] / 1000;
                            }

                            var input = $("*[name='" + k + "']");
                            if (input.attr('type') === 'checkbox') {
                                input.prop('checked', configObj[k]);
                            } else {
                                input.val(configObj[k]);
                            }
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

        $("#pipelineTaskSelect").on('change', function () {
            setHelpText(this.value);
            handleMoveField(this.value);
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

            var enabledCheck = $('#pipeline-enabled-check')[0];
            standardObj[enabledCheck.name] = enabledCheck.checked;

            var recursiveCheck = $('#pipeline-recursive-check')[0];
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

        function setHelpText(taskId) {
            if (taskId) {
                $(".lk-trigger-help-text").html(taskPipelineVariables[taskId].helpText);
            } else if ($('#pipelineTaskSelect').val()){
                $(".lk-trigger-help-text").html(taskPipelineVariables[$('#pipelineTaskSelect').val()].helpText);
            }
        }

        function handleMoveField(taskId) {
            if (taskId) {
                var moveElem = $("input[name='move']");
                moveElem.prop('disabled', !taskPipelineVariables[taskId].moveEnabled);
                if (!taskPipelineVariables[taskId].moveEnabled) {
                    moveElem[0].value = "";
                }
            }
        }

        function addParameterGroup(key, value) {
            var elem = $("<div class='form-group lk-pipeline-customParam-group'>" +
                        "<div class='col-sm-3 col-lg-2'>" +
                            "<input type='text' class='form-control lk-pipeline-custom-key' placeholder='Name' name='customParamKey' style='float: right;'>" +
                        "</div>" +
                        "<div class='col-sm-9 col-lg-10'>" +
                            "<input type='text' class='form-control lk-pipeline-custom-value' placeholder='Value' name='customParamValue' style='display: inline-block;'>" +
                            "<a class='removeParamTrigger' style='cursor: pointer;' title='remove'><i class='fa fa-trash' style='padding: 0 8px; color: #555;'></i></a>" +
                        "</div>" +
                    "</div>");

            if (key && value) {
                elem.find(".lk-pipeline-custom-key").val(key);
                elem.find(".lk-pipeline-custom-value").val(value);
            }

            elem.appendTo($("#extraParams"));
        }

        var _isDirty = false, body = $("body");
        body.on("change", "#pipelineForm :input", function() {
            _isDirty = true
        });

        function isDirty() {
            return _isDirty;
        }
        function allowNavigate() {
            window.onbeforeunload = undefined;
        }

        window.onbeforeunload = LABKEY.beforeunload(isDirty);
        setHelpText();
    }(jQuery);
</script>
<%
    }
%>