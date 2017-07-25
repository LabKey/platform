/*
 * Copyright (c) 2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.pipeline.AnalyzeForm', {
    extend: 'Ext.panel.Panel',

    bodyStyle: 'background-color: transparent;',
    border: false,

    taskId: null,
    path: null,
    fileNames: null,
    allProtocols: null,

    constructor: function(config) {
        this.callParent([config]);
        this.addEvents('showFileStatus');
    },

    initComponent: function() {
        this.allProtocols = {};
        if (this.fileNames == null) {
            this.fileNames = [];
        }

        this.items = [this.getFormTableView()];
        this.callParent();
    },

    getFormTableView: function() {
        if (!this.formTableView) {
            var tpl = new Ext4.XTemplate(
                '<table>',
                    '<tr>',
                        '<td class="labkey-form-label" width="150">Analysis Protocol:</td>',
                        '<td>',
                            '<select id="protocolSelect" name="protocol">',
                                '<option>&lt;Loading...&gt;</option>',
                            '</select>',
                            '<span id="protocolName">',
                                '<label for="protocolNameInput" >&nbsp;Name:</label>',
                                '<input disabled id="protocolNameInput" class="protocol-input" type="text" name="protocolName" size="40" />',
                            '</span>',
                        '</td>',
                    '</tr>',
                    '<tr>',
                        '<td class="labkey-form-label">Protocol Description:</td>',
                        '<td>',
                            '<textarea disabled id="protocolDescriptionInput" class="protocol-input" style="width: 100%;" name="protocolDescription" cols="150" rows="4"></textarea>',
                        '</td>',
                    '</tr>',
                    '<tr>',
                        '<td class="labkey-form-label">File(s):</td>',
                        '<td id="fileStatus" />',
                    '</tr>',
                    '<tr id="parametersRow">',
                        '<td class="labkey-form-label">Parameters:</td>',
                        '<td>',
                            '<textarea id="xmlParametersInput" class="protocol-input" style="width: 100%;" name="xmlParameters" cols="150" rows="15"></textarea>',
                        '</td>',
                    '</tr>',
                    '<tr id="extraSettings" style="display: none;"/>',
                    '<tr>',
                        '<td colspan="2">',
                            '<input type="checkbox" class="protocol-input" disabled id="saveProtocolInput" name="saveProtocol" checked/>',
                            '<label for="saveProtocolInput">Save protocol for future use</label>',
                        '</td>',
                    '</tr>',
                '</table>'
            );

            this.formTableView = Ext4.create('Ext.view.View', { tpl: tpl });
            this.formTableView.on('refresh', this.attachTextAreaCodeMirror, this, {single: true});
            this.formTableView.on('refresh', this.attachProtocolSelectChangeListener, this, {single: true});
            this.formTableView.on('refresh', this.getPipelineProtocols, this, {single: true});
        }

        return this.formTableView;
    },

    attachProtocolSelectChangeListener: function() {
        var el = Ext4.get('protocolSelect');
        if (el) {
            el.on('change', function(evt, selEl) {
                var selectedProtocolName = selEl.options[selEl.selectedIndex].value;
                if (!this.changeProtocol(selectedProtocolName)) {
                    this.getElementById('protocolNameInput').focus();
                }
            }, this);
        }
    },

    getPipelineProtocols: function() {
        this.showFileStatus("<em>(Refreshing status)</em>");

        LABKEY.Pipeline.getProtocols({
            taskId: this.taskId,
            scope: this,
            successCallback: this.getProtocolsCallback
        });
    },

    getProtocolsCallback: function(protocols, defaultProtocolName) {
        var selectElement = this.getElementById("protocolSelect");
        selectElement.options[0].text = "<New Protocol>";
        this.allProtocols = {};
        var defaultProtocolIndex = -1;
        for (var i = 0; i < protocols.length; i++) {
            selectElement.options[i + 1] = new Option(protocols[i].name, protocols[i].name, protocols[i].name == defaultProtocolName);
            this.allProtocols[protocols[i].name] = protocols[i];
            if (protocols[i].name == defaultProtocolName) {
                defaultProtocolIndex = i + 1;
            }
        }

        if (this.changeProtocol(defaultProtocolName)) {
            selectElement.selectedIndex = defaultProtocolIndex;
            selectElement.focus();
        }
        else {
            this.getElementById("protocolNameInput").focus();
        }
    },

    /** @return true if an existing, saved protocol is selected */
    changeProtocol: function(selectedProtocolName) {
        var selectedProtocol = this.allProtocols[selectedProtocolName];
        var disabledState;
        if (selectedProtocol) {
            disabledState = true;
            this.getElementById("protocolNameInput").value = selectedProtocol.name;
            this.getElementById("protocolDescriptionInput").value = selectedProtocol.description;
            this.codeMirror.setValue(selectedProtocol.xmlParameters);
            this.showFileStatus("<em>(Refreshing status)</em>");
            LABKEY.Pipeline.getFileStatus(
            {
                taskId: this.taskId,
                path: this.path,
                files: this.fileNames,
                protocolName: selectedProtocolName,
                scope: this,
                successCallback: this.showFileStatus
            });
        }
        else {
            disabledState = false;
            this.getElementById("protocolNameInput").value = "";
            this.getElementById("protocolDescriptionInput").value = "";
            this.codeMirror.setValue(
                "<?xml version=\"1.0\"?>\n" +
                "<bioml>\n" +
                "  <!-- Override default parameters here. Example:-->\n" +
                "  <!-- <note label=\"myParameterName\" type=\"input\">overrideValue</note>-->\n" +
                "</bioml>"
            );
            this.showFileStatus("", "Analyze");
        }

        var inputs = Ext4.DomQuery.select("[@class=protocol-input]");
        for (var i = 0; i < inputs.length; i++) {
            inputs[i].disabled = disabledState;
        }

        this.resetStyles(disabledState);
        return disabledState;
    },

    resetStyles: function(disabled) {
        document.getElementsByClassName("CodeMirror")[0].style.backgroundColor = window.getComputedStyle(this.getElementById("protocolNameInput")).backgroundColor;
        this.codeMirror.setOption("readOnly", disabled);
        this.getElementById("protocolName").style.visibility = disabled ? "hidden" : "visible";
    },

    attachTextAreaCodeMirror: function() {
        var el = Ext4.get("xmlParametersInput");
        if (el) {
            this.codeMirror = CodeMirror.fromTextArea(el.dom, {
                mode: "xml",
                lineNumbers: true
            });
            this.codeMirror.setSize(null, '200px');
            LABKEY.codemirror.RegisterEditorInstance('xmlParameters', this.codeMirror);
            document.getElementsByClassName("CodeMirror")[0].style.border = window.getComputedStyle(this.getElementById("protocolDescriptionInput")).border;
        }
    },

    /** @param statusInfo is either a string to be shown for all files, or an array with status information for each file */
    showFileStatus: function(statusInfo, submitType)
    {
        var globalStatus = "";
        var files = [];
        if (typeof statusInfo === 'string') {
            files = [];
            globalStatus = statusInfo;
        }
        else if (statusInfo && statusInfo.length) {
            // Assume it's an array
            files = statusInfo;
        }

        var status = "";
        for (var i = 0; i < this.fileNames.length; i++) {
            status = status + this.fileNames[i];
            for (var j = 0; j < files.length; j++) {
                if (this.fileNames[i] == files[j].name) {
                    if (files[j].status) {
                        status += " <b>(" + files[j].status + ")</b>";
                    }
                    break;
                }
            }
            status += " " + globalStatus + "<br/>";
        }
        this.getElementById("fileStatus").innerHTML = status;

        this.fireEvent('showFileStatus', this, submitType);
    },

    getXmlParametersValue: function() {
        return this.codeMirror.getValue();
    },

    getElementById: function(id) {
        return Ext4.dom.Query.selectNode('*[id=' + id + ']', this.getEl().dom);
    }
});