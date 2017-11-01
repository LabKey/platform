/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.DeveloperOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    isDeveloper: false,
    renderType: null,

    initComponent : function(){
        this.id = 'developerPanel-' + Ext4.id();

        this.fnErrorDiv = 'error-' + Ext4.id();
        this.pointClickFnDesc = Ext4.create('Ext.container.Container', {
            width: 650,
            html: 'A developer can provide a JavaScript function that will be called when a data point in the chart is clicked. '
                    + 'See the "Help" tab for more information on the parameters available to the function.'
                    + '<br/><div id="' + this.fnErrorDiv + '">&nbsp;</div>'
        });

        this.pointClickFnBtn = Ext4.create('Ext.Button', {
            text: this.pointClickFn ? 'Disable' : 'Enable',
            handler: this.togglePointClickFn,
            scope: this
        });

        this.pointClickTextAreaId = 'textarea-' + Ext4.id();
        this.pointClickTextAreaHtml = Ext4.create('Ext.Panel', {
            height: 280,
            border: false,
            disabled: this.pointClickFn == null,                    // name is for selenium testing
            html: '<textarea id="' + this.pointClickTextAreaId + '" name="point-click-fn-textarea" '
                    + 'wrap="on" rows="21" cols="120" style="width: 100%;"></textarea>',
            listeners: {
                afterrender: function(cmp) {
                    var code = Ext4.get(this.pointClickTextAreaId);
                    var size = cmp.getSize();

                    if (code) {

                        var me = this;
                        this.codeMirror = CodeMirror.fromTextArea(code.dom, {
                            mode            : 'text/javascript',
                            lineNumbers     : true,
                            lineWrapping    : true,
                            indentUnit      : 3
                        });

                        this.codeMirror.setSize(null, size.height + 'px');
                        this.codeMirror.setValue(this.pointClickFn ? this.pointClickFn : '');
                        LABKEY.codemirror.RegisterEditorInstance('point-click-fn-textarea', this.codeMirror);
                    }
                },
                scope: this
            }
        });

        this.pointClickHelpPanel = Ext4.create('Ext.panel.Panel', {
            border: false,
            padding: 10,
            html: this.getPointClickFnHelp()
        });

        this.pointClickFnTabPanel = Ext4.create('Ext.tab.Panel', {
            items: [
                Ext4.create('Ext.Panel', {
                    title: 'Source',
                    layout: 'fit',
                    items: this.pointClickTextAreaHtml
                }),
                Ext4.create('Ext.Panel', {
                    title: 'Help',
                    height: 280,
                    autoScroll: true,
                    items: [this.pointClickHelpPanel]
                })
            ]
        });

        if (this.isDeveloper)
        {
            this.items = [
                {
                    xtype: 'fieldcontainer',
                    layout: 'hbox',
                    anchor: '100%',
                    hideLabel: true,
                    items: [
                        this.pointClickFnDesc,
                        this.pointClickFnBtn
                    ]
                },
                this.pointClickFnTabPanel
            ];
        }

        this.callParent();
    },

    togglePointClickFn: function()
    {
        if (this.pointClickTextAreaHtml.isDisabled())
        {
            this.pointClickFnTabPanel.setActiveTab(0);
            this.setEditorEnabled(this.getDefaultPointClickFn());
        }
        else
        {
            Ext4.Msg.show({
                title:'Confirmation...',
                msg: 'Disabling this feature will delete any code that you have provided.<br/>Would you like to proceed?',
                buttons: Ext4.Msg.YESNO,
                fn: function(btnId)
                {
                    if (btnId == 'yes')
                    {
                        this.pointClickFnTabPanel.setActiveTab(0);
                        this.setEditorDisabled();
                    }
                },
                icon: Ext4.MessageBox.QUESTION,
                scope: this
            });
        }
    },

    setEditorEnabled: function(editorValue) {
        this.pointClickFn = editorValue;
        this.pointClickTextAreaHtml.enable();
        if (this.codeMirror)
            this.codeMirror.setValue(editorValue);
        this.pointClickFnBtn.setText('Disable');
    },

    setEditorDisabled: function() {
        if (Ext4.getDom(this.fnErrorDiv) != null)
            Ext4.getDom(this.fnErrorDiv).innerHTML = '&nbsp;';

        this.pointClickFn = null;
        if (this.codeMirror)
            this.codeMirror.setValue('');
        this.pointClickTextAreaHtml.disable();
        this.pointClickFnBtn.setText('Enable');
    },

    removeLeadingComments: function(fnText) {
        // issue 15679: allow comments before function definition
        fnText = fnText.trim();
        while (fnText.indexOf("//") == 0 || fnText.indexOf("/*") == 0)
        {
            if (fnText.indexOf("//") == 0)
            {
                var endLineIndex = fnText.indexOf("\n");
                fnText = endLineIndex > -1 ? fnText.substring(endLineIndex + 1).trim() : '';
            }
            else if (fnText.indexOf("*/") > -1)
            {
                fnText = fnText.substring(fnText.indexOf("*/") + 2).trim()
            }
            else
            {
                break;
            }
        }

        return fnText;
    },

    getPointClickFnValue : function(validate)
    {
        if (validate)
        {
            var fnText = this.codeMirror.getValue();
            fnText = this.removeLeadingComments(fnText);

            if (fnText == null || fnText.length == 0 || fnText.indexOf("function") != 0)
            {
                Ext4.getDom(this.fnErrorDiv).innerHTML = '<span class="labkey-error">Error: the value provided does not begin with a function declaration.</span>';
                return null;
            }

            try
            {
                var verifyFn = new Function("", "return " + fnText);
            }
            catch(err)
            {
                Ext4.getDom(this.fnErrorDiv).innerHTML = '<span class="labkey-error">Error parsing the function: ' + err.message + '</span>';
                return null;
            }

            Ext4.getDom(this.fnErrorDiv).innerHTML = '&nbsp;';
        }

        if (this.codeMirror)
            return this.codeMirror.getValue();
        else
            return this.pointClickFn;
    },

    getPanelOptionValues : function() {
        return {
            pointClickFn: !this.pointClickTextAreaHtml.isDisabled()
                    // TODO the removeLeadingComments should only be applied when the function is being used, not here
                    ? this.removeLeadingComments(this.getPointClickFnValue(false))
                    : null
        };
    },

    validateChanges : function()
    {
        return this.pointClickTextAreaHtml.isDisabled() || this.getPointClickFnValue(true) != null;
    },

    setPanelOptionValues: function(config){
        if (config && config.hasOwnProperty("pointClickFn"))
        {
            if (config.pointClickFn != null)
                this.setEditorEnabled(config.pointClickFn);
            else
                this.setEditorDisabled();
        }
    },

    onMeasureChange : function(measures, renderType)
    {
        this.renderType = renderType;
        this.pointClickHelpPanel.update(this.getPointClickFnHelp());
    },

    getDefaultPointClickFn : function()
    {
        if (this.renderType == 'time_chart')
        {
            return "function (data, columnMap, measureInfo, clickEvent) {\n"
                + "   var participant = columnMap[\"participant\"] ? \n"
                + "                     data[columnMap[\"participant\"]].value : null;\n"
                + "   var group = columnMap[\"group\"] ? \n"
                + "                     data[columnMap[\"group\"]].displayValue : null;\n\n"
                + "   // use LABKEY.ActionURL.buildURL to generate a link to a different \n"
                + "   // controller/action within LabKey server\n"
                + "   var ptidHref = LABKEY.ActionURL.buildURL('study', 'participant', \n"
                + "                  LABKEY.container.path, {participantId: participant});\n"
                + "   var queryHref = LABKEY.ActionURL.buildURL('query', 'executeQuery', \n"
                + "                   LABKEY.container.path, {schemaName: measureInfo[\"schemaName\"], \n"
                + "                   \"query.queryName\": measureInfo[\"queryName\"]});\n\n"
                + "   // display a message box with some information from the function parameters\n"
                + "   var subjectLabel = (group != null ? 'Group: ' + group : \n"
                + "                'Participant: <a href=\"' + ptidHref + '\">' + participant + '</a>');\n"
                + "   Ext4.Msg.alert('Data Point Information',\n"
                + "       subjectLabel\n"
                + "       + '<br/> Interval: ' + data[columnMap[\"interval\"]].value\n"
                + "       + '<br/> Value: ' + data[columnMap[\"measure\"]].value\n"
                + "       + '<br/> Measure Name: ' + measureInfo[\"name\"]\n"
                + "       + '<br/> Schema Name: ' + measureInfo[\"schemaName\"]\n"
                + "       + '<br/> Query Name: <a href=\"' + queryHref + '\">' + \n"
                + "                    measureInfo[\"queryName\"] + '</a>'\n"
                + "   );\n\n"
                + "   // you could also directly navigate away from the chart using window.location\n"
                + "   // window.location = queryHref;\n"
                + "}";
        }
        else
        {
            return "function (data, measureInfo, clickEvent) {\n"
                + "   // use LABKEY.ActionURL.buildURL to generate a link\n"
                + "   // to a different controller/action within LabKey server\n"
                + "   var queryHref = LABKEY.ActionURL.buildURL('query', 'executeQuery',\n"
                + "                      LABKEY.container.path, {\n"
                + "                          schemaName: measureInfo[\"schemaName\"],\n"
                + "                          \"query.queryName\": measureInfo[\"queryName\"]\n"
                + "                      }\n"
                + "                   );\n\n"
                + "   // display an Ext message box with some information from the function parameters\n"
                + "   var info = 'Schema: ' + measureInfo[\"schemaName\"]\n"
                + "       + '<br/> Query: <a href=\"' + queryHref + '\">'\n"
                + "       + measureInfo[\"queryName\"] + '</a>';\n"
                + "   for (var key in measureInfo)\n"
                + "   {\n"
                + "       if (measureInfo.hasOwnProperty(key) && data[measureInfo[key]])\n"
                + "       {\n"
                + "           info += '<br/>' + measureInfo[key] + ': '\n"
                + "                + (data[measureInfo[key]].displayValue\n"
                + "                   ? data[measureInfo[key]].displayValue\n"
                + "                   : data[measureInfo[key]].value);\n"
                + "       }\n"
                + "   }\n"
                + "   Ext4.Msg.alert('Data Point Information', info);\n\n"
                + "   // you could also directly navigate away from the chart using window.location\n"
                + "   // window.location = queryHref;\n"
                + "}";
        }
    },

    getPointClickFnHelp : function()
    {
        if (this.renderType == 'time_chart')
        {
            return 'Your code should define a single function to be called when a data point in the chart is clicked. '
                + 'The function will be called with the following parameters:<br/>'
                + '<ul>'
                + '<li><b>data:</b> the set of data values for the selected data point. Example: </li>'
                + '<div style="margin-left: 40px;">{</div>'
                + '<div style="margin-left: 60px;">Days: {value: 10},<br/>study_Dataset1_Measure1: {value: 250}<br/>study_Dataset1_ParticipantId: {value: "123456789"}</div>'
                + '<div style="margin-left: 40px;">}</div>'
                + '<li><b>columnMap:</b> a mapping from participant, interval, and measure to use when looking up values in the data object</li>'
                + '<div style="margin-left: 40px;">{</div>'
                + '<div style="margin-left: 60px;">participant: "study_Dataset1_ParticipantId",<br/>measure: "study_Dataset1_Measure1"<br/>interval: "Days"</div>'
                + '<div style="margin-left: 40px;">}</div>'
                + '<li><b>measureInfo:</b> the schema name, query name, and measure name for the selected series</li>'
                + '<div style="margin-left: 40px;">{</div>'
                + '<div style="margin-left: 60px;">name: "Measure1",<br/>queryName: "Dataset1"<br/>schemaName: "study"</div>'
                + '<div style="margin-left: 40px;">}</div>'
                + '<li><b>clickEvent:</b> information from the browser about the click event (i.e. target, position, etc.)</li></ul>';
        }
        else
        {
            return 'Your code should define a single function to be called when a data point in the chart is clicked. '
                + 'The function will be called with the following parameters:<br/>'
                + '<br/><b>data:</b> the set of data values for the selected data point.'
                + '<br/>Example: {'
                + '<div style="margin-left: 80px;">YAxisMeasure: {displayValue: "250", value: 250},<br/>XAxisMeasure: {displayValue: "0.45", value: 0.45000},<br/>ColorMeasure: {value: "Color Value 1"},<br/>PointMeasure: {value: "Point Value 1"}</div>'
                + '<div style="margin-left: 60px;">}</div>'
                + '<br/><b>measureInfo:</b> the schema name, query name, and measure names selected for the plot.'
                + '<br/>Example: {'
                + '<div style="margin-left: 80px;">schemaName: "study",<br/>queryName: "Dataset1",<br/>yAxis: "YAxisMeasure",<br/>xAxis: "XAxisMeasure",<br/>colorName: "ColorMeasure",<br/>pointName: "PointMeasure"</div>'
                + '<div style="margin-left: 60px;">}</div>'
                + '<br/><b>clickEvent:</b> information from the browser about the click event (i.e. target, position, etc.)';
        }
    }
});
