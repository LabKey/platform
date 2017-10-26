<%
/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
        dependencies.add("FileUploadField.js");
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
            %><%= button(p.first).href(p.second) %><br>&nbsp;<br><%
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
</style>
<div id="<%=text(errorDivId)%>" class="labkey-error">
<labkey:errors></labkey:errors>&nbsp;
</div>
<div class="panel panel-portal" style="width: 760px;">
    <div class="panel-heading">
        <h3 class="panel-title pull-left">Upload file (.xlsx, .xls, .csv, .txt)</h3>
        <span class="lk-import-expando pull-right">
            <%= PageFlowUtil.button("+").href("#").attributes("id='" + uploadFileDivId + "Expando'") %>
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
            <%=PageFlowUtil.button("&ndash;").textAsHTML(true).href("#").attributes("id='" + copyPasteDivId + "Expando'") %>
        </span>
        <div class="clearfix"></div>
    </div>
    <div class="panel-body">
        <div id="<%=text(copyPasteDivId)%>"></div>
    </div>
</div>
<script type="text/javascript"> (function(){
    var $html = Ext.util.Format.htmlEncode;

    var importTsvDiv = Ext.get(<%=q(copyPasteDivId)%>);
    var uploadFileDiv = Ext.get(<%=q(uploadFileDivId)%>);
    var errorDiv = Ext.get(<%=q(errorDivId)%>);
    var tsvTextarea ;
    var endpoint = <%=q(bean.urlEndpoint)%>;
    var cancelUrl = <%=q(bean.urlCancel)%>;
    var returnUrl = <%=q(bean.urlReturn)%>;
    var successMessageSuffix = <%=q(bean.successMessageSuffix)%>;
    var importTsvForm;
    var uploadFileForm;

    // attach listeners to the buttons
    var importTsvExpando = Ext.get(<%=q(copyPasteDivId+"Expando")%>);
    var uploadTsvExpando = Ext.get(<%=q(uploadFileDivId+"Expando")%>);

    importTsvExpando.parent('div').on('click',function(){toggleExpanded(importTsvExpando,importTsvDiv,uploadTsvExpando,uploadFileDiv);});
    uploadTsvExpando.parent('div').on('click',function(){toggleExpanded(uploadTsvExpando,uploadFileDiv,importTsvExpando,importTsvDiv);});

    function toggleExpanded(toggleButton, toggleDiv, collapseButton, collapseDiv)
    {
        var collapsed = -1!=toggleButton.dom.innerHTML.indexOf("+");
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
        Ext.Msg.show({
            title: "Success",
            msg: msg,
            closable: false
        });
        new Ext.util.DelayedTask(function(){
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

        Ext.getBody().mask();
        errorDiv.update("&nbsp;");

        form.getForm().submit(
        {
            clientValidation : false,
            success: function(form, action)
            {
                Ext.getBody().unmask();
                var msg = null;
                var rowCount;

                if ("msg" in action.result)
                    msg = action.result.msg;
                else if ("rowCount" in action.result)
                {
                    rowCount = action.result.rowCount;
                    msg = rowCount + " row" + (rowCount!=1?"s":"") + " " + successMessageSuffix + ".";
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
                Ext.getBody().unmask();
                switch (action.failureType)
                {
                    case Ext.form.Action.CLIENT_INVALID:
                        Ext.Msg.alert('Failure', 'Form fields may not be submitted with invalid values');
                        break;
                    case Ext.form.Action.CONNECT_FAILURE:
                        if (action.result && (action.result.errors || action.result.exception))
                            serverInvalid(action.result);
                        else
                            Ext.Msg.alert('Failure', 'Ajax communication failed');
                        break;
                    case Ext.form.Action.SERVER_INVALID:
                        serverInvalid(action.result);
                        break;
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
            errors[i] = $html(errors[i]);
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

        if (Ext.isArray(errors))
        {
            for (var i=0 ; i<errors.length ; i++)
                _getGlobalErrors(collection, errors[i], rowNumber);
        }
        else if (errors.errors)
        {
            _getGlobalErrors(collection, errors.errors, rowNumber);
        }

        if (collection.length == count)
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


    function onReady(bean)
    {
        importTsvForm = new Ext.form.FormPanel({
            fileUpload : false,
            labelWidth: 100, // label settings here cascade unless overridden
            url:endpoint,
            title: false, // 'Import text',
            border: false,
            bodyStyle:'padding:5px',
            minWidth:600,
            timeout: Ext.Ajax.timeout,

            items: [
                <%=text(extraFormFields)%>
                {
                    xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF
                },
                {
                    id: <%=q(tsvId)%>,
                    xtype: 'textarea',
                    fieldLabel: 'Data',
                    name: 'text',
                    width:580,
                    height:300
                },
                {
                    fieldLabel: 'Format',
                    xtype: 'combo',
                    store: new Ext.data.ArrayStore({
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
                    width: 250
                },
                {
                    boxLabel: 'Import Lookups by Alternate Key',
                    xtype: 'checkbox',
                    name: 'importLookupByAlternateKey',
                    checked: false,
                    inputValue: "true"
                }
            ],
            buttonAlign:'left',
            buttons: [{
                text: 'Submit', handler:submitFormTsv
            },{
                text: 'Cancel', handler:cancelForm
            }]
        });
        importTsvForm.render(importTsvDiv);
        var resizer = new Ext.Resizable(<%=q(tsvId)%>, {
            wrap:true,
            handles: 'se',
            minWidth: 200,
            minHeight: 100,
            pinned: true
        });
        tsvTextarea = Ext.get(<%=q(tsvId)%>);
        Ext.EventManager.on(tsvTextarea, 'keydown', LABKEY.ext.Utils.handleTabsInTextArea);

        var fibasic = new Ext.form.FileUploadField({
            width: 300,
            hideLabel: true,
            name: 'file',
            buttonText: 'Browse',
            emptyText: 'Select a file to upload'
        });

        uploadFileForm = new Ext.form.FormPanel({
            fileUpload : true,
            url: endpoint,
            title: false, // 'Import text',
            border: false,
            bodyStyle:'padding:5px',
            minWidth:600,
            defaultType: 'textfield',
            timeout: Ext.Ajax.timeout,

            items: [
                    <%=text(extraFormFields)%>
                    {
                        xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF
                    },
                    fibasic,
                    {
                        boxLabel: 'Import Lookups by Alternate Key',
                        xtype: 'checkbox',
                        name: 'importLookupByAlternateKey',
                        checked: false,
                        inputValue: "true",
                        hideLabel: true
                    }
            ],

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

    Ext.onReady(onReady);
})();
</script>
