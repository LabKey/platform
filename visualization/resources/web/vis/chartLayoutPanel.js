Ext4.define('LABKEY.vis.ChartLayoutPanel', {
    extend: 'Ext.panel.Panel',

    cls: 'chart-layout-panel',
    layout: 'border',
    border: false,
    width: 900,
    height: 525,

    isDeveloper: false,
    defaultTitle: null,

    initComponent : function()
    {
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

    getTitlePanel : function()
    {
        if (!this.titlePanel)
        {
            this.titlePanel = Ext4.create('Ext.panel.Panel', {
                region: 'north',
                cls: 'region-panel title-panel',
                border: false,
                html: 'Customize look and feel'
            });
        }

        return this.titlePanel;
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
                        defaultPointClickFn: this.defaultPointClickFn,
                        pointClickFnHelp: this.pointClickFnHelp
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

    getButtonBar : function()
    {
        if (!this.buttonBar)
        {
            this.buttonBar = Ext4.create('Ext.toolbar.Toolbar', {
                region: 'south',
                cls: 'region-panel button-bar',
                border: false,
                ui: 'footer',
                defaults: {width: 65},
                items: [
                    '->',
                    this.getCancelButton(),
                    this.getApplyButton()
                ]
            });
        }

        return this.buttonBar;
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