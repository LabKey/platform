<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.di.EtlDef" %>
<%@ page import="org.labkey.di.view.DataIntegrationController" %>
<%@ page import="java.util.Collections" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("codemirror");
    }
%>
<%
    JspView<DataIntegrationController.EtlDefinitionForm> me =
            (JspView<DataIntegrationController.EtlDefinitionForm>) HttpView.currentView();
    DataIntegrationController.EtlDefinitionForm form = me.getModelBean();
    EtlDef def = form.getBean();
    boolean newDef = true;
    boolean enabled = false;
    if (null != def.getName())
    {
        newDef = false;
        if (!form.isReadOnly())
        {
            enabled = !DataIntegrationController.getEnabledTransformIds(getContainer(), Collections.singleton(def)).isEmpty();
        }
    }
    ActionURL cancelURL = form.getReturnURLHelper() != null ? form.getReturnActionURL() : getContainer().getStartURL(getUser());
    boolean isAdmin = getViewContext().hasPermission(AdminPermission.class);
    List<Container> containerList = DataIntegrationController.getContainersWithEtlDefinitions(getViewContext());
    StringBuilder optionHtml = new StringBuilder();
    for (Container c : containerList)
    {
        optionHtml.append("<option value=\"")
                .append(c.getPath())
                .append("\">");
        String s = c.getParsedPath().toString().substring(1, c.getParsedPath().toString().lastIndexOf("/"));
        optionHtml.append(c.getId().equals(getContainer().getId()) ? s + "   (Current) " : s)
                .append("</option>");
    }
    String sampleXml =
            "<etl xmlns=\"http://labkey.org/etl/xml\">\n" +
            "  <name>Add name</name>\n" +
            "  <description>Add description</description>\n" +
            "  <transforms>\n" +
            "    <!--<transform id=\"step1\" type=\"org.labkey.di.pipeline.TransformTask\">\n" +
            "      <description>Copy to target</description>\n" +
            "      <source schemaName=\"etltest\" queryName=\"source\" />\n" +
            "      <destination schemaName=\"etltest\" queryName=\"target\" />\n" +
            "    </transform>-->\n" +
            "  </transforms>\n" +
            "  <incrementalFilter className=\"ModifiedSinceFilterStrategy\" timestampColumnName=\"modified\"/>\n" +
            "  <schedule>\n" +
            "    <poll interval=\"1h\" />\n" +
            "  </schedule>\n" +
            "</etl>\n";
%>
<script type="text/javascript">
    var _etlform, definitionEditor;
    jQuery(document).ready(function() {
        +function($) {
            var templateModalFn = function () {

                var templateXmlDefs = {};

                var html = [
                        '<div>',
                            '<label class="control-label" for="template-container-select">Select location:</label>',
                            '<select style="margin: 8px" class="form-control" id="template-container-select">',
                                '<option disabled selected value style="display: none">-- choose a folder --</option>',
                                <%=q(optionHtml.toString())%>,
                            '</select>',
                        '</div>',
                        '<div id="template-select-ct" style="margin-bottom: 30px">',
                            '<label class="control-label" for="template-select">Select ETL Definition:</label>',
                            '<select style="margin: 8px" class="form-control" id="template-select"></select>',
                            '<div id="template-message-ct" style="float: left; padding-top: 5px; padding-left: 8px; color: #d9534f"></div>',
                            '<a class="btn btn-default" style="float: right" id="template-cancel-btn">Cancel</a>' +
                            '<a disabled class="btn btn-primary" style="float: right; margin-right: 8px"id="template-apply-btn">Apply</a>',
                        '</div>'
                ].join('');

                $('#modal-fn-body').html(html);

                $('#template-cancel-btn').on('click', function() {
                    $('#lk-utils-modal').modal('hide');
                });

                var applyBtn = $('#template-apply-btn');
                applyBtn.on('click', function() {
                    var selectedTemplate = $('#template-select').val();
                    var confirmText = 'Are you sure? Unsaved changes will be lost.';
                    var msg = $('#template-message-ct');
                    if (!selectedTemplate) {
                        msg.html('Please select a location and definition to copy from.');
                        return;
                    }

                    if (msg.html() === confirmText) {
                        applyBtn.html("Apply");
                        msg.html('');
                    } else if (_etlform.isDirty() && selectedTemplate !== undefined && selectedTemplate !== null) {
                        applyBtn.html("Yes, apply");
                        msg.html(confirmText);
                        return; //Force additional confirmation
                    }
                    definitionEditor.setValue(templateXmlDefs[selectedTemplate]);
                    _etlform.setClean();
                    $('#lk-utils-modal').modal('hide');
                });

                $('#template-container-select').on('change', function() {
                    $('#template-select').html('').append($('<option disabled selected value style="display: none">-- choose a definition --</option>'));
                    LABKEY.Query.selectRows({
                        schemaName: 'dataintegration',
                        queryName: 'EtlDef',
                        containerPath: this.value,
                        columns: ['EtlDefId', 'Name', 'Definition'],
                        success: function (data) {
                            var s = '';
                            data.rows.forEach(function(row) {
                                templateXmlDefs[row.EtlDefId] = row.Definition;
                                s += '<option value="' + row.EtlDefId + '">' + row.Name + '</option>';
                            });
                            $('#template-select').append($(s));
                        },
                        failure: function (err) {
                            $('#template-message-ct').html(err.exception);
                        }
                    })
                });
                $('#template-select').on('change', function(){
                    applyBtn.removeAttr('disabled');
                });
            };

            var changeNameModalFn = function() {
                var html = [
                    '<div style="margin-bottom: 20px;">',
                        '<div id="name-change-message-ct">',
                            'The definition name has changed. Do you want to update the existing definition or save as a new definition?',
                        '</div>',
                        '<a class="btn btn-default _etl-name-button" style="float: right" id="name-change-saveAs-btn">Save as New</a>',
                        '<a class="btn btn-primary _etl-name-button" style="float: right; margin-right: 8px" id="name-change-update-btn">Update Existing</a>',
                    '</div>'
                ].join('');

                $('#modal-fn-body').html(html);

                $('#name-change-update-btn').on('click', function() {
                    $('#confirmNameChange').val(true);
                    $('#etlDefinitionForm').submit();
                });

                $('#name-change-saveAs-btn').on('click', function() {
                    $('#confirmNameChange').val(true);
                    $('#saveAsNew').attr("name", "saveAsNew").val(true);
                    $('#etlDefinitionForm').submit();
                });
            };

            _etlform = new LABKEY.Form({
                formElement: 'etlDefinitionForm'
            });

            if (<%=form.hasNameConflict()%>) {
                LABKEY.Utils.alert("Definition Name Conflict", 'This definition name is already in use in the current folder. Please specify a different name. ');
            }

            else if (<%=!newDef%> && <%=form.hasNameChanged()%>) {
                LABKEY.Utils.modal('Definition Name Changed', null, changeNameModalFn, null);
            }

            $('#chooseTemplateButton').on('click', function() {
                LABKEY.Utils.modal('Copy from Existing Definition', null, templateModalFn, null);
            });

            var definition = document.getElementById('definition');
            definitionEditor = CodeMirror.fromTextArea(definition, {
                mode: "xml",
                lineNumbers: true,
                lineWrapping: true,
                autofocus: true
            });
            LABKEY.codemirror.RegisterEditorInstance('etlDefinition', definitionEditor);
            if (<%=form.isReadOnly()%>) {
                definitionEditor.options.readOnly = "nocursor";
                definitionEditor.options.viewportMargin = "Infinity";
                document.getElementsByClassName("CodeMirror")[0].style.backgroundColor = window.getComputedStyle(document.getElementById('etlDef')).backgroundColor;
            }
            else {
                definitionEditor.setSize(1200, 500);
                definitionEditor.on('change', function () {
                    _etlform.setDirty();
                })
            }
            document.getElementsByClassName("CodeMirror")[0].style.border = window.getComputedStyle(definition).border;

        }(jQuery);
    });
</script>
<style type="text/css">
    .CodeMirror {
        height: auto;
        width: 1200px;
    }
</style>

<h4 id="name"><%=h(newDef ? "New Definition" : "Definition: \"" + def.getName() + "\"")%></h4>
<% if (enabled) { %>
    <p>Warning: This ETL has been enabled and is scheduled to run. Modifying it will unschedule it from future runs, and it must be reenabled.</p>
<% } %>
<% if (!form.isReadOnly()) {%>
<button type="button" id="chooseTemplateButton" class="labkey-button" data-toggle="modal" data-target="#templateModal" style="margin-bottom: 10px">Copy from Existing</button>
<% }
String xml = sampleXml;
if (def.getDefinition() != null)
    xml = def.getDefinition();
if (form.getPostedDefinition() != null)
    xml = form.getPostedDefinition();
%>

<labkey:errors/>
<labkey:form id="etlDefinitionForm" method="post">
    <labkey:input name="oldName" type="hidden" id="oldNameInput" value="<%=h(form.getBean().getOldName())%>"/>
    <labkey:input name="confirmNameChange" type="hidden" id="confirmNameChange" value="false"/>
    <labkey:input type="hidden" id="saveAsNew" />
    <table id="etlDef">
        <tr>
            <td colspan="2">
                <textarea name="definition" id="definition" cols="80" rows="30"><%=text(xml)%></textarea>
            </td>
        </tr>
    </table>
    <br/>
    <% String cancelText = "Cancel";
    if (form.isReadOnly()) {
        cancelText = "Show Grid";
        ActionURL editUrl = new ActionURL(DataIntegrationController.DefineEtlAction.class, getContainer());
        editUrl.addParameter("etlDefId", def.getEtlDefId());
        editUrl.addReturnURL(cancelURL);
        if (isAdmin) { %>
            <labkey:button text="Edit" id="editButton" href="<%=editUrl%>"/>
        <% } %>
    <% } else if (isAdmin){%> <%-- Should always be admin to get here, but just to be safe --%>
    <labkey:button text="Save" id="etlSubmitButton" submit="true" onclick="_etlform.setClean()"/>
    <% } %>
    <labkey:button text="<%=h(cancelText)%>" href="<%= cancelURL %>" onclick="_etlform.setClean()"/>
</labkey:form>