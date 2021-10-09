<%
/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.query.AbstractQueryImportAction" %>
<%@ page import="org.labkey.api.query.QueryUpdateService" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="static org.labkey.api.util.HtmlString.NDASH" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="static org.labkey.api.query.QueryUpdateService.InsertOption.MERGE" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext4");
    }
%>
<%
    AbstractQueryImportAction.ImportViewBean bean = (AbstractQueryImportAction.ImportViewBean)HttpView.currentModel();
    final String copyPasteDivId = "copypasteDiv" + getRequestScopedUID();
    final String uploadFileDivId = "uploadFileDiv" + getRequestScopedUID();
    String tsvId = "tsv" + getRequestScopedUID();
    String errorDivId = "errorDiv" + getRequestScopedUID();
    String extraFormFields = "";

    if (bean.importMessage != null)
    {
        %><div><%=h(bean.importMessage)%></div><p></p><%
    }

    if (null != bean.extraFields)
    {
        for (JSONObject o : bean.extraFields.toJSONObjectArray())
            extraFormFields += o.toString() + ",\n";
    }


    if (bean.urlExcelTemplates != null && bean.urlExcelTemplates.size() > 0)
    {
        if (bean.urlExcelTemplates.size() == 1)
        {
            Pair<String, String> p = bean.urlExcelTemplates.get(0);
            %><%= button(p.first).href(p.second).usePost() %><br>&nbsp;<br><%
        }
        else
        {
            %>Choose Template: <select id="importTemplate"><%
            for (Pair<String, String> p : bean.urlExcelTemplates)
            {
                %><option value="<%=h(p.second)%>"><%=h(p.first)%></option><%
            }
            %></select>
            <%= button("Download").href("javascript:void(0);").onClick("window.location = document.getElementById('importTemplate').value;") %><br>&nbsp;<br>
            <%
        }
    }%>

<style type="text/css">
    .lk-import-expando .labkey-button {
        padding: 0 5px;
    }

    .x4-field-label-cell a:focus {
        outline: none;
    }
</style>
<div id="<%=text(errorDivId)%>" class="labkey-error">
<labkey:errors/>&nbsp;
</div>
<div class="panel panel-portal" style="width: 760px;">
    <div class="panel-heading">
        <h3 class="panel-title pull-left">Upload file (.xlsx, .xls, .csv, .txt)</h3>
        <span class="lk-import-expando pull-right">
            <%= button("+").href("#").id(uploadFileDivId + "Expando") %>
        </span>
        <div class="clearfix"></div>
    </div>
    <div class="panel-body">
        <div id="<%=text(uploadFileDivId)%>"></div>
    </div>
</div>
<div class="panel panel-portal" style="width: 760px;">
    <div class="panel-heading">
        <h3 class="panel-title pull-left">Copy/paste text</h3>
        <span class="lk-import-expando pull-right">
            <%=button(NDASH).href("#").id(copyPasteDivId + "Expando") %>
        </span>
        <div class="clearfix"></div>
    </div>
    <div class="panel-body">
        <div id="<%=unsafe(copyPasteDivId)%>"></div>
    </div>
</div>
<script type="text/javascript"> (function(){
    var importTsvDiv = Ext4.get(<%=q(copyPasteDivId)%>);
    var uploadFileDiv = Ext4.get(<%=q(uploadFileDivId)%>);
    var errorDiv = Ext4.get(<%=q(errorDivId)%>);
    var tsvTextarea ;
    var type = <%=q(bean.typeName)%>;
    var helpTopic = <%=q(bean.importHelpTopic)%>;
    var helpDisplayText = <%=q(bean.importHelpDisplayText)%>;
    var endpoint = <%=q(bean.urlEndpoint)%>;
    var cancelUrl = <%=q(bean.urlCancel)%>;
    var returnUrl = <%=q(bean.urlReturn)%>;
    var successMessageSuffix = <%=q(bean.successMessageSuffix)%>;
    var importTsvForm;
    var uploadFileForm;

    // attach listeners to the buttons
    var importTsvExpando = Ext4.get(<%=q(copyPasteDivId+"Expando")%>);
    var uploadTsvExpando = Ext4.get(<%=q(uploadFileDivId+"Expando")%>);

    importTsvExpando.parent('div').on('click',function(){toggleExpanded(importTsvExpando,importTsvDiv,uploadTsvExpando,uploadFileDiv);});
    uploadTsvExpando.parent('div').on('click',function(){toggleExpanded(uploadTsvExpando,uploadFileDiv,importTsvExpando,importTsvDiv);});

    function toggleExpanded(toggleButton, toggleDiv, collapseButton, collapseDiv)
    {
        var collapsed = -1 !== toggleButton.dom.innerHTML.indexOf("+");
        toggleButton.dom.innerHTML = collapsed ? "&ndash;" : "+";
        toggleDiv.parent().setStyle("display",collapsed?"block":"none");

        collapseButton.dom.innerHTML = "+";
        collapseDiv.parent().setStyle("display","none");
    }

    function cancelForm()
    {
        window.location = cancelUrl;
    }

    function submitFormTsv()
    {
        submitForm(importTsvForm);
    }

    function submitFormUpload()
    {
        submitForm(uploadFileForm);
    }

    function showSuccessMessage(msg)
    {
        Ext4.Msg.show({
            title: "Success",
            msg: msg,
            closable: false
        });
        new Ext4.util.DelayedTask(function(){
            window.location = returnUrl;
        }).delay(1500);
    }

    function showError() {
        serverInvalid({errors: {_form: "No rows were inserted. Please check to make sure your data is formatted properly."}});
        LABKEY.Utils.signalWebDriverTest('importFailureSignal');
    }

    function submitForm(form)
    {
        if (!form)
            return;

        Ext4.getBody().mask();
        errorDiv.update("&nbsp;");

        form.getForm().submit(
        {
            clientValidation : false,
            success: function(form, action)
            {
                Ext4.getBody().unmask();
                var msg = null;
                var rowCount;

                if ("msg" in action.result)
                    msg = action.result.msg;
                else if ("rowCount" in action.result)
                {
                    rowCount = action.result.rowCount;
                    msg = rowCount + " row" + (rowCount !==1 ? "s" : "") + " " + successMessageSuffix + ".";
                }

                if (msg && "rowCount" in action.result && action.result.rowCount > 0)
                {
                    showSuccessMessage(msg);
                }
                else if("rowCount" in action.result && action.result.rowCount <= 0)
                {
                    <%
                    if (bean.acceptZeroResults) {%>
                    if (rowCount == 0)
                        showSuccessMessage("Upload successful, but 0 updates occurred");
                    else {
                        showError();
                    }
                    <%
                    } else {
                    %>
                    showError();
                    <%
                    }
                    %>
                }
                else
                    window.location = returnUrl;
            },
            failure: function(form, action)
            {
                Ext4.getBody().unmask();
                switch (action.failureType)
                {
                    case Ext4.form.Action.CLIENT_INVALID:
                        Ext4.Msg.alert('Failure', 'Form fields may not be submitted with invalid values');
                        break;
                    case Ext4.form.Action.CONNECT_FAILURE:
                        if (action.result && (action.result.errors || action.result.exception))
                            serverInvalid(action.result);
                        else if (action.response && action.response.responseText)
                            serverInvalid(Ext4.decode(action.response.responseText));
                        else
                            Ext4.Msg.alert('Failure', 'Ajax communication failed');
                        break;
                    case Ext4.form.Action.SERVER_INVALID:
                        serverInvalid(action.result);
                        break;
                }
                LABKEY.Utils.signalWebDriverTest('importFailureSignal');
            }
        });
    }


    // extra processing for server errors
    function serverInvalid(result)
    {
        if (result.exception)
            console.log(result.exception);
        if (result.stackTrace)
            console.log(result.stackTrace);

        var formMsg = getGlobalErrorHtml(result);
        errorDiv.update(formMsg);
    }


    /* our error reporting is complicated for multi-row import,
     * try to consolidate into one useful display string
     */
    function getGlobalErrorHtml(result)
    {
        var errors = _getGlobalErrors([], result);
        for (var i=0 ; i<errors.length ; i++)
            errors[i] = LABKEY.Utils.encodeHtml(errors[i]);
        if (errors.length > 20)
        {
            var total = errors.length;
            errors = errors.slice(0,19);
            errors.push("... total of " + total + " errors");
        }

        return errors.join("<br>");
    }


    function _getGlobalErrors(collection, errors, rowNumber)
    {
        rowNumber = errors.rowNumber || rowNumber;
        var count = collection.length;

        if (errors["msg"] || errors["_form"])
        {
            var err = errors["exception"] || errors["msg"] || errors["_form"];
            if (rowNumber)
                err = "row " + rowNumber + ": " + err;
            collection.push(err);
        }

        if (Ext4.isArray(errors))
        {
            for (var i=0 ; i<errors.length ; i++)
                _getGlobalErrors(collection, errors[i], rowNumber);
        }
        else if (errors.errors)
        {
            _getGlobalErrors(collection, errors.errors, rowNumber);
        }

        if (collection.length === count)
        {
            // don't want to double up messages, so ignore errors.exception unless there are no other messages
            if (errors["exception"])
            {
                collection.push(errors.exception);
            }
            else
            {
                for (var p in errors) {
                    if (errors.hasOwnProperty(p))
                        collection.push(errors[p]);
                }
            }
        }

        return collection;
    }

    function getImportOptions(index)
    {
        <%
        if (bean.showImportOptions) {
        %>
            return [{
                xtype: 'checkbox',
                id:'insertOption' + index,
                itemId: 'insertOption',
                name: 'insertOption',
                inputValue: <%=q(QueryUpdateService.InsertOption.MERGE.name())%>,
                fieldLabel: 'Import Options',
                boxLabel: 'Update data for existing ' + type + ' during import. ' +
                        ((!helpTopic||!helpDisplayText) ? "" : <%=q(new HelpTopic(bean.importHelpTopic).getLinkHtml(bean.importHelpDisplayText))%>),
                preventMark: true,
                helpPopup: LABKEY.Utils.encodeHtml(
                    '<p>By default, import will insert new rows based on the data/file provided. The operation will fail ' +
                    'if there are existing row identifiers that match those being imported.</p>' +
                    '<p>When update is selected, data will be updated for matching row identifiers, and new rows will be created for any that do not match.' +
                    ' Data will not be changed for any columns not in the imported data/file.</p>'
                ),
                columns: 1,
                defaults: {
                    xtype: 'radio',
                    name: 'insertOption'
                },
                items: [
                    {
                        boxLabel: 'Insert',
                        inputValue: 'IMPORT',
                        checked: true
                    },
                    {
                        boxLabel: 'Insert and Replace',
                        inputValue: 'MERGE'
                    }
                ]
            }];
        <%
        }
        else {
        %>
            return [];
        <%
        }
        %>
    }


    function onReady(bean)
    {
        Ext4.QuickTips.init();
        importTsvForm = new Ext4.form.FormPanel({
            fileUpload : false,
            defaults: {
                labelWidth: 110, // label settings here cascade unless overridden
            },
            url:endpoint,
            title: false, // 'Import text',
            border: false,
            bodyStyle:'padding:5px',
            minWidth:600,
            timeout: Ext4.Ajax.timeout,

            items: getImportOptions(1).concat([
                <%=unsafe(extraFormFields)%>
                {
                    xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF
                },
                {
                    inputId: <%=q(tsvId)%>,
                    xtype: 'textarea',
                    fieldLabel: 'Data',
                    name: 'text',
                    width:580,
                    height:300
                },
                {
                    fieldLabel: 'Format',
                    xtype: 'combo',
                    store: new Ext4.data.ArrayStore({
                        fields: ['id', 'value'],
                        data:
                        [
                            ['tsv', 'Tab-separated text (tsv)'],
                            ['csv', 'Comma-separated text (csv)']
                        ]
                    }),
                    hidden: <%=bean.hideTsvCsvCombo%>,
                    mode: 'local',
                    hiddenName: 'format',
                    name: 'format',
                    valueField: 'id',
                    displayField: 'value',
                    triggerAction: 'all',
                    value: 'tsv',
                    width: 350
                },
                {
                    hideEmptyLabel: false,
                    boxLabel: 'Import Lookups by Alternate Key',
                    labelPad:8,
                    xtype: 'checkbox',
                    name: 'importLookupByAlternateKey',
                    checked: false,
                    inputValue: "true",
                }
            ]),
            buttonAlign:'left',
            buttons: [{
                text: 'Submit', handler:submitFormTsv
            },{
                text: 'Cancel', handler:cancelForm
            }]
        });
        importTsvForm.render(importTsvDiv);
        new Ext4.Resizable(<%=q(tsvId)%>, {
            wrap:true,
            handles: 'se',
            minWidth: 200,
            minHeight: 100,
            pinned: true
        });
        tsvTextarea = Ext4.get(<%=q(tsvId)%>);
        Ext4.EventManager.on(tsvTextarea, 'keydown', LABKEY.Utils.handleTabsInTextArea);

        uploadFileForm = new Ext4.form.Panel({
            defaults: {
                labelWidth: 110, // label settings here cascade unless overridden
            },
            fileUpload : true,
            url: endpoint,
            title: false, // 'Import text',
            border: false,
            bodyStyle:'padding:5px',
            minWidth:600,
            defaultType: 'textfield',
            timeout: Ext4.Ajax.timeout,

            items: getImportOptions(0).concat([
                    <%=unsafe(extraFormFields)%>
                    {
                        xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF
                    },
                    {
                        xtype: 'filefield',
                        width: 400,
                        fieldLabel: 'File to Import',
                        labelPad:16,
                        name: 'file',
                        buttonText: 'Browse',
                        emptyText: 'Select a file to upload',
                        clearOnSubmit: false
                    },
                    {
                        hideEmptyLabel: false,
                        boxLabel: 'Import Lookups by Alternate Key',
                        labelPad:8,
                        xtype: 'checkbox',
                        name: 'importLookupByAlternateKey',
                        checked: false,
                        inputValue: "true",
                    }
            ]),

            buttonAlign:'left',
            buttons: [{
                text: 'Submit', handler:submitFormUpload
            },{
                text: 'Cancel', handler:cancelForm
            }]
        });
        uploadFileForm.render(uploadFileDiv);
        uploadFileDiv.parent().setStyle("display","none");
    }

    Ext4.onReady(onReady);
})();
</script>
