/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.vis.DeveloperOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){

        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'closeOptionsWindow'
        );
    },

    initComponent : function(){
        // track if the panel has changed
        this.hasChanges = false;

        this.fnErrorDiv = 'error-' + Ext4.id();
        this.pointClickFnDesc = Ext4.create('Ext.container.Container', {
            width: 675,
            autoEl: {
                tag: 'span',
                html: 'A developer can provde a JavaScript function that will be called when a data point in the chart is clicked. '
                    + 'See the "Help" tab for more information on the parameters available to the function.'
                    + '<br/><div id="' + this.fnErrorDiv + '">&nbsp;</div>'
            }
        });

        this.pointClickFnBtn = Ext4.create('Ext.Button', {
            text: this.pointClickFn ? 'Disable' : 'Enable',
            handler: this.togglePointClickFn,
            scope: this
        });

        // TODO: change to use JS text editor similar to JS Report Source tab
        this.pointClickFnTextArea = Ext4.create('Ext.form.field.TextArea', {
            name: 'point-click-fn-textarea', // for selenium testing
            hideLabel: true,
            disabled: this.pointClickFn == null, 
            value: this.pointClickFn ? this.pointClickFn : this.getDefaultPointClickFn(),
            height: 375,
            listeners: {
                scope: this,
                'change': function() {
                    this.hasChanges = true;
                }
            }
        });

        this.pointClickFnTabPanel = Ext4.create('Ext.tab.Panel', {
            height: 400,
            items: [
                Ext4.create('Ext.Panel', {
                    title: 'Source',
                    width: 600,
                    layout: 'fit',
                    items: this.pointClickFnTextArea
                }),
                Ext4.create('Ext.Panel', {
                    title: 'Help',
                    width: 600,
                    padding: 5,
                    html: 'Your code should define a single function to be called when a data point in the chart is clicked. '
                        + 'The function will be called with the following parameters:<br/><br/>'
                        + '<ul style="margin-left:20px;">'
                        + '<li><b>data:</b> the set of data values for the selected data point. Example: </li>'
                        + '<div style="margin-left: 40px;">{</div>'
                        + '<div style="margin-left: 50px;">Days: {value: 10},<br/>study_Dataset1_Measure1: {value: 250}<br/>study_Dataset1_ParticipantId: {value: "123456789"}</div>'
                        + '<div style="margin-left: 40px;">}</div>'    
                        + '<li><b>columnMap:</b> a mapping from participant, interval, and measure to use when looking up values in the data object</li>'
                        + '<div style="margin-left: 40px;">{</div>'
                        + '<div style="margin-left: 50px;">participant: "study_Dataset1_ParticipantId",<br/>measure: "study_Dataset1_Measure1"<br/>interval: "Days"</div>'
                        + '<div style="margin-left: 40px;">}</div>'
                        + '<li><b>measureInfo:</b> the schema name, query name, and measure name for the selected series</li>'
                        + '<div style="margin-left: 40px;">{</div>'
                        + '<div style="margin-left: 50px;">name: "Measure1",<br/>queryName: "Dataset1"<br/>schemaName: "study"</div>'
                        + '<div style="margin-left: 40px;">}</div>'
                        + '<li><b>clickEvent:</b> information from the browser about the click event (i.e. target, position, etc.)</li></ul>'
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

        this.buttons = [{
            text: 'Apply',
            handler: this.applyButtonClicked,
            scope: this
        }];

        this.callParent();
    },

    togglePointClickFn: function() {
        if (this.pointClickFnTextArea.isDisabled())
        {
            this.pointClickFnTextArea.enable();
            this.pointClickFnBtn.setText('Disable');
        }
        else
        {
            Ext4.Msg.show({
                title:'Confirmation...',
                msg: 'Disabling this feature will delete any code that you have provided. Would you like to proceed?',
                buttons: Ext4.Msg.YESNO,
                fn: function(btnId, text, opt){
                    if(btnId == 'yes'){
                        Ext4.getDom(this.fnErrorDiv).innerHTML = '&nbsp;';
                        this.pointClickFnTextArea.disable();
                        this.pointClickFnTextArea.setValue(this.getDefaultPointClickFn());
                        this.pointClickFnBtn.setText('Enable');
                    }
                },
                icon: Ext4.MessageBox.QUESTION,
                scope: this
            });
        }
        this.hasChanges = true;
    },

    getDefaultPointClickFn: function() {
        return "function (data, columnMap, measureInfo, clickEvent) {\n"
            + "   // use LABKEY.ActionURL.buildURL to generate a link to a different controller/action within labkey server\n"
            + "   var ptidHref = LABKEY.ActionURL.buildURL('study', 'participant', LABKEY.container.path, \n"
            + "                      {participantId: data[columnMap[\"participant\"]].value});\n"
            + "   var queryHref = LABKEY.ActionURL.buildURL('query', 'executeQuery', LABKEY.container.path, \n"
            + "                      {schemaName: measureInfo[\"schemaName\"], \"query.queryName\": measureInfo[\"queryName\"]});\n\n"
            + "   // display an Ext message box with some information from the function parameters\n"
            + "   Ext.Msg.alert('Data Point Information',\n"
            + "       'Participant: <a href=\"' + ptidHref + '\">' + data[columnMap[\"participant\"]].value + '</a>'\n"
            + "       + '<br/> Interval: ' + data[columnMap[\"interval\"]].value\n"
            + "       + '<br/> Value: ' + data[columnMap[\"measure\"]].value\n"
            + "       + '<br/> Meausure Name:' + measureInfo[\"name\"]\n"
            + "       + '<br/> Schema Name:' + measureInfo[\"schemaName\"]\n"
            + "       + '<br/> Query Name: <a href=\"' + queryHref + '\">' + measureInfo[\"queryName\"] + '</a>'\n"
            + "   );\n\n"
            + "   // you could also directly navigate away from the chart using window.location\n"
            + "   // window.location = ptidHref;\n"
            + "}";
    },

    applyButtonClicked: function() {
        // verify the pointClickFn for JS errors
        if (!this.pointClickFnTextArea.isDisabled())
        {
            var fnText = this.pointClickFnTextArea.getValue().trim();
            if (fnText == null || fnText.length == 0 || fnText.indexOf("function") != 0)
            {
                Ext4.getDom(this.fnErrorDiv).innerHTML = '<span class="labkey-error">Error: the value provided does not begin with a function declaration.</span>';
                return;
            }

            try
            {
                var verifyFn = new Function("", "return " + fnText);
            }
            catch(err)
            {
                console.error(err.message);
                Ext4.getDom(this.fnErrorDiv).innerHTML = '<span class="labkey-error">Error parsing the function: ' + err.message + '</span>';
                return;
            }
            Ext4.getDom(this.fnErrorDiv).innerHTML = '&nbsp;';
        }

        this.fireEvent('closeOptionsWindow');
        this.checkForChangesAndFireEvents();
    },

    getPanelOptionValues : function() {
        return {pointClickFn: !this.pointClickFnTextArea.isDisabled() ? this.pointClickFnTextArea.getValue() : null};
    },

    checkForChangesAndFireEvents : function() {
        if (this.hasChanges)
            this.fireEvent('chartDefinitionChanged', false);

        // reset the changes flags
        this.hasChanges = false;
    }
});
