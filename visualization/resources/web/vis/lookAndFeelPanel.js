Ext4.define('LABKEY.vis.LookAndFeelPanel', {
    extend: 'Ext.panel.Panel',

    cls: 'look-and-feel-panel',
    layout: 'border',
    border: false,
    width: 900,
    height: 525,

    isDeveloper: false,

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
                cardId: 'card-1',
                label: 'General',
                cardClass: 'LABKEY.vis.GenericChartOptionsPanel'
            },{
                cardId: 'card-2',
                label: 'X-Axis',
                cardClass: 'LABKEY.vis.GenericChartAxisPanel'
            },{
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
                    cardClass: 'LABKEY.vis.DeveloperOptionsPanel'
                });
            }

            var store = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.LookAndFeelCardModel',
                data: data
            });

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
                        // if the card for the selected item does not yet exist, create it
                        if (this.getCenterPanel().getCardItemIds().indexOf(record.get('cardId')) == -1)
                        {
                            // TODO what about the properties to pass to the newly created component?
                            var newCardPanel;
                            if (record.get('cardClass'))
                            {
                                newCardPanel = Ext4.create(record.get('cardClass'), {
                                    itemId: record.get('cardId'),
                                    autoScroll: true
                                });
                            }
                            else
                            {
                                newCardPanel = Ext4.create('Ext.Component', {
                                    itemId: record.get('cardId'),
                                    html: 'No cardClass defined for ' + record.get('cardId') + '.'
                                });
                            }

                            this.getCenterPanel().add(newCardPanel);
                            this.getCenterPanel().updateCardItemIds();
                        }

                        this.getCenterPanel().getLayout().setActiveItem(record.get('cardId'));
                    }
                }
            });
        }

        return this.navigationPanel;
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
                cardItemIds: [],
                items: [],

                updateCardItemIds : function()
                {
                    this.cardItemIds = Ext4.Array.pluck(this.getLayout().getLayoutItems(), 'itemId');
                },

                getCardItemIds : function()
                {
                    return this.cardItemIds;
                }
            });

            // stash the card itemId's for reference
            this.centerPanel.updateCardItemIds();
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
                    // TODO if we have changes, revert the panel back to initial values

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
                    this.fireEvent('apply', this, values);
                }
            });
        }

        return this.applyButton;
    }
});

Ext4.define('LABKEY.vis.LookAndFeelCardModel', {
    extend: 'Ext.data.Model',
    fields: [
        {name: 'name', type: 'string'},
        {name: 'label', type: 'string'},
        {name: 'cardId', type: 'string'},
        {name: 'cardClass', type: 'string'}
    ]
});