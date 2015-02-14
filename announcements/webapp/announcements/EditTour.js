/*
 * Copyright (c) 2008-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

(function ($)
{

    var _idPrefix = 'tour-';
    var _idSel = '#' + _idPrefix;
    var _steps = 1;
    var _idSelector = _idPrefix + 'selector';

    var _rowClass = "col-width row";
    var _labelClass = "label";
    var _inputClass = "input";
    var _textAreaRows = "10";
    var _textAreaCols = "65";

    var _rowId = "";

    var _stepHandles = {};

    LABKEY._tour = new function ()
    {
        return {
            json: {},
            title: "",
            description: "",
            mode: 0,
            rowId: 0
        };
    };

    Ext4.onReady(function ()
    {
        Ext4.define('LABKEY._tour.TourExportJsonPanel', {
            extend: 'LABKEY.vis.BaseExportScriptPanel',

            SCRIPT_TEMPLATE: "{\n" +
            "\"Title\":\"{{tourTitle}}\",\n" +
            "\"Description\":\"{{tourDescription}}\",\n" +
            "\"Mode\":\"{{tourMode}}\",\n" +
            "\"Tour\":\n{{tourJson}}\n" +
            "}\n",

            codeMirrorMode: {name: "javascript", json: true},

            compileTemplate: function (input)
            {
                return this.SCRIPT_TEMPLATE
                        .replace('{{tourTitle}}', $(_idSel + 'title').val())
                        .replace('{{tourDescription}}', $(_idSel + 'description').val())
                        .replace('{{tourMode}}', $(_idSel + 'mode').val())
                        .replace('{{tourJson}}', _generateExport());
            }
        });

        Ext4.define('LABKEY._tour.TourImportJsonPanel', {
            extend: 'LABKEY.vis.BaseExportScriptPanel',

            SCRIPT_TEMPLATE: "",

            codeMirrorMode: {name: "javascript", json: true},

            optionButton: function ()
            {
                return {
                    text: 'Import', scope: this, handler: function ()
                    {
                        _retrieveImport(this.codeMirror.getValue());
                        this.fireEvent("closeOptionsWindow");
                    }
                }
            },

            compileTemplate: function (input)
            {
                return this.SCRIPT_TEMPLATE;
            }
        });

        Ext4.define('LABKEY._tour.EditTour', {
            extend: 'Ext.panel.Panel',
            export: function ()
            {
                if (!this.exportTourWindow)
                {
                    this.editorExportTourPanel = Ext4.create('LABKEY._tour.TourExportJsonPanel', {
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
            },
            import: function ()
            {
                if (!this.importTourWindow)
                {
                    this.editorImportTourPanel = Ext4.create('LABKEY._tour.TourImportJsonPanel', {
                        listeners: {
                            scope: this,
                            closeOptionsWindow: function ()
                            {
                                this.importTourWindow.hide();
                            }
                        }
                    });

                    this.importTourWindow = Ext4.create('Ext.window.Window', {
                        title: "Import Tour",
                        cls: 'data-window',
                        border: false,
                        frame: false,
                        modal: true,
                        width: 800,
                        resizable: false,
                        closeAction: 'hide',
                        items: [this.editorImportTourPanel]
                    });
                }
                this.importTourWindow.show();
            }
        });
    });

    var _init = function ()
    {
        _bindControls();

        _generateMode(document.getElementById("leftcolumn"), document.getElementById("mode-dummy"));

        var dummy = document.getElementById("dummy");
        var parent = document.getElementById("rightcolumn");

        // Set title and description if editing existing tour
        $(_idSel + "title").val(LABKEY._tour.title);
        $(_idSel + "description").val(LABKEY._tour.description);

        var x=0;
        var orderedSteps = [];
        var inSteps = LABKEY._tour.json["steps"];
        var stepIndex = 0;

        // If editing existing tour, first need to establish correct order of steps
        for(var key in inSteps)
        {
            if(inSteps[key].step)
                stepIndex = inSteps[key].step-1;
            else
                stepIndex = x++;

            orderedSteps[stepIndex] = inSteps[key];
            delete orderedSteps[stepIndex].step;
        }

        // After existing tour steps ordered correctly, generate steps with values
        for(var i=0; i<orderedSteps.length; i++)
        {
            if(orderedSteps[i])
            {
                var target = orderedSteps[i].target;
                delete orderedSteps[i].target;
                _generateStep(_steps++, parent, dummy, target, JSON.stringify(orderedSteps[i]));
            }
            else
                _generateStep(_steps++, parent, dummy, null, null);
        }

        // Generate steps for new tour or left over space from editing tour
        while (_steps < 4)
            _generateStep(_steps++, parent, dummy, null, null);

        _rowId = LABKEY._tour.rowId;
    }

    var _saveClose = function ()
    {
        _submit(true);
    }

    var _save = function ()
    {
        _submit(false);
    }

    var _submit = function (close)
    {
        var json = {};
        var tour = {};
        var steps = [];
        var err = false;

        for (var i = 1; i < _steps; i++)
        {
            var obj = {};

            var target = $(_idSel + 'selector' + i).val();
            if (target !== undefined && target != "")
            {
                var step = _stepHandles[_idPrefix + 'step' + i].getValue();
                if (step !== undefined && step != "")
                {
                    obj["target"] = $(_idSel + 'selector' + i).val();
                    obj["step"] = i;
                    try
                    {
                        $.each(JSON.parse(step), function (key, value)
                        {
                            obj[key] = value;
                        });

                        steps.push(obj);
                    }
                    catch (x)
                    {
                        if (x instanceof SyntaxError)
                        {
                            LABKEY.Utils.alert("JSON syntax error in step " + i + ": " + x.message);
                            err = true;
                        }
                    }
                }
            }
        }

        if (!err)
        {
            tour["id"] = $(_idSel + 'title').val();
            tour["steps"] = steps;

            json["title"] = $(_idSel + 'title').val();
            json["description"] = $(_idSel + 'description').val();
            json["mode"] = $(_idSel + 'mode').val();
            json["rowId"] = _rowId;
            json["tour"] = tour;

            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('tours', 'saveTour'),
                jsonData: json,
                success: LABKEY.Utils.getCallbackWrapper(function (result)
                {
                    _success.call(this, close, result);
                }, this, false),
                failure: LABKEY.Utils.getCallbackWrapper(function (result)
                {
                    _failure.call(result);
                }, this, false),
                scope: this
            });
        }
    }

    var _failure = function (result)
    {
        LABKEY.Utils.alert("Save failed! Error: " + result);
    }

    var _success = function (close, result)
    {
        if (close)
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
        for (var i = 1; i < _steps; i++)
        {
            $(_idSel + 'selector' + i).val('');
            _stepHandles[_idPrefix + 'step' + i].setValue('');
        }
    }

    var _importTour = function ()
    {
        LABKEY._tour.EditTour.create().import();
    }

    var _exportTour = function ()
    {
        LABKEY._tour.EditTour.create().export();
    }

    var _addStep = function ()
    {
        var dummy = document.getElementById("dummy");
        var parent = document.getElementById("rightcolumn");

        _generateStep(_steps++, parent, dummy, null, null);
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

    var _retrieveImport = function (input)
    {
        var obj;
        var err;
        try
        {
            obj = JSON.parse(input);
        }
        catch (x)
        {
            if (x instanceof SyntaxError)
            {
                LABKEY.Utils.alert("Import canceled. JSON syntax error in import: " + x.message);
                err = true;
            }
        }

        if (!err)
        {
            $(_idSel + 'title').val(obj["Title"]);
            $(_idSel + 'description').val(obj["Description"]);
            $(_idSel + 'mode').val(parseInt(obj["Mode"]));

            var i = 1;
            for (var step in obj["Tour"])
            {
                if (i < _steps)
                {
                    $(_idSel + 'selector' + i).val(step);
                    _stepHandles[_idPrefix + 'step' + i].setValue(_formatStep(JSON.stringify(obj.Tour[step])));
                }
                else
                {
                    var dummy = document.getElementById("dummy");
                    var parent = document.getElementById("rightcolumn");
                    _generateStep(_steps++, parent, dummy, step, JSON.stringify(obj.Tour[step]));
                }
                i++;
            }
        }
    }

    var _generateExport = function ()
    {
        var tour = {};
        var err = false;
        for (var i = 1; i < _steps; i++)
        {
            if ($(_idSel + 'selector' + i).val() && _stepHandles[_idPrefix + 'step' + i].getValue())
            {
                try
                {
                    tour[$(_idSel + 'selector' + i).val()] = JSON.parse(_stepHandles[_idPrefix + 'step' + i].getValue());
                }
                catch (x)
                {
                    if (x instanceof SyntaxError)
                    {
                        LABKEY.Utils.alert("JSON syntax error in step " + i + ": " + x.message);
                        err = true;
                    }
                }
            }
        }
        if (err)
            return null;
        else
            return JSON.stringify(tour, null, '\t');
    }

    var _generateMode = function (parent, element)
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

        for (var i = 0; i < values.length; i++)
        {
            var option = document.createElement("option");
            option.value = i;
            option.text = values[i];
            if (LABKEY._tour.mode == i)
                option.selected = true;
            selectMode.appendChild(option);
        }

        parent.insertBefore(rowMode, element);
    }

    var _regCodeMirrorEditor = function (id, value)
    {
        var editor = CodeMirror.fromTextArea(document.getElementById(id), {
            mode: {name: 'javascript', json: true},
            lineNumbers: true,
            lineWrapping: true,
            indentUnit: 0
        });

        editor.setSize(474, 150)
        if (null != value)
            editor.setValue(_formatStep(value));

        LABKEY.codemirror.RegisterEditorInstance(id, editor);
        _stepHandles[id] = editor;
    }

    var _formatGeneric = function (value)
    {
        value = value.replace(/\\/g, "");
        value = value.replace(/,/g, ",\n");
        value = value.replace(/""/g, "\"");
        return value;
    }

    var _formatExport = function (value)
    {
        value = _formatGeneric(value);
        value = value.replace(/}"[^},]/g, "}");
        value = value.replace(/"{/g, "{");
        return value;
    }

    var _formatStep = function (value)
    {
        value = _formatGeneric(value);
        value = value.replace(/"}"}/g, "\"");
        return value;
    }

    var _formatSelector = function (value)
    {
        value = value.replace(/"/g, "");
        value = value.replace(/}/g, "");
        value = value.replace(/{/g, "");
        return value;
    }

    var _generateStep = function (index, parent, element, select, step)
    {
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
        if (null != select)
            inputSelector.setAttribute("value", select);
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

        _regCodeMirrorEditor(_idPrefix + "step" + index, step);
    }

    LABKEY.Utils.onReady(_init);
})(jQuery);