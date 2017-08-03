/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.ChartLayoutPanel', {
    extend: 'LABKEY.vis.ChartWizardPanel',

    cls: 'chart-wizard-panel chart-layout-panel',
    mainTitle: 'Customize look and feel',
    width: 900,
    height: 530,
    isDeveloper: false,
    renderType: null,
    initMeasures: null,
    defaultChartLabel: null,
    defaultOpacity: null,
    defaultLineWidth: null,
    isSavedReport: false,

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
        this.on('show', function(panel, selectedChartType, measures) {
            this.initValues = this.getValues();
            this.requiresDataRefresh = false;
            this.updateVisibleLayoutOptions(selectedChartType, measures);
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
                    defaultChartLabel: this.defaultChartLabel,
                    defaultOpacity: this.defaultOpacity,
                    defaultLineWidth: this.defaultLineWidth,
                    isSavedReport: this.isSavedReport,
                    renderType: this.renderType,
                    initMeasures: this.initMeasures,
                    listeners: {
                        scope: this,
                        chartLayoutChange: function(newChartLayout) {
                            this.onChartLayoutChange(newChartLayout != 'single');
                        }
                    }
                }
            },{
                name: 'x',
                cardId: 'card-2',
                label: 'X-Axis',
                layoutOptions: 'axisBased',
                cardClass: 'LABKEY.vis.GenericChartAxisPanel',
                config: {
                    axisName: 'x',
                    multipleCharts: this.multipleCharts,
                    isSavedReport: this.isSavedReport,
                    renderType: this.renderType
                }
            },{
                name: 'y',
                cardId: 'card-3',
                label: 'Y-Axis',
                layoutOptions: 'axisBased',
                cardClass: 'LABKEY.vis.GenericChartAxisPanel',
                config: {
                    axisName: 'y',
                    multipleCharts: this.multipleCharts,
                    isSavedReport: this.isSavedReport,
                    renderType: this.renderType
                }
            },{
                name: 'yRight',
                cardId: 'card-4',
                label: 'Y-Axis (Right)',
                layoutOptions: 'axisBased',
                cardClass: 'LABKEY.vis.GenericChartAxisPanel',
                config: {
                    axisName: 'yRight',
                    multipleCharts: this.multipleCharts,
                    isSavedReport: this.isSavedReport,
                    renderType: this.renderType
                }
            }];

            if (this.isDeveloper)
            {
                data.push({
                    name: 'developer',
                    label: 'Developer',
                    cardId: 'card-5',
                    layoutOptions: ['point', 'time', 'series'],
                    cardClass: 'LABKEY.vis.DeveloperOptionsPanel',
                    config: {
                        isDeveloper: this.isDeveloper,
                        renderType: this.renderType
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
                        '<div class="item" style="{[this.getDisplayStyle(values)]}">{label:htmlEncode}</div>',
                    '</tpl>',
                    {
                        getDisplayStyle : function(values)
                        {
                            return values.visible ? 'display: block;' : 'display: none;';
                        }
                    }
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
                layoutOptions: record.get('layoutOptions'),
                autoScroll: true
            }, record.get('config'));

            newCardPanel = Ext4.create(record.get('cardClass'), config);

            // set initial values based on the config props loaded from the saved config
            if (newCardPanel.setPanelOptionValues)
                newCardPanel.setPanelOptionValues(this.options[record.get('name')]);

            newCardPanel.on('requiresDataRefresh', function(){
                this.requiresDataRefresh = true;
            }, this);
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
                handler: this.cancelHandler
            });
        }

        return this.cancelButton;
    },

    cancelHandler : function()
    {
        // if we have changes, revert the panels back to initial values
        if (this.hasSelectionsChanged(this.getValues()))
        {
            Ext4.each(this.getCenterPanel().items.items, function(panel)
            {
                if (!panel.isDisabled() && panel.setPanelOptionValues)
                {
                    this.getCenterPanel().getLayout().setActiveItem(panel.itemId);
                    panel.setPanelOptionValues(this.initValues[panel.panelName]);
                }
            }, this);
        }

        this.fireButtonEvent('cancel');
    },

    fireButtonEvent : function(eventName, param1, param2)
    {
        this.returnToFirstPanel();
        this.fireEvent(eventName, this, param1, param2);
    },

    returnToFirstPanel : function()
    {
        // change the selected panel back to the first item
        this.getNavigationPanel().getSelectionModel().select(0);
        this.getCenterPanel().getLayout().setActiveItem(0);
    },

    getApplyButton : function()
    {
        if (!this.applyButton)
        {
            this.applyButton = Ext4.create('Ext.button.Button', {
                text: 'Apply',
                scope: this,
                handler: this.applyHandler
            });
        }

        return this.applyButton;
    },

    applyHandler : function()
    {
        var values = this.getValues();

        // if nothing has changed, just treat this as a click on 'cancel'
        if (!this.hasSelectionsChanged(values))
        {
            this.fireButtonEvent('cancel');
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
            {
                this.fireButtonEvent('apply', values, this.requiresDataRefresh);
            }
        }
    },

    updateVisibleLayoutOptions : function(selectedChartType, measures)
    {
        // allow the selected chart type to dictate which layout options are visible
        var chartTypeLayoutOptions = selectedChartType != null && Ext4.isObject(selectedChartType.layoutOptions) ? selectedChartType.layoutOptions : null;
        if (chartTypeLayoutOptions != null)
        {
            Ext4.each(this.getCenterPanel().items.items, function(panel)
            {
                // hide/show the whole center panel based on if it has a specific layoutOption type
                var navRecord = this.getNavigationPanel().getStore().findRecord('cardId', panel.itemId),
                    includeLayoutPanel = (panel.layoutOptions == null || this.hasMatchingLayoutOption(chartTypeLayoutOptions, panel.layoutOptions)) || false;

                // special case for axis based panels to hide/show based on selected measures
                if (includeLayoutPanel && panel.hasOwnProperty('axisName'))
                    includeLayoutPanel = this.shouldIncludeAxisPanel(selectedChartType, measures, panel.axisName);

                panel.setDisabled(!includeLayoutPanel);
                navRecord.set('visible', includeLayoutPanel);

                this.updateNavPanelTitle(selectedChartType, measures, navRecord);

                // hide/show individual panel items based on their specific layoutOption type
                if (panel.getInputFields)
                {
                    Ext4.each(panel.getInputFields(), function(inputField)
                    {
                        var includeLayoutField = inputField.hideForDatatype ? false : this.hasMatchingLayoutOption(chartTypeLayoutOptions, inputField.layoutOptions);
                        inputField.setVisible(includeLayoutField);
                    }, this);
                }
            }, this);
        }
    },

    shouldIncludeAxisPanel : function(selectedChartType, measures, axisName)
    {
        if (selectedChartType.name == 'time_chart')
        {
            var sides = LABKEY.vis.TimeChartHelper.getDistinctYAxisSides(measures),
                showX = axisName == 'x',
                showLeft = axisName == 'y' && sides.indexOf('left') > -1,
                showRight = axisName == 'yRight' && sides.indexOf('right') > -1;

            return showX || showLeft || showRight;
        }
        else
        {
            return Ext4.Object.getKeys(measures).indexOf(axisName) > -1;
        }
    },

    updateNavPanelTitle : function(selectedChartType, measures, navRecord)
    {
        // for time charts, if both y-axis sides are in use, update the labels to make it clear
        if (selectedChartType.name == 'time_chart' && (navRecord.get('name') == 'y' || navRecord.get('name') == 'yRight'))
        {
            var sides = LABKEY.vis.TimeChartHelper.getDistinctYAxisSides(measures),
                label = sides.length == 2 ? ' (' + (navRecord.get('name') == 'y' ? 'Left' : 'Right') + ')' : '';
            navRecord.set('label', 'Y-Axis' + label);
        }
    },

    hasMatchingLayoutOption : function(expectedOptionsMap, layoutOptions)
    {
        if (Ext4.isString(layoutOptions))
        {
            return expectedOptionsMap.hasOwnProperty(layoutOptions) && expectedOptionsMap[layoutOptions];
        }
        else if (Ext4.isArray(layoutOptions))
        {
            var includeLayoutField = false;
            Ext4.each(layoutOptions, function(fieldLayoutOption) {
                if (expectedOptionsMap[fieldLayoutOption])
                {
                    includeLayoutField = true;
                    return false; // break
                }
            });
            return includeLayoutField;
        }

        return true;
    },

    getOptionPanelByName : function(name)
    {
        var matchingPanel = null;

        Ext4.each(this.getCenterPanel().items.items, function(panel)
        {
            if (panel.panelName == name)
            {
                matchingPanel = panel;
                return false; // break
            }
        }, this);

        return matchingPanel;
    },

    getValues : function()
    {
        var values = {};

        Ext4.each(this.getCenterPanel().items.items, function(panel)
        {
            // if the panel is disabled, don't return the values
            if (panel.isDisabled())
                values[panel.panelName] = undefined;
            else if (panel.getPanelOptionValues)
                values[panel.panelName] = panel.getPanelOptionValues();
        });

        return values;
    },

    hasSelectionsChanged : function(values)
    {
        // first check if we need to refresh the data because of a change
        if (this.requiresDataRefresh)
            return true;

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
            var skipUndefined = key == 'yRight' && !Ext4.isDefined(value);
            var encodedObjectMatch = skipUndefined || Ext4.encode(value) == Ext4.encode(this.initValues[key]);

            if (!encodedObjectMatch)
            {
                hasChanges = true;
                return false; // break
            }
        }, this);

        return hasChanges;
    },

    onMeasuresChange : function(measures, renderType)
    {
        // give each of the layout options tabs a chance to update on measure change
        for (var i = 0; i < this.getNavigationPanel().getStore().getCount(); i++)
        {
            var generalOptionsPanel = this.getCenterPanel().getLayout().getLayoutItems()[i];
            if (generalOptionsPanel.onMeasureChange)
                generalOptionsPanel.onMeasureChange(measures, renderType);
        }
    },

    onChartSubjectSelectionChange : function(asGroups)
    {
        // give each of the layout options tabs a chance to update on subject selection change
        for (var i = 0; i < this.getNavigationPanel().getStore().getCount(); i++)
        {
            var generalOptionsPanel = this.getCenterPanel().getLayout().getLayoutItems()[i];
            if (generalOptionsPanel.onChartSubjectSelectionChange)
                generalOptionsPanel.onChartSubjectSelectionChange(asGroups);
        }
    },

    onChartLayoutChange : function(multipleCharts)
    {
        // give each of the layout options tabs a chance to update on chart layout change
        for (var i = 0; i < this.getNavigationPanel().getStore().getCount(); i++)
        {
            var generalOptionsPanel = this.getCenterPanel().getLayout().getLayoutItems()[i];
            if (generalOptionsPanel.onChartLayoutChange)
                generalOptionsPanel.onChartLayoutChange(multipleCharts);
        }
    }
});

Ext4.define('LABKEY.vis.ChartLayoutCardModel', {
    extend: 'Ext.data.Model',
    fields: [
        {name: 'name', type: 'string'},
        {name: 'label', type: 'string'},
        {name: 'cardId', type: 'string'},
        {name: 'cardClass', type: 'string'},
        {name: 'visible', type: 'boolean', defaultValue: true},
        {name: 'layoutOptions', defaultValue: null},
        {name: 'config'}
    ]
});