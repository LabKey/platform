Ext4.define('LABKEY.vis.ChartLayoutPanel', {
    extend: 'LABKEY.vis.ChartWizardPanel',

    cls: 'chart-wizard-panel chart-layout-panel',
    mainTitle: 'Customize look and feel',
    width: 900,
    height: 525,
    isDeveloper: false,
    defaultChartLabel: null,

    initComponent : function()
    {
        this.bottomButtons = [
            '->',
            this.getCancelButton(),
            this.getApplyButton()
        ];

        this.items = [
            this.getTitlePanel(),
            this.getNavigationPanel(),
            this.getCenterPanel(),
            this.getButtonBar()
        ];

        this.callParent();

        this.addEvents('cancel', 'apply');

        // on show, stash the initial values so we can use for comparison and cancel reset
        this.initValues = {};
        this.on('show', function() {
            this.initValues = this.getValues();
        }, this);
    },

    getNavigationPanel : function()
    {
        if (!this.navigationPanel)
        {
            var data = [{
                name: 'general',
                cardId: 'card-1',
                label: 'General',
                cardClass: 'LABKEY.vis.GenericChartOptionsPanel',
                config: {
                    defaultChartLabel: this.defaultChartLabel
                }
            },{
                name: 'x',
                cardId: 'card-2',
                label: 'X-Axis',
                cardClass: 'LABKEY.vis.GenericChartAxisPanel'
            },{
                name: 'y',
                cardId: 'card-3',
                label: 'Y-Axis',
                cardClass: 'LABKEY.vis.GenericChartAxisPanel'
            }];

            if (this.isDeveloper)
            {
                data.push({
                    name: 'developer',
                    label: 'Developer',
                    cardId: 'card-4',
                    cardClass: 'LABKEY.vis.DeveloperOptionsPanel',
                    config: {
                        isDeveloper: this.isDeveloper,
                        defaultPointClickFn: this.getDefaultPointClickFn(),
                        pointClickFnHelp: this.getPointClickFnHelp()
                    }
                });
            }

            var store = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.ChartLayoutCardModel',
                data: data
            });

            // populate the center card panel with the initial set of panels
            Ext4.each(store.getRange(), function(record)
            {
                var newCardPanel = this.initOptionPanel(record);
                this.getCenterPanel().add(newCardPanel);
            }, this);

            this.navigationPanel = Ext4.create('Ext.view.View', {
                region: 'west',
                cls: 'region-panel navigation-panel',
                itemSelector: 'div.item',
                overItemCls: 'item-over',
                selectedItemCls: 'item-selected',
                store: store,
                tpl: new Ext4.XTemplate(
                    '<tpl for=".">',
                        '<div class="item">{label:htmlEncode}</div>',
                    '</tpl>'
                ),
                listeners: {
                    scope: this,
                    viewready: function(view)
                    {
                        // select the first item when the view is ready
                        view.getSelectionModel().select(0);
                    },
                    select: function(view, record)
                    {
                        this.getCenterPanel().getLayout().setActiveItem(record.get('cardId'));
                    }
                }
            });
        }

        return this.navigationPanel;
    },

    initOptionPanel : function(record)
    {
        var newCardPanel;

        if (record.get('cardClass'))
        {
            // join together the config properties from the record with the ones we need for the card layout
            var config = Ext4.apply({
                itemId: record.get('cardId'),
                panelName: record.get('name'),
                autoScroll: true
            }, record.get('config'));

            newCardPanel = Ext4.create(record.get('cardClass'), config);

            // set initial values based on the config props loaded from the saved config
            if (this.options[record.get('name')] && newCardPanel.setPanelOptionValues)
                newCardPanel.setPanelOptionValues(this.options[record.get('name')]);
        }
        else
        {
            newCardPanel = Ext4.create('Ext.Component', {
                itemId: record.get('cardId'),
                html: 'No cardClass defined for ' + record.get('cardId') + '.'
            });
        }

        return newCardPanel;
    },

    getCenterPanel : function()
    {
        if (!this.centerPanel)
        {
            this.centerPanel = Ext4.create('Ext.panel.Panel', {
                region: 'center',
                cls: 'region-panel center-panel',
                layout: 'card',
                activeItem: 0,
                items: []
            });
        }

        return this.centerPanel;
    },

    getCancelButton : function()
    {
        if (!this.cancelButton)
        {
            this.cancelButton = Ext4.create('Ext.button.Button', {
                text: 'Cancel',
                scope: this,
                handler: function ()
                {
                    // TODO need to also reset the values on ESC key close

                    // if we have changes, revert the panels back to initial values
                    if (this.hasSelectionsChanged(this.getValues()))
                    {
                        Ext4.each(this.getCenterPanel().items.items, function(panel)
                        {
                            if (panel.setPanelOptionValues)
                            {
                                this.getCenterPanel().getLayout().setActiveItem(panel.itemId);
                                panel.setPanelOptionValues(this.initValues[panel.panelName]);
                            }
                        }, this);
                    }

                    // change the selected panel back to the first item
                    this.getNavigationPanel().getSelectionModel().select(0);
                    this.getCenterPanel().getLayout().setActiveItem(0);

                    this.fireEvent('cancel', this);
                }
            });
        }

        return this.cancelButton;
    },

    getApplyButton : function()
    {
        if (!this.applyButton)
        {
            this.applyButton = Ext4.create('Ext.button.Button', {
                text: 'Apply',
                scope: this,
                handler: function()
                {
                    // if nothing has changed, just treat this as a click on 'cancel'
                    var values = this.getValues();
                    if (!this.hasSelectionsChanged(values))
                    {
                        this.fireEvent('cancel', this);
                    }
                    else
                    {
                        // give each panel a chance to validate before applying changes
                        var valid = true;
                        Ext4.each(this.getCenterPanel().items.items, function(panel)
                        {
                            if (panel.validateChanges)
                            {
                                if (!panel.validateChanges())
                                {
                                    // select the panel with invalid state
                                    var navIndex = this.getNavigationPanel().getStore().findExact('cardId', panel.itemId);
                                    this.getNavigationPanel().getSelectionModel().select(navIndex);
                                    this.getCenterPanel().getLayout().setActiveItem(panel.itemId);

                                    valid = false;
                                    return false; // break;
                                }
                            }
                        }, this);

                        if (valid)
                            this.fireEvent('apply', this, values);
                    }
                }
            });
        }

        return this.applyButton;
    },

    getValues : function()
    {
        var values = {};

        Ext4.each(this.getCenterPanel().items.items, function(panel)
        {
            if (panel.getPanelOptionValues)
                values[panel.panelName] = panel.getPanelOptionValues();
        });

        return values;
    },

    hasSelectionsChanged : function(values)
    {
        // compare the keys for the two value objects
        var initKeys = Object.keys(this.initValues),
            newKeys = Object.keys(values);
        if (!Ext4.Array.equals(initKeys, newKeys))
            return true;

        // compare the object in the new values to the init values
        // note: we know we have all the same keys here
        var hasChanges = false;
        Ext4.Object.each(values, function(key, value)
        {
            if (!Ext4.Object.equals(value, this.initValues[key]))
            {
                hasChanges = true;
                return false; // break
            }
        }, this);

        return hasChanges;
    },

    onMeasuresChange : function(measures)
    {
        Ext4.Object.each(measures, function(key, props)
        {
            var navIndex = this.getNavigationPanel().getStore().findExact('name', key);
            if (navIndex > -1)
            {
                var panel = this.getCenterPanel().getLayout().getLayoutItems()[navIndex];
                if (panel.onMeasureChange)
                    panel.onMeasureChange(props);
            }
        }, this);
    },

    getDefaultPointClickFn : function()
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
    },

    getPointClickFnHelp : function()
    {
        return 'Your code should define a single function to be called when a data point in the chart is clicked. '
            + 'The function will be called with the following parameters:<br/>'
            + '<ul>'
            + '<li><b>data:</b> the set of data values for the selected data point. Example: </li>'
            + '<div style="margin-left: 40px;">{</div>'
            + '<div style="margin-left: 60px;">YAxisMeasure: {displayValue: "250", value: 250},<br/>XAxisMeasure: {displayValue: "0.45", value: 0.45000},<br/>ColorMeasure: {value: "Color Value 1"},<br/>PointMeasure: {value: "Point Value 1"}</div>'
            + '<div style="margin-left: 40px;">}</div>'
            + '<li><b>measureInfo:</b> the schema name, query name, and measure names selected for the plot. Example:</li>'
            + '<div style="margin-left: 40px;">{</div>'
            + '<div style="margin-left: 60px;">schemaName: "study",<br/>queryName: "Dataset1",<br/>yAxis: "YAxisMeasure",<br/>xAxis: "XAxisMeasure",<br/>colorName: "ColorMeasure",<br/>pointName: "PointMeasure"</div>'
            + '<div style="margin-left: 40px;">}</div>'
            + '<li><b>clickEvent:</b> information from the browser about the click event (i.e. target, position, etc.)</li></ul>';
    }
});

Ext4.define('LABKEY.vis.ChartLayoutCardModel', {
    extend: 'Ext.data.Model',
    fields: [
        {name: 'name', type: 'string'},
        {name: 'label', type: 'string'},
        {name: 'cardId', type: 'string'},
        {name: 'cardClass', type: 'string'},
        {name: 'config'}
    ]
});