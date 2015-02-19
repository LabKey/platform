/*
 * Copyright (c) 2008-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

(function($)
{
    var _idPrefix = 'tour-',
        _idSel = '#' + _idPrefix,
        _steps = 1,
        _stepHandles = {},
        _idSelector = _idPrefix + 'selector',
        _rowId = '',
        _rowClass = 'col-width row',
        _labelClass = 'label',
        _inputClass = 'input',
        _textAreaRows = '10',
        _textAreaCols = '65';

    LABKEY._tour = new function()
    {
        return {
            json: {},
            title: '',
            description: '',
            mode: 0,
            rowId: 0
        };
    };

    Ext4.define('LABKEY._tour.TourExportJsonPanel', {
        extend: 'LABKEY.vis.BaseExportScriptPanel',

        SCRIPT_TEMPLATE: "{\n" +
        "\"Title\":\"{{tourTitle}}\",\n" +
        "\"Description\":\"{{tourDescription}}\",\n" +
        "\"Mode\":\"{{tourMode}}\",\n" +
        "\"Tour\":\n{{tourJson}}\n" +
        "}\n",

        codeMirrorMode: {name: "javascript", json: true},

        compileTemplate: function(input)
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

        optionButton: function()
        {
            return {
                text: 'Import', scope: this, handler: function()
                {
                    _retrieveImport(this.codeMirror.getValue());
                    this.fireEvent("closeOptionsWindow");
                }
            }
        },

        compileTemplate: function(input)
        {
            return this.SCRIPT_TEMPLATE;
        }
    });

    Ext4.define('LABKEY._tour.EditTour', {
        extend: 'Ext.panel.Panel',
        showExport: function ()
        {
            if (!this.exportTourWindow)
            {
                var editorExportTourPanel = Ext4.create('LABKEY._tour.TourExportJsonPanel', {
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
                    items: [editorExportTourPanel]
                });
            }
            this.exportTourWindow.show();
        },
        showImport: function ()
        {
            if (!this.importTourWindow)
            {
                var editorImportTourPanel = Ext4.create('LABKEY._tour.TourImportJsonPanel', {
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
                    items: [editorImportTourPanel]
                });
            }
            this.importTourWindow.show();
        }
    });

    Ext4.onReady(function()
    {
        _bindControls();

        _generatePrimaryFields($('.leftcolumn'));

        // Set title and description if editing existing tour
        $(_idSel + "title").val(LABKEY._tour.title);
        $(_idSel + "description").val(LABKEY._tour.description);

        var inputSteps = LABKEY._tour.json.steps;

        if (inputSteps)
        {
            $.each(inputSteps, function(i, istep)
            {
                if (istep.step)
                {
                    var stepText = JSON.parse(istep.step).replace('_stepcontent = ', '');
                    _generateStep(_steps++, istep.target, stepText);
                }
            });
        }

        // Generate steps for new tour or left over space from editing tour
        while (_steps < 4)
        {
            _generateStep(_steps++);
        }

        _rowId = LABKEY._tour.rowId;
    });

    var _saveClose = function()
    {
        _submit(true);
    };

    var _save = function()
    {
        _submit(false);
    };

    var _submit = function(close)
    {
        var steps = [],
            err = false,
            obj, target, step, title = $(_idSel + 'title').val();

        if (!title)
        {
            LABKEY.Utils.alert('Field Value Error', "Please include a title");
            err = true;
        }
        else
        {
            var s;
            for (var i = 1; i < _steps; i++)
            {
                obj = {};

                target = $(_idSel + 'selector' + i).val();

                if (target)
                {
                    step = _stepHandles[_idPrefix + 'step' + i].getValue();

                    if (step)
                    {
                        s = step.trim();

                        if (s && s.length > 0)
                        {
                            // check if using object notation
                            if (s.indexOf('{') == 0)
                            {
                                if (s[s.length-1] == '}')
                                {
                                    s = "_stepcontent = " + s;
                                }
                                else
                                {
                                    LABKEY.Utils.alert('Syntax Error Step ' + i, 'If using object notation, must wrap both sides in { }.');
                                    err = true;
                                    break;
                                }
                            }
                            else
                            {
                                // assume they need to wrap in an object
                                s = "_stepcontent = {\n" + s + "\n}";
                            }

                            obj["target"] = target;
                            try
                            {
                                var asString = JSON.stringify(s);

                                // ensure the step is syntactically safe for an eval
                                JSON.parse(asString);

                                // looks like we're good
                                obj["step"] = asString;
                                steps.push(obj);
                            }
                            catch (x)
                            {
                                if (x instanceof SyntaxError)
                                {
                                    LABKEY.Utils.alert('Syntax Error', "JSON syntax error in step " + i + ": " + x.message);
                                    err = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!err && steps.length < 1)
        {
            LABKEY.Utils.alert('Field Value Error', "At least one valid step required to save tour.");
            err = true;
        }

        if (!err)
        {
            var tour = {
                id: title,
                steps: steps
            };

            var json = {
                title: title,
                description: $(_idSel + 'description').val(),
                mode: $(_idSel + 'mode').val(),
                rowId: _rowId,
                tour: tour
            };

            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('tours', 'saveTour'),
                jsonData: json,
                success: LABKEY.Utils.getCallbackWrapper(function(result)
                {
                    _success.call(this, close, result);
                }, this, false),
                failure: LABKEY.Utils.getCallbackWrapper(function(result)
                {
                    _failure.call(result);
                }, this, false),
                scope: this
            });
        }
    };

    var _failure = function(result)
    {
        LABKEY.Utils.alert('Save Failed!', 'Error: ' + result);
    };

    var _success = function(close, result)
    {
        if (close)
            _cancel();
        else
        {
            _rowId = result["rowId"];
            _setStatus("Saved.", true);
        }
    };

    var _setStatus = function(msg, autoClear) {
        $('#status').html(msg).attr('class', 'labkey-status-info').show();
        if (autoClear) {
            setTimeout(function() { $('#status').html('').hide(); }, 5000);
        }
    };

    var _cancel = function()
    {
        window.location = LABKEY.ActionURL.buildURL('tours', 'begin.view');
    };

    var _clear = function()
    {
        $(_idSel + 'title').val('');
        $(_idSel + 'description').val('');

        $(_idSel + 'mode option:eq(0)').prop('selected', true);

        for (var i = 1; i < _steps; i++)
        {
            $(_idSel + 'selector' + i).val('');
            _stepHandles[_idPrefix + 'step' + i].setValue('');
        }
    };

    var _importTour = function()
    {
        Ext4.create('LABKEY._tour.EditTour').showImport();
    };

    var _exportTour = function()
    {
        Ext4.create('LABKEY._tour.EditTour').showExport();
    };

    var _addStep = function()
    {
        _generateStep(_steps++);
    };

    /**
     * Binds all events to the form attributes of the editor.
     */
    var _bindControls = function()
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

    var _retrieveImport = function(input)
    {
        var obj, err;

        try
        {
            obj = JSON.parse(input);
        }
        catch (x)
        {
            if (x instanceof SyntaxError)
            {
                LABKEY.Utils.alert("Import canceled", "JSON syntax error in import: " + x.message);
                err = true;
            }
        }

        if (!err)
        {
            $(_idSel + 'title').val(obj["Title"]);
            $(_idSel + 'description').val(obj["Description"]);
            $(_idSel + 'mode').val(parseInt(obj["mode"]));

            var i = 1;
            if (obj.Tour)
            {
                $.each(obj.Tour, function(selector, config)
                {
                    if (i < _steps)
                    {
                        $(_idSel + 'selector' + i).val(selector);
                        _stepHandles[_idPrefix + 'step' + i].setValue(_formatStep(JSON.stringify(config)));
                    }
                    else
                    {
                        _generateStep(_steps++, selector, JSON.stringify(config));
                    }
                    i++;
                });
            }
        }
    };

    var _generateExport = function()
    {
        var tour = {},
            err = false;
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
                        LABKEY.Utils.alert('Syntax Error', 'JSON syntax error in step ' + i + ': ' + x.message);
                        err = true;
                    }
                }
            }
        }

        if (!err)
        {
            return JSON.stringify(tour, null, '\t');
        }

        return null;
    };

    var _generatePrimaryFields = function(parent)
    {
        //
        // Title
        //
        var html = '<div class="' + _rowClass + '">' +
                        '<div><label class="label" for="tour-title">Title</label></div>' +
                        '<div><input id="tour-title" type="text" class="input" name="tour-title"></div>' +
                   '</div>';

        //
        // Mode
        //
        var selectHtml = '<select id="tour-mode" class="select">';
        $.each(['Off', 'Run Once', 'Run Always'], function(i, val)
        {
            selectHtml += '<option value="' + i + '" ' + (LABKEY._tour.mode == i ? 'selected' : '') + '>' + val + '</option>';
        });
        selectHtml += '</select>';

        html += '<div class="' + _rowClass + '">' +
                    '<div><label class="label" for="tour-mode">Mode</label></div>' +
                    '<div>' + selectHtml + '</div>' +
                '</div>';

        //
        // Description
        //
        html += '<div class="' + _rowClass + '">' +
                    '<div><label class="label" for="tour-description">Description</label></div>' +
                    '<div><textarea rows="10" cols="65" id="tour-description"></div>' +
                '</div>';

        parent.append(html);
    };

    var _regCodeMirrorEditor = function(id, value)
    {
        var editor = CodeMirror.fromTextArea(document.getElementById(id), {
            mode: {name: 'javascript'},
            lineNumbers: true,
            lineWrapping: true,
            indentUnit: 0
        });

        editor.setSize(474, 150);
        if (value)
            editor.setValue(value);

        LABKEY.codemirror.RegisterEditorInstance(id, editor);
        _stepHandles[id] = editor;
    };

    var _formatGeneric = function(value)
    {
        return value.replace(/\\/g, '').replace(/,/g, ',\n').replace(/""/g, '\"');
    };

    var _formatExport = function(value)
    {
        return _formatGeneric(value).replace(/}"[^},]/g, '}').replace(/"{/g, '{');
    };

    var _formatStep = function(value)
    {
        return _formatGeneric(value).replace(/"}"}/g, '\"');
    };

    var _formatSelector = function(value)
    {
        return value.replace(/"/g, '').replace(/}/, '').replace(/{/, '');
    };

    /**
     *
     * @param index
     * @param {string} [select]
     * @param {number} [stepValue]
     * @private
     */
    var _generateStep = function(index, select, stepValue)
    {
        var element = document.getElementById("dummy");
        var parent = document.getElementById("rightcolumn");

        var rowSelector = document.createElement("div");
        rowSelector.setAttribute("class", _rowClass);

        var divLabelSelector = document.createElement("div");
        rowSelector.appendChild(divLabelSelector);

        var labelSelector = document.createElement("label");
        labelSelector.setAttribute("class", _labelClass);
        labelSelector.setAttribute("for", _idPrefix + "selector" + index);
        labelSelector.appendChild(document.createTextNode("Selector " + index + ' (target)'));
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

        _regCodeMirrorEditor(_idPrefix + "step" + index, stepValue);
    };
})(jQuery);