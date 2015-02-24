/*
 * Copyright (c) 2015 LabKey Corporation
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
        _textAreaCols = '65',
        _formClean;

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

    Ext4.define('LABKEY._tour.BaseScriptPanel', {
        extend: 'Ext.Panel',

        SCRIPT_TEMPLATE: null,

        codeMirrorMode: 'text/html',

        optionButton: function(){
            return null;
        },

        constructor: function(config){
            config.padding = '10px 0 0 0';
            config.border = false;
            config.frame = false;
            config.codeMirrorId = 'textarea-' + Ext4.id();
            config.html = '<textarea id="' + config.codeMirrorId + '" name="export-script-textarea"'
            + 'wrap="on" rows="23" cols="120" style="width: 100%;"></textarea>';

            this.callParent([config]);
        },

        initComponent: function(){
            this.buttons = [{
                text: 'Close',
                scope: this,
                handler: function(){this.fireEvent('closeOptionsWindow');}
            },
                this.optionButton()];

            this.on('afterrender', function(cmp){
                var el = Ext4.get(this.codeMirrorId);
                var size = cmp.getSize();
                if (el) {
                    this.codeMirror = CodeMirror.fromTextArea(el.dom, {
                        mode: this.codeMirrorMode,
                        lineNumbers: true,
                        lineWrapping: true,
                        indentUnit: 3
                    });

                    this.codeMirror.setSize(null, size.height + 'px');
                    this.codeMirror.setValue(this.compileTemplate(this.templateConfig));
                    LABKEY.codemirror.RegisterEditorInstance('export-script-textarea', this.codeMirror);
                }
            }, this);
            this.callParent();
        },

        compileTemplate: function(input) {
            return this.SCRIPT_TEMPLATE.replace('{{chartConfig}}', LABKEY.Utils.encode(input.chartConfig));
        }
    });

    Ext4.define('LABKEY._tour.TourExportJsonPanel', {
        extend: 'LABKEY._tour.BaseScriptPanel',

        SCRIPT_TEMPLATE: "{\n" +
        "title:\"{{tourTitle}}\",\n" +
        "description:\"{{tourDescription}}\",\n" +
        "mode:\"{{tourMode}}\",\n" +
        "steps:\n{{tourStep}}\n" +
        "}\n",

        codeMirrorMode: {name: "javascript"},

        compileTemplate: function(input)
        {
            return this.SCRIPT_TEMPLATE
                    .replace('{{tourTitle}}', $(_idSel + 'title').val())
                    .replace('{{tourDescription}}', $(_idSel + 'description').val())
                    .replace('{{tourMode}}', $(_idSel + 'mode').val())
                    .replace('{{tourStep}}', _generateExport());
        }
    });

    Ext4.define('LABKEY._tour.TourImportJsonPanel', {
        extend: 'LABKEY._tour.BaseScriptPanel',

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
        if (LABKEY._tour.description)
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

        _formClean = _serializeAll();
        window.onbeforeunload = LABKEY.beforeunload(_isDirty);

        _rowId = LABKEY._tour.rowId;
    });

    var _isDirty = function()
    {
        var _formDirty = _serializeAll();
        if (_formDirty != _formClean)
        {
            return true;
        }
        return false;

    };

    var _serializeAll = function()
    {
        var form = $('[name="editTour"]').serialize();

        form = form.concat("Mode=" + $(_idSel + "mode").val());
        form = form.concat("Description=" + $(_idSel + "description").val());

        //Code mirror values do not get serialized with form, so tack them on at end
        for (var i = 1; i < _steps; i++)
        {
            form = form.concat("Step" + i + "=" + _stepHandles[_idPrefix + "step" + i].getValue());
        }
        return form;
    };

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
                                eval(JSON.parse(asString));

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
                                else
                                {
                                    LABKEY.Utils.alert('Error', "Evaluation error in step " + i + ": " + x.message);
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
        _formClean = _serializeAll();
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
        var inObj, fnArray = [], err;

        try
        {
            inObj = eval('_a = ' + JSON.parse(JSON.stringify(input)));
            $.each(inObj.steps, function(index, step)
            {
                $.each(step, function(prop, value)
                {
                    if(LABKEY.Utils.isFunction(value))
                    {
                        var fnStr = value.toString();
                        fnArray.push(fnStr);
                        step[prop] = fnStr;
                    }
                });
            });
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
            $(_idSel + 'title').val(inObj["title"]);
            $(_idSel + 'description').val(inObj["description"]);
            $(_idSel + 'mode').val(parseInt(inObj["mode"]));

            var i = 1, strStep;
            if (inObj.steps)
            {
                $.each(inObj.steps, function(index, step)
                {
                    if (!step.target)
                        return;

                    var target = step.target;
                    delete step.target;
                    $(_idSel + 'selector' + i).val(target);

                    strStep = JSON.stringify(step, null, '\t').replace(/\"([^(\")"]+)\":/g, "$1:");

                    $.each(fnArray, function(index, fn)
                    {
                        strStep = strStep.replace(JSON.stringify(fn), fn);
                    });

                    if (i < _steps)
                    {
                        _stepHandles[_idPrefix + 'step' + i].setValue(strStep);
                    }
                    else
                    {
                        _generateStep(_steps++, target, strStep);
                    }
                    i++;
                });
            }
        }
    };

    var _generateExport = function()
    {
        var steps = [];

        for (var i = 1; i < _steps; i++)
        {
            if ($(_idSel + 'selector' + i).val() && _stepHandles[_idPrefix + 'step' + i].getValue())
            {
                try
                {
                    var safeStep = "_stepcontent = " + _stepHandles[_idPrefix + 'step' + i].getValue();
                    var step = eval(JSON.parse(JSON.stringify(safeStep)));

                    step.target = $(_idSel + 'selector' + i).val();
                    steps.push(step);
                }
                catch (x)
                {
                    if (x instanceof SyntaxError)
                    {
                        LABKEY.Utils.alert('Syntax Error', 'JSON syntax error in step ' + i + ': ' + x.message);
                        return;
                    }
                }
            }
        }

        //
        // Here we replace each property that is a function on a step with the .toString() version. This allows
        // us to pass functions through JSON.stringify. This pattern is reversed on import.
        //
        var fnArray = [], strSteps, fnStr;
        $.each(steps, function(i, step)
        {
            $.each(step, function(prop, value)
            {
                if (LABKEY.Utils.isFunction(value))
                {
                    fnStr = value.toString();
                    fnArray.push(fnStr);
                    step[prop] = fnStr;
                }
            });
        });

        strSteps = JSON.stringify(steps, null, '\t').replace(/\"([^(\")"]+)\":/g, "$1:");

        $.each(fnArray, function(index, fn)
        {
            strSteps = strSteps.replace(JSON.stringify(fn), fn);
        });

        return strSteps
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
                    '<div><textarea rows="10" cols="65" id="tour-description"></textarea></div>' +
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

        _regCodeMirrorEditor(_idPrefix + "step" + index, stepValue);
    };
})(jQuery);