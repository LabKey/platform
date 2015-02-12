/*
 * Copyright (c) 2008-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

(function($) {

    var _idPrefix = 'tour-';
    var _idSel = '#' + _idPrefix;
    var _steps = 1;

    //var _idTitle = _idPrefix + 'title';
    //var _idDescription = _idPrefix + 'description';
    //var _idModeSelector = _idPrefix + 'mode';
    var _idSelector = _idPrefix + 'selector';

    var _rowClass = "col-width row";
    var _labelClass = "label";
    var _inputClass = "input";
    var _textAreaRows = "10";
    var _textAreaCols = "65";

    var _rowId = "";
    var _stepHandles = {};

    Ext4.onReady( function()
    {
        Ext4.define('LABKEY.tour.TourJsonPanel', {
            extend: 'LABKEY.vis.BaseExportScriptPanel',

            SCRIPT_TEMPLATE:
            "{\n" +
            "\"Title\":\"{{tourTitle}}\",\n" +
            "\"Description\":\"{{tourDescription}}\",\n" +
            "\"Mode\":\"{{tourMode}}\",\n" +
            "\"Tour\":\n\"{{tourJson}}\"\n" +
            "}\n",

            codeMirrorMode: {name: "javascript", json: true},

            compileTemplate: function(input) {
                return this.SCRIPT_TEMPLATE
                        .replace('{{tourTitle}}', $(_idSel + 'title').val())
                        .replace('{{tourDescription}}', $(_idSel + 'description').val())
                        .replace('{{tourMode}}', $(_idSel + 'mode').val())
                        .replace('{{tourJson}}', _formatExport($("#dummy-hidden").val()));
            }
        });

        Ext4.define('LABKEY.tour.EditTour', {
            extend : 'Ext.panel.Panel',
            export: function ()
            {
                if (!this.exportTourWindow)
                {
                    this.editorExportTourPanel = Ext4.create('LABKEY.tour.TourJsonPanel', {
                        listeners: {
                            scope: this,
                            closeOptionsWindow: function ()
                            {
                                this.exportTourWindow.hide();
                            }
                        }
                    });

                    this.exportTourWindow = Ext4.create('Ext.window.Window', {
                        title: "Export Tour",
                        cls: 'data-window',
                        border: false,
                        frame: false,
                        modal: true,
                        width: 800,
                        resizable: false,
                        closeAction: 'hide',
                        items: [this.editorExportTourPanel]
                    });
                }
                this.exportTourWindow.show();
            }
        });
    });

    var _init = function ()
    {
        _bindControls();

        _generateMode(document.getElementById("leftcolumn"), document.getElementById("mode-dummy"));

        var dummy = document.getElementById("dummy");
        var parent = document.getElementById("rightcolumn");

        var stepArray = [];
        if(document.getElementById("dummy-hidden").value != "")
            stepArray = document.getElementById("dummy-hidden").value.split("}\",\"");

        var maxSteps = 3
        if( stepArray.length > maxSteps)
            maxSteps = stepArray.length;

        while(_steps <= maxSteps)
        {
            if(stepArray.length >= _steps)
                _generateStep(_steps, parent, dummy, stepArray[_steps - 1]);
            else
                _generateStep(_steps, parent, dummy, null);

            _steps++;
        }

        if(document.getElementById("tour-rowid").value != "")
            _rowId = document.getElementById("tour-rowid").value;
    }

    var _saveClose = function ()
    {
        _submit(true);
    }

    var _save = function ()
    {
        _submit(false);
    }

    var _submit = function(close)
    {
        var json = {};
        var tour = {};
        var steps = [];

        for(var i = 1; i<_steps; i++)
        {
            var obj = {};

            var target = $(_idSel + 'selector' + i).val();
            if (target !== undefined && target != "")
            {
                var step = _stepHandles[_idPrefix + 'step' + i].getValue();
                if (step !== undefined && step != "")
                {
                    obj["target"] = $(_idSel + 'selector' + i).val();

                    $.each(JSON.parse('{' + step + '}'), function (key, value)
                    {
                        obj[key] = value;
                    });

                    steps.push(obj);
                }
            }
        }

        tour["id"] = $(_idSel + 'title').val();
        tour["steps"] = steps;

        json["title"] = $(_idSel + 'title').val();
        json["description"] = $(_idSel + 'description').val();
        json["mode"] = $(_idSel + 'mode').val();
        json["rowId"] = _rowId;
        json["tour"] = tour;

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('tours', 'saveTour'),
            jsonData : json,
            //method : 'POST',
            success: LABKEY.Utils.getCallbackWrapper(function(result) { _success.call(this, close, result); }, this, false),
            //failure: LABKEY.Utils.getCallbackWrapper(function(result) { failure.call(this, id, result); }, me, false),
            scope: this
        });
    }

    var _success = function(close, result)
    {
        if(close)
            _cancel();
        else
            _rowId = result["rowId"];
    }

    var _cancel = function ()
    {
        window.location = LABKEY.ActionURL.buildURL('tours', 'begin.view')
    }

    var _clear = function ()
    {
        $(_idSel + 'title').val('');
        $(_idSel + 'description').val('');
        for(var i=1; i<_steps; i++)
        {
            $(_idSel + 'selector' + i).val('');
            _stepHandles[_idPrefix + 'step' + i].setValue('');
        }
    }

    var _importTour = function ()
    {
        var test = 1;
        test++;
    }

    var _exportTour = function ()
    {
        LABKEY.tour.EditTour.create().export();
    }

    var _addStep = function ()
    {
        var dummy = document.getElementById("dummy");
        var parent = document.getElementById("rightcolumn");

        _generateStep(_steps++, parent, dummy);
    }

    /**
     * Binds all events to the form attributes of the editor.
     * @param props
     */
    var _bindControls = function ()
    {
        // form buttons
        $(_idSel + 'button-save-close').click(_saveClose);
        $(_idSel + 'button-save').click(_save);
        $(_idSel + 'button-cancel').click(_cancel);
        $(_idSel + 'button-clear').click(_clear);
        $(_idSel + 'button-import').click(_importTour);
        $(_idSel + 'button-export').click(_exportTour);
        $(_idSel + 'button-add-step').click(_addStep);

    };


    var _generateMode = function(parent, element)
    {
        var values = ["Off", "Run Once", "Run Always"];

        var rowMode = document.createElement("div");
        rowMode.setAttribute("class", _rowClass);

        var divLabelMode = document.createElement("div");
        rowMode.appendChild(divLabelMode);

        var labelMode = document.createElement("label");
        labelMode.setAttribute("class", _labelClass);
        labelMode.setAttribute("for", _idPrefix + "mode");
        labelMode.appendChild(document.createTextNode("Mode"));
        divLabelMode.appendChild(labelMode);

        var divSelectMode = document.createElement("div");
        rowMode.appendChild(divSelectMode);

        var selectMode = document.createElement("select");
        selectMode.setAttribute("id", "tour-mode");
        selectMode.setAttribute("class", "select");
        divSelectMode.appendChild(selectMode);

        for (var i=0; i<values.length; i++)
        {
            var option = document.createElement("option");
            option.value = i;
            option.text = values[i];
            if(parseInt(document.getElementById("mode-dummy-hidden").value) == i )
                option.selected = true;
            selectMode.appendChild(option);
        }

        parent.insertBefore(rowMode, element);
    }

    var _regCodeMirrorEditor = function(id, value)
    {
        var editor = CodeMirror.fromTextArea(document.getElementById(id), {
            mode: {name: 'javascript', json: true},
            lineNumbers: true,
            lineWrapping: true,
            indentUnit: 0
        });

        editor.setSize(474, 150)
        if(value)
            editor.setValue(_formatStep(value));

        LABKEY.codemirror.RegisterEditorInstance(id, editor);
        _stepHandles[id] = editor;
    }

    var _formatGeneric = function(value)
    {
        value = value.replace(/\\/g, "");
        value = value.replace(/,/g, ",\n");
        value = value.replace(/""/g, "\"");
        return value;
    }

    var _formatExport = function(value)
    {
        value = _formatGeneric(value);
        value = value.replace(/}"[^},]/g, "}");
        value = value.replace(/"{/g, "{");
        return value;
    }

    var _formatStep = function(value)
    {
        value = _formatGeneric(value);
        value = value.replace(/"}"}/g, "\"");
        return value;
    }

    var _formatSelector = function(value)
    {
        value = value.replace(/"/g, "");
        value = value.replace(/}/g, "");
        value = value.replace(/{/g, "");
        return value;
    }

    var _generateStep = function(index, parent, element, stepInfo)
    {
        var step;
        if(stepInfo)
            step = stepInfo.split("\":\"{");

        var rowSelector = document.createElement("div");
        rowSelector.setAttribute("class", _rowClass);

        var divLabelSelector = document.createElement("div");
        rowSelector.appendChild(divLabelSelector);

        var labelSelector = document.createElement("label");
        labelSelector.setAttribute("class", _labelClass);
        labelSelector.setAttribute("for", _idPrefix + "selector" + index);
        labelSelector.appendChild(document.createTextNode("Selector " + index));
        divLabelSelector.appendChild(labelSelector);

        var divInputSelector = document.createElement("div");
        rowSelector.appendChild(divInputSelector);

        var inputSelector = document.createElement("input");
        inputSelector.setAttribute("type", "text");
        inputSelector.setAttribute("class", _inputClass);
        inputSelector.setAttribute("name", _idSelector + index);
        inputSelector.setAttribute("id", _idSelector + index);
        if(stepInfo)
            inputSelector.setAttribute("value", _formatSelector(step[0]));
        divInputSelector.appendChild(inputSelector);

        var rowStep = document.createElement("div");
        rowStep.setAttribute("class", _rowClass);

        var divLabelStep = document.createElement("div");
        rowStep.appendChild(divLabelStep);

        var labelStep = document.createElement("label");
        labelStep.setAttribute("class", _labelClass);
        labelStep.setAttribute("for", _idPrefix + "step" + index);
        labelStep.appendChild(document.createTextNode("Step " + index));
        divLabelStep.appendChild(labelStep);

        var divStepTextArea = document.createElement("div");
        rowStep.appendChild(divStepTextArea);

        var textAreaStep = document.createElement("textarea");
        textAreaStep.setAttribute("rows", _textAreaRows);
        textAreaStep.setAttribute("cols", _textAreaCols);
        textAreaStep.setAttribute("id", _idPrefix + "step" + index);
        divStepTextArea.appendChild(textAreaStep);

        parent.insertBefore(rowSelector, element);
        parent.insertBefore(rowStep, element);

        if(stepInfo)
            _regCodeMirrorEditor(_idPrefix + "step" + index, step[1]);
        else
            _regCodeMirrorEditor(_idPrefix + "step" + index, null);
    }

    LABKEY.Utils.onReady(_init);
})(jQuery);