/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.ChartTypePanel', {
    extend: 'LABKEY.vis.ChartWizardPanel',

    cls: 'chart-wizard-panel chart-type-panel',
    mainTitle: 'Create a plot',
    width: 900,
    selectedType: null,
    hideNonSelectedType: false,
    chartTypesToHide: null,
    selectedFields: null,
    requiredFieldNames: null,
    restrictColumnsEnabled: false,
    customRenderTypes: null,
    forceApplyChanges: false,
    baseQueryKey: null,
    studyQueryName: null,

    initComponent : function()
    {
        var typesArr = [];
        Ext4.each(LABKEY.vis.GenericChartHelper.getRenderTypes(), function(renderType)
        {
            // show the selected type and all non-hidden types (unless specifically requested to only show selected)
            var isSelectedType = this.selectedType == renderType.name,
                explicitlyHidden = Ext4.isArray(this.chartTypesToHide) && this.chartTypesToHide.indexOf(renderType.name) > -1,
                isNotHiddenType = !this.hideNonSelectedType && !renderType.hidden;

            if ((isSelectedType || isNotHiddenType) && !explicitlyHidden)
                typesArr.push(renderType);
        }, this);

        if (this.customRenderTypes)
        {
            Ext4.Object.each(this.customRenderTypes, function(key, properties)
            {
                if (!properties.name)
                    properties.name = key;
                if (!properties.title && properties.label)
                    properties.title = properties.label;
                typesArr.push(properties);
            }, this);
        }

        this.typesStore = Ext4.create('Ext.data.Store', {
            model: 'LABKEY.vis.ChartTypeModel',
            data: typesArr
        });

        this.height = Math.max((85 * this.typesStore.getCount()) + 185, 530);

        // lookup type by name
        if (Ext4.isString(this.selectedType))
            this.selectedType = this.typesStore.findRecord('name', this.selectedType, 0, false, true, true);
        // default to the first active chart type if none selected/found
        if (!this.selectedType)
            this.selectedType = this.typesStore.findRecord('active', true);

        // if no selectedFields pass in, create an empty object
        if (this.selectedFields == null)
            this.selectedFields = {};

        this.bottomButtons = [
            '->',
            this.getCancelButton(),
            this.getApplyButton()
        ];

        this.items = [
            this.getTitlePanel(),
            this.getTypesPanel(),
            this.getFieldMappingPanel(),
            this.getButtonBar()
        ];

        this.callParent();

        this.addEvents('cancel', 'apply');

        // on show, stash the initial values so we can use for comparison and cancel reset
        this.initValues = {};
        this.on('show', function()
        {
            // if we haven't populated the queryColumnsStore, make the request now
            if (this.getQueryColumnsStore().getCount() == 0 && Ext4.isString(this.schemaName) && Ext4.isString(this.queryName))
            {
                LABKEY.vis.GenericChartHelper.getQueryColumns(this, this.loadQueryColumns, this);
            }

            // if we haven't populated the studyColumnsStore, make the request now
            if (this.getStudyColumnsStore().getCount() == 0)
            {
                if (!Ext4.isIE) {
                    this.getStudyQueryCombo().getEl().mask('Loading study measures...', 'query-combo-mask');
                }

                LABKEY.vis.TimeChartHelper.getStudyMeasures(this.loadStudyColumns, this);
            }

            this.initValues = this.getValues();
        }, this);
    },

    getTypesPanel : function()
    {
        if (!this.typesPanel)
        {
            var tpl = new Ext4.XTemplate(
                '<tpl for=".">',
                    '<div class="item {[this.getItemCls(values)]}" id="chart-type-{name}">',
                        '<img src="{imgUrl}" height="50" width="80"/>',
                        '<div>{title}</div>',
                    '</div>',
                '</tpl>',
                {
                    getItemCls : function(item) {
                        return item.active ? 'item-active' : 'item-disabled';
                    }
                }
            );

            this.typesPanel = Ext4.create('Ext.view.View', {
                region: 'west',
                cls: 'region-panel types-panel',
                store: this.typesStore,
                tpl: tpl,
                itemSelector: 'div.item',
                listeners: {
                    scope: this,
                    beforeitemclick: this.allowTypeSelect,
                    selectionchange: this.selectChartType,
                    viewready: function(view)
                    {
                        // select the initial type, if not null
                        if (this.selectedType != null)
                        {
                            view.getSelectionModel().select(this.selectedType);
                        }
                        else
                        {
                            this.getFieldSelectionsPanel().add({
                                html: 'Select a chart type.'
                            });
                        }
                    }
                }
            });
        }

        return this.typesPanel;
    },

    getFieldMappingPanel : function()
    {
        if (!this.fieldMappingPanel)
        {
            this.fieldMappingPanel = Ext4.create('Ext.panel.Panel', {
                region: 'center',
                cls: 'region-panel mapping-panel',
                border: false,
                layout: {
                    type: 'column',
                    reserveScrollbar: true
                },
                autoScroll: true,
                items: [{
                    columnWidth: 0.5,
                    cls: 'field-selection-col',
                    border: false,
                    items: [this.getFieldSelectionsPanel()]
                },{
                    columnWidth: 0.49,
                    cls: 'mapping-query-col',
                    border: false,
                    hidden: true,
                    items: [this.getQueryColumnsPanel()]
                }]
            });
        }

        return this.fieldMappingPanel;
    },

    showQueryMappingPanelCol : function()
    {
        this.getStudyQueryComboContainer().setVisible(this.isTimeChartTypeSelected());
        this.getStudyColumnsGrid().getSelectionModel().deselectAll();
        this.getStudyColumnsGrid().setVisible(this.isTimeChartTypeSelected());

        this.getQueryColumnsGrid().getSelectionModel().deselectAll();
        this.getQueryColumnsGrid().setVisible(!this.isTimeChartTypeSelected());

        this.down('*[cls~=mapping-query-col]').show();
    },

    getQueryColumnsPanel : function()
    {
        if (!this.queryColumnsPanel)
        {
            this.queryColumnsPanel = Ext4.create('Ext.panel.Panel', {
                cls: 'query-columns',
                border: false,
                items: [
                    this.getStudyQueryComboContainer(),
                    this.getStudyColumnsGrid(),
                    this.getQueryColumnsGrid()
                ]
            });
        }

        return this.queryColumnsPanel;
    },

    getStudyQueryCombo : function()
    {
        if (!this.studyQueryCombo)
        {
            var store = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.SchemaModel',
                sorters: [{property: 'queryLabel'}]
            });

            this.studyQueryCombo = Ext4.create('Ext4.form.field.ComboBox', {
                hideFieldLabel: true,
                padding: '0 0 15px 0',
                width: 311,
                store: store,
                queryMode: 'local',
                editable: false,
                triggerAction: 'all',
                typeAhead: true,
                emptyText: 'Select a query',
                displayField: 'queryLabel',
                valueField: 'queryName',
                listeners   : {
                    scope : this,
                    change : this.filterStudyColumnsGrid
                }
            });
        }

        return this.studyQueryCombo;
    },

    getStudyQueryComboHelp : function()
    {
        if (!this.studyQueryComboHelp)
        {
            var subjectInfo = LABKEY.vis.TimeChartHelper.getStudySubjectInfo(),
                title = '<b>Which queries are included?</b>',
                msg = '<p>The time chart type is currently only supported for datasets/queries in the \'study\' schema.</p>'
                    + '<p>This list contains datasets which include columns that have been designated as \'measures\' from the dataset definition. '
                    + 'It also includes queries in the study schema that contain both the \'' + subjectInfo.nounSingular + 'Id\' and \''
                    + subjectInfo.nounSingular + 'Visit\' columns and have at least one \'measure\' column.</p>';

            this.studyQueryComboHelp = Ext4.create('Ext.Component', {
                padding: '2px 0 0 5px',
                html: '<i class="fa fa-question-circle"></i>',
                listeners: {
                    scope: this,
                    render: function(cmp)
                    {
                        Ext4.create('Ext.tip.ToolTip', {
                            target: cmp.getEl(),
                            autoHide: false,
                            width: 250,
                            html: title + msg
                        });
                    }
                }
            });
        }

        return this.studyQueryComboHelp;
    },

    getStudyQueryComboContainer : function()
    {
        if (!this.studyQueryComboContainer)
        {
            this.studyQueryComboContainer = Ext4.create('Ext.form.FieldContainer', {
                hidden: !this.isTimeChartTypeSelected(),
                width: 336,
                layout: 'hbox',
                items: [this.getStudyQueryCombo(), this.getStudyQueryComboHelp()]
            });
        }

        return this.studyQueryComboContainer;
    },

    getStudyColumnsStore : function()
    {
        if (!this.studyColumnsStore)
        {
            this.studyColumnsStore = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.QueryColumnModel',
                data: [],
                listeners: {
                    scope: this,
                    load: function(store)
                    {
                        var queryNameMap = {};
                        Ext4.each(store.getRange(), function(record){
                            if (!queryNameMap.hasOwnProperty(record.get('queryName')))
                            {
                                queryNameMap[record.get('queryName')] = LABKEY.vis.SchemaModel.create(record.data);
                                this.getStudyQueryCombo().getStore().add(queryNameMap[record.get('queryName')]);
                            }
                        }, this);

                        if (this.getStudyQueryCombo().getStore().findExact('queryName', this.studyQueryName) > -1)
                            this.getStudyQueryCombo().setValue(this.studyQueryName);
                        this.filterStudyColumnsGrid();

                        if (this.getStudyQueryCombo().getEl())
                            this.getStudyQueryCombo().getEl().unmask();
                    }
                },
                sorters: [{property: 'label'}]
            });
        }

        return this.studyColumnsStore;
    },

    filterStudyColumnsGrid : function()
    {
        // filter the study column store for the selected query
        var selectedQuery = this.getStudyQueryCombo().getValue();
        this.getStudyColumnsStore().clearFilter(true);
        this.getStudyColumnsStore().filter({property: 'queryName', value: selectedQuery, exactMatch: true});
    },

    loadStudyColumns : function(columns)
    {
        this.getStudyColumnsStore().loadRawData(columns);
    },

    getStudyColumnsGrid : function()
    {
        if (!this.studyColumnsGrid)
        {
            this.studyColumnsGrid = Ext4.create('Ext.grid.Panel', {
                hidden: !this.isTimeChartTypeSelected(),
                cls: 'study-columns-grid',
                store: this.getStudyColumnsStore(),
                autoScroll: true,
                height: this.height - 37/*studyQueryCombo height*/ - 170,
                enableColumnHide: false,
                columns: this.getGridColumnConfig(),
                viewConfig: { plugins: this.getGridViewDragPluginConfig() },
                stripeRows: true,
                selModel: new Ext4.selection.RowModel({ singleSelect: true }),
                allowDeselect: true,
                listeners: {
                    scope: this,
                    beforedeselect: function()
                    {
                        this.getFieldSelectionsPanel().destroyFieldSelectionDropTargets();
                    },
                    select: function(sm, selected)
                    {
                        this.showSelectionTargetsForColumn(this.studyColumnsGrid, selected);
                    }
                }
            });

            this.attachGridMouseEvents(this.studyColumnsGrid);
        }

        return this.studyColumnsGrid;
    },

    getQueryColumnsStore : function()
    {
        if (!this.queryColumnsStore)
        {
            this.queryColumnsStore = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.QueryColumnModel',
                proxy: {
                    type: 'memory',
                    reader: {
                        type: 'json'
                    }
                },
                listeners: {
                    scope: this,
                    load: function(store)
                    {
                        store.filterBy(function(record)
                        {
                            if (this.restrictColumnsEnabled && !(record.get('measure') || record.get('dimension')))
                                return false;

                            return true;
                        }, this);
                    }
                }
            });
        }

        return this.queryColumnsStore;
    },

    loadQueryColumns : function(columns)
    {
        this.getQueryColumnsStore().loadRawData(columns);
    },

    getQueryColumnNames : function()
    {
        return this.getQueryColumnsStore().collect('name');
    },

    getQueryColumnFieldKeys : function()
    {
        return this.getQueryColumnsStore().collect('fieldKey');
    },

    getQueryColumnsGrid : function()
    {
        if (!this.queryColumnsGrid)
        {
            this.queryColumnsGrid = Ext4.create('Ext.grid.Panel', {
                hidden: this.isTimeChartTypeSelected(),
                cls: 'query-columns-grid',
                store: this.getQueryColumnsStore(),
                autoScroll: true,
                height: this.height - 170,
                enableColumnHide: false,
                columns: this.getGridColumnConfig(),
                viewConfig: {
                    plugins: this.getGridViewDragPluginConfig(),
                    forceFit: true,
                },
                stripeRows: true,
                selModel: new Ext4.selection.RowModel({ singleSelect: true }),
                allowDeselect: true,
                listeners: {
                    scope: this,
                    beforedeselect: function()
                    {
                        this.getFieldSelectionsPanel().destroyFieldSelectionDropTargets();
                    },
                    select: function(sm, selected)
                    {
                        this.showSelectionTargetsForColumn(this.queryColumnsGrid, selected);
                    }
                }
            });

            this.attachGridMouseEvents(this.queryColumnsGrid);
        }

        return this.queryColumnsGrid;
    },

    getGridColumnConfig : function()
    {
        return [{
            header: 'Columns',
            dataIndex: 'label',
            menuDisabled: true,
            resizable: false,
            flex: 1,
            renderer: function(value)
            {
                return Ext4.util.Format.htmlEncode(value);
            }
        }];
    },

    attachGridMouseEvents : function(baseGrid)
    {
        // show allowable field targets on hover of grid row on mouseenter
        // and revert back to the selected field for allowable field targets on mouseleave
        var showHoverTargets = new Ext4.util.DelayedTask();
        var showSelectedTargets = new Ext4.util.DelayedTask();

        baseGrid.on('itemmouseenter', function(grid, record)
        {
            showSelectedTargets.cancel();
            showHoverTargets.delay(250, function(hoverRec) { this.showSelectionTargetsForColumn(grid, hoverRec); }, this, [record]);
        }, this);

        baseGrid.on('itemmouseleave', function(grid, record)
        {
            var selection = grid.getSelectionModel().getSelection();

            showHoverTargets.cancel();
            if (selection.length > 0)
                showSelectedTargets.delay(250, function () { this.showSelectionTargetsForColumn(grid, selection[0]); }, this);
            else
                showSelectedTargets.delay(250, function () { this.getFieldSelectionsPanel().destroyFieldSelectionDropTargets() }, this);
        }, this);
    },

    showSelectionTargetsForColumn : function(grid, col)
    {
        this.getFieldSelectionsPanel().destroyFieldSelectionDropTargets();
        var ddGroup = this.getGridViewDragPluginConfig().ddGroup;
        this.getFieldSelectionsPanel().addFieldSelectionDropTargets(ddGroup, col, grid);
    },

    getGridViewDragPluginConfig : function()
    {
        return {
            ddGroup: 'column-grid-to-field-selection',
            ptype: 'gridviewdragdrop',
            dragText: '1 column selected',
            enableDrop: false
        };
    },

    getFieldSelectionsPanel : function()
    {
        if (!this.fieldSelectionsPanel)
        {
            this.fieldSelectionsPanel = Ext4.create('LABKEY.vis.ChartTypeFieldSelectionsPanel', {
                chartType: this.selectedType,
                selection: this.selectedFields,
                baseQueryKey: this.baseQueryKey,
                listeners: {
                    scope: this,
                    selectionchange: this.fieldSelectionsChanged
                }
            });
        }

        return this.fieldSelectionsPanel;
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

    cancelHandler : function ()
    {
        // if we have changes, revert the panel back to initial values
        if (this.hasSelectionsChanged(this.getValues()))
        {
            this.selectedType = this.typesStore.findRecord('name', this.initValues.type, 0, false, true, true);
            this.getTypesPanel().getSelectionModel().select(this.selectedType);

            this.selectedFields = this.initValues.fields;
            this.selectedFields = Ext4.apply(this.selectedFields, this.initValues.altValues);
            this.getFieldSelectionsPanel().setSelection(this.selectedFields);
        }

        // deselect any grid columns
        this.getStudyColumnsGrid().getSelectionModel().deselectAll();
        this.getQueryColumnsGrid().getSelectionModel().deselectAll();

        this.fireEvent('cancel', this);
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
        // check required fields and if they all exist and nothing has changed, just treat this as a click on 'cancel'
        var values = this.getValues();
        if (!this.hasAllRequiredFields())
            this.getFieldSelectionsPanel().flagRequiredFields();
        else if (!this.hasSelectionsChanged(values))
            this.fireEvent('cancel', this);
        else
            this.fireEvent('apply', this, values);
    },

    setToForceApplyChanges : function()
    {
        this.forceApplyChanges = true;
    },

    hasSelectionsChanged : function(newValues)
    {
        if (this.forceApplyChanges)
        {
            this.forceApplyChanges = false;
            return true;
        }
        else if (Ext4.isObject(this.initValues) && Ext4.isObject(newValues))
        {
            if (this.initValues.type != newValues.type)
                return true;

            // see if any of the altValues key/values have changed
            if (Ext4.encode(this.initValues.altValues) != Ext4.encode(newValues.altValues))
                return true;

            // see if any of the field keys have changed
            if (!Ext4.Array.equals(Ext4.Object.getKeys(this.initValues.fields), Ext4.Object.getKeys(newValues.fields)))
                return true;

            // see if any of the field values have changed
            var initFieldNames = this.getFieldValueKeys(this.initValues.fields),
                newFieldNames = this.getFieldValueKeys(newValues.fields);
            if (!Ext4.Array.equals(initFieldNames, newFieldNames))
                return true;

            // see if any of the yAxis sides have changed
            var initYAxisSides = this.getFieldValueKeys(this.initValues.fields, 'yAxis'),
                newYAxisSides = this.getFieldValueKeys(newValues.fields, 'yAxis');
            if (!Ext4.Array.equals(initYAxisSides, newYAxisSides))
                return true;

            // see if any of the aggregate methods have changed
            var initAggregates = this.getFieldValueKeys(this.initValues.fields, 'aggregate'),
                    newAggregates = this.getFieldValueKeys(newValues.fields, 'aggregate');
            if (!Ext4.Array.equals(initAggregates, newAggregates))
                return true;
        }

        return false;
    },

    getFieldValueKeys : function(fieldValueMap, additionalPropName)
    {
        var keys = [];

        Ext4.Object.each(fieldValueMap, function(key, value){
            if (Ext4.isArray(value))
            {
                Ext4.each(value, function(v){
                    keys.push(this.getFieldValueKey(key, v, additionalPropName));
                }, this);
            }
            else
                keys.push(this.getFieldValueKey(key, value, additionalPropName));
        }, this);

        return keys;
    },

    getFieldValueKey : function(fieldName, value, additionalPropName)
    {
        var additionalPropVal = additionalPropName && value[additionalPropName] ? value[additionalPropName].value || value[additionalPropName] : '';
        return fieldName + '|' + value.queryName + '|' + value.name + '|' + additionalPropVal;
    },

    allowTypeSelect : function(view, selected)
    {
        return selected.get('active');
    },

    selectChartType : function(view, selected)
    {
        if (Ext4.isArray(selected) && selected.length == 1)
        {
            this.selectedType = selected[0];
            this.getFieldSelectionsPanel().update(this.selectedType);
            this.showQueryMappingPanelCol();

            // clear the required field names array so that it is recomputed next time it is accessed
            this.requiredFieldNames = null;

            // reset the selectedFields object since the type change may have removed some
            this.selectedFields = this.getFieldSelectionsPanel().getSelection();
        }
    },

    isTimeChartTypeSelected : function()
    {
        return this.selectedType && this.selectedType.get('name') == 'time_chart';
    },

    getRequiredFieldNames : function()
    {
        if (this.requiredFieldNames == null && this.selectedType != null)
        {
            this.requiredFieldNames = [];
            Ext4.each(this.selectedType.get('fields'), function (field)
            {
                if (field.required)
                    this.requiredFieldNames.push(field.name);
            }, this);
        }

        return this.requiredFieldNames || [];
    },

    hasAllRequiredFields : function()
    {
        var selectionKeys = Object.keys(this.getValues().fields).concat(Object.keys(this.getValues().altValues)),
            requiredKeysInSelection = Ext4.Array.intersect(this.getRequiredFieldNames(), selectionKeys);

        return this.getRequiredFieldNames().length == requiredKeysInSelection.length;
    },

    fieldSelectionsChanged : function()
    {
        this.selectedFields = this.getFieldSelectionsPanel().getSelection();
    },

    setFieldSelection : function(fieldName, measure)
    {
        this.selectedFields[fieldName] = measure;
    },

    getSelectedType : function()
    {
        return this.selectedType;
    },

    getValues : function()
    {
        return {
            type: this.selectedType != null ? this.selectedType.get('name') : null,
            fields: this.getSelectedFieldValues(),
            altValues: this.getAlternateFieldValues()
        };
    },

    getSelectedFieldValues : function()
    {
        var values = {};

        Ext4.each(this.selectedType.get('fields'), function(field)
        {
            var fieldName = field.name;
            if (!Ext4.isDefined(field.altFieldType) && Ext4.isDefined(this.selectedFields[fieldName]))
                values[fieldName] = Ext4.clone(this.selectedFields[fieldName]);
        }, this);

        return values;
    },

    getAlternateFieldValues : function()
    {
        var values = {};

        Ext4.each(this.selectedType.get('fields'), function(field)
        {
            if (Ext4.isDefined(field.altFieldType))
            {
                // first check the selectedFields object for the initValues case
                // otherwise get the value from the component itself
                var fieldName = field.name;
                if (Ext4.isDefined(this.selectedFields[fieldName]))
                    values[fieldName] = Ext4.clone(this.selectedFields[fieldName]);
                else if (field.altFieldCmp)
                    values[fieldName] = Ext4.clone(field.altFieldCmp.getValue());
            }
        }, this);

        return values;
    },

    getImgUrl : function()
    {
        if (this.selectedType != null)
        {
            return this.selectedType.get('imgUrl');
        }

        return this.typesStore.getAt(0).get('imgUrl');
    }
});

Ext4.define('LABKEY.vis.ChartTypeFieldSelectionsPanel', {
    extend: 'Ext.panel.Panel',

    cls: 'field-selections',
    border: false,
    defaults: {border: false},
    items: [],

    chartType: null,
    selection: null,
    baseQueryKey: null,
    fieldSelectionDropTargets: null,
    fieldSelectionAreas: null,

    initComponent : function() {
        this.fieldSelectionDropTargets = [];
        this.fieldSelectionAreas = {};

        this.callParent();

        this.on('render', this.initializeFieldDragZone, this);
    },

    addFieldSelectionDropTargets : function(ddGroup, selectedCol, grid, callbackFn, callbackScope)
    {
        var gridSelection = grid ? grid.getSelectionModel().getSelection() : [],
            selectedColData = selectedCol.data || selectedCol;

        // destroy any previous drop targets based on the last selected column
        this.destroyFieldSelectionDropTargets();

        // enable drop target based on allowable column types for the given field
        var selectedColType = LABKEY.vis.GenericChartHelper.getMeasureType(selectedColData),
            isMeasure = selectedColData['measure'],
            isDimension = selectedColData['dimension'];
        Ext4.each(this.query('charttypefield'), function(fieldSelPanel)
        {
            var hasMatchingType = fieldSelPanel.getAllowableTypes().indexOf(selectedColType) > -1,
                isMeasureDimensionMatch = this.isMeasureDimensionMatch(this.chartType.get('name'), fieldSelPanel, isMeasure, isDimension);

            if (hasMatchingType || isMeasureDimensionMatch)
            {
                var dropTarget = fieldSelPanel.createDropTarget(ddGroup, grid, callbackFn, callbackScope);

                // for automated test, allow click on field area to apply grid column selection
                if (gridSelection.length > 0)
                {
                    fieldSelPanel.getEl().on('click', function(event)
                    {
                        dropTarget.notifyDrop(null, event, {records: gridSelection});
                    }, this);
                }

                this.fieldSelectionDropTargets.push(dropTarget);
            }
        }, this);
    },

    isMeasureDimensionMatch : function(chartType, fieldSelPanel, isMeasure, isDimension)
    {
        if ((chartType === 'box_plot' || chartType === 'bar_chart'))
        {
            //x-axis does not support 'measure' column types for these plot types
            if (fieldSelPanel.field.name === 'x' || fieldSelPanel.field.name === 'xSub')
                return isDimension;
            else
                return isMeasure;
        }

        return (fieldSelPanel.field.numericOnly && isMeasure) || (fieldSelPanel.field.nonNumericOnly && isDimension);
    },

    destroyFieldSelectionDropTargets : function()
    {
        // remove the cls used for display drop target and remove any click listeners
        Ext4.each(this.query('charttypefield'), function(fieldSelPanel)
        {
            fieldSelPanel.removeDropTargetCls();
            fieldSelPanel.getEl().removeAllListeners();
        }, this);

        // destroy the actual DropTarget components
        Ext4.each(this.fieldSelectionDropTargets, function (dropTarget)
        {
            dropTarget.destroy();
        }, this);

        this.fieldSelectionDropTargets = [];
    },

    flagRequiredFields : function()
    {
        Ext4.each(this.query('charttypefield'), function(fieldSelPanel)
        {
            fieldSelPanel.flagIfRequired();
        }, this);
    },

    update : function(chartType)
    {
        if (Ext4.isDefined(chartType))
            this.chartType = chartType;

        this.removeAll();

        this.add(Ext4.create('Ext.Component', {
            cls: 'type-title',
            html: this.chartType.get('title')
        }));

        Ext4.each(this.chartType.get('fields'), function(field)
        {
            var fieldSelection = this.selection ? this.selection[field.name] : undefined;

            // drop the field selection from the previous chart type if the query key is not a match
            if (Ext4.isObject(fieldSelection) && Ext4.isString(fieldSelection.schemaName) && Ext4.isString(fieldSelection.queryName))
            {
                var queryKey = fieldSelection.schemaName + '.' + fieldSelection.queryName;
                if (this.baseQueryKey.toLowerCase() != queryKey.toLowerCase())
                    fieldSelection = undefined;
            }
            // drop the field selection if it is an array and the field doesn't support multi select
            else if (Ext4.isArray(fieldSelection) && !field.allowMultiple)
            {
                fieldSelection = undefined;
            }

            this.fieldSelectionAreas[field.name] = Ext4.create('LABKEY.vis.ChartTypeFieldSelectionPanel', {
                chartTypeName: this.chartType.get('name'),
                field: field,
                selection: fieldSelection
            });

            this.add(this.fieldSelectionAreas[field.name]);
        }, this);

        this.add(Ext4.create('Ext.Component', {
            cls: 'type-footer',
            html: '* Required fields'
        }));
    },

    initializeFieldDragZone : function() {
        var ddGroup = 'field-to-field-selection',
            me = this;

        // initialize the field selection area as a DragZone so that selected fields can be dragged
        // between field selection areas.
        this.dragZone = Ext4.create('Ext.dd.DragZone', this.getEl(), {
            ddGroup: ddGroup,
            repairHighlightColor: 'ffffff',

            getDragData: function(e) {
                var fieldDisplayEl = e.getTarget('div.field-selection-display'),
                    fieldTextEl = e.getTarget('div.field-selection-text');

                if (fieldDisplayEl && fieldTextEl) {
                    var fieldName = fieldDisplayEl.getAttribute('fieldName'),
                        recordData = me.selection[fieldName],
                        d = fieldTextEl.cloneNode(true);

                    d.id = Ext4.id();
                    d.className += ' chart-type-field-selection-drag';

                    me.dragData = {
                        ddel: d,
                        sourceEl: fieldDisplayEl,
                        repairXY: Ext4.fly(fieldDisplayEl).getXY(),
                        records: [recordData]
                    };

                    me.addFieldSelectionDropTargets(ddGroup, recordData, undefined, function(dropFieldCmp) {
                        this.destroyFieldSelectionDropTargets();
                        if (dropFieldCmp.field.name != fieldName) {
                            this.fieldSelectionAreas[fieldName].setSelection(null);
                        }
                    }, me);

                    return me.dragData;
                }
            },

            getRepairXY: function() {
                return me.dragData.repairXY;
            }
        });
    },

    setSelection : function(selection)
    {
        if (Ext4.isObject(selection))
        {
            this.selection = selection;
            this.update(this.chartType);
        }
    },

    getSelection : function()
    {
        this.selection = {};

        Ext4.each(this.query('charttypefield'), function(fieldSelPanel)
        {
            if (fieldSelPanel.getSelection() != null)
                this.selection[fieldSelPanel.field.name] = fieldSelPanel.getSelection();
        }, this);

        return this.selection;
    }
});

Ext4.define('LABKEY.vis.ChartTypeFieldSelectionPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.charttypefield',

    border: false,
    field: null,
    selection: null,
    allowableTypes: null,
    chartTypeName: null,

    initComponent : function()
    {
        // if we have an initial selection, make sure the type is a match
        if (this.selection != null)
        {
            var allowableTypes = this.getAllowableTypes();
            this.selection = this.getCurrentSelectionAsArray();

            // some fields allow multiple selection, so we have to check all in the array in that case
            var matchingSelection = [];
            Ext4.each(this.selection, function(selection)
            {
                var selectionType = LABKEY.vis.GenericChartHelper.getMeasureType(selection),
                        isMeasureDimensionMatch = (this.field.numericOnly && selection.measure) || (this.field.nonNumericOnly && selection.dimension);
                if (!Ext4.isDefined(selectionType) || allowableTypes.indexOf(selectionType) > -1 || isMeasureDimensionMatch)
                    matchingSelection.push(selection);
            }, this);

            if (matchingSelection.length == 0)
                this.selection = null;
            else
                this.selection = matchingSelection.length > 1 ? matchingSelection : matchingSelection[0];
        }

        var items = [
            this.getFieldTitle(),
            this.getFieldArea(),
            this.getFieldAreaDropText()
        ];

        this.items = items;
        this.callParent();

        this.addEvents('selectionchange');
        this.enableBubble('selectionchange');
    },

    getFieldTitle : function()
    {
        if (!this.fieldTitleCmp)
        {
            this.fieldTitleCmp = Ext4.create('Ext.Component', {
                cls: 'field-title',
                border: false
            });

            this.setFieldTitle();
        }

        return this.fieldTitleCmp;
    },

    setFieldTitle : function(title)
    {
        if (title)
            this.getFieldTitle().update(title);
        else
            this.getFieldTitle().update(this.field.label + (this.field.required ? ' *' : ''));
    },

    getFieldArea : function()
    {
        if (!this.fieldAreaCmp)
        {
            if (Ext4.isString(this.field.altFieldType))
            {
                this.field.altFieldCmp = Ext4.create(this.field.altFieldType, Ext4.apply({
                    cls: 'alternate-field-selection',
                    initData: this.selection
                }, this.field.altFieldConfig || {}));

                this.fieldAreaCmp = Ext4.create('Ext.panel.Panel', {
                    cls: 'field-area',
                    border: false,
                    minHeight: 50,
                    items: [this.field.altFieldCmp]
                });

                // clear the selection value for this altField
                this.selection = null;
            }
            else
            {
                var tpl = new Ext4.XTemplate(
                    '<tpl for=".">',
                        '<div class="field-selection-display" fieldName="' + this.field.name + '">',
                            '<div class="fa fa-times field-selection-remove" fieldName="{[this.getFieldName(values)]}"></div>',
                            '{[this.getFieldSelectionDisplay("' + this.chartTypeName + '", "' + this.field.name + '", values)]}',
                        '</div>',
                    '</tpl>',
                    {
                        getFieldSelectionDisplay: function (chartTypeName, fieldName, record)
                        {
                            var values = record.data || record,
                                label = '<div class="field-selection-text">'
                                    + Ext4.String.htmlEncode(LABKEY.vis.GenericChartHelper.getSelectedMeasureLabel(chartTypeName, fieldName, values))
                                    + '</div>';

                            if (chartTypeName == 'time_chart')
                            {
                                // for time chart, add query label to display since the UI allows cross query measure selection
                                label += '<div class="field-selection-subtext">Query: ' + Ext4.String.htmlEncode(values.queryLabel) + '</div>';

                                // also allow Y Axis Side selection for the measures
                                var leftSide = '<i class="fa fa-arrow-circle-left ' + (values.yAxis != 'left' ? 'unselected' : '') + '"></i>',
                                    rightSide = '<i class="fa fa-arrow-circle-right ' + (values.yAxis != 'right' ? 'unselected' : '') + '"></i>';
                                label += '<div class="field-selection-subtext">Y Axis Side: ' + leftSide + ' ' + rightSide + '</div>';
                            }
                            else if (chartTypeName == 'bar_chart' && fieldName == 'y')
                            {
                                label += '<div class="field-selection-subtext always-show"><span class="aggregate-method"></span></div>';
                            }

                            return label;
                        },

                        getFieldName: function(record)
                        {
                            var values = record.data || record;
                            return values.name;
                        }
                    }
                );

                var dataArr = [];
                if (Ext4.isObject(this.selection))
                    dataArr = [this.selection];
                else if (Ext4.isArray(this.selection))
                    dataArr = this.selection;

                this.fieldAreaCmp = Ext4.create('Ext.view.View', {
                    cls: 'field-area',
                    minHeight: 50,
                    store: Ext4.create('Ext.data.Store', {
                        model: 'LABKEY.vis.QueryColumnModel',
                        data: dataArr
                    }),
                    itemSelector: 'div.field-selection-display',
                    tpl: tpl,
                    listeners: {
                        scope: this,
                        beforeitemclick: function(view, record, item, index, event)
                        {
                            var targetTag = event.target.tagName,
                                targetCls = event.target.getAttribute('class');

                            if (targetCls.indexOf('field-selection-remove') > -1)
                            {
                                this.removeSelection(index, record);
                                return false;
                            }
                            else if (targetTag.toLowerCase() == 'i' && targetCls.indexOf('unselected') > -1)
                            {
                                this.toggleYAxisSide(index, record);
                                return false;
                            }
                        },
                        deselect: function(view) {
                            this.doLayout();// needed for resetting the field panel height
                        },
                        select: function(view, record)
                        {
                            view.select(record, false);// force single selection
                            this.doLayout();// needed for resetting the field panel height
                        }
                    }
                });

                // 'SIMPLE' mode allows for single click select and deselect
                this.fieldAreaCmp.getSelectionModel().setSelectionMode('SIMPLE');

                this.fieldAreaCmp.on('refresh', this.attachAggregateMethodCombo, this);
            }
        }

        return this.fieldAreaCmp;
    },

    attachAggregateMethodCombo : function(view) {
        var aggMethodEls = Ext4.DomQuery.select('span.aggregate-method', view.getEl().dom);
        if (aggMethodEls.length == 1) {
            Ext4.create('Ext.form.field.ComboBox', {
                renderTo: aggMethodEls[0],
                width: 270,
                labelWidth: 135,
                fieldLabel: 'Aggregate Method',
                queryMode: 'local',
                displayField: 'name',
                valueField: 'value',
                editable: false,
                value: this.selection.aggregate ? this.selection.aggregate.value || this.selection.aggregate : 'SUM',
                store: Ext4.create('Ext.data.Store', {
                    fields: ['name', 'value'],
                    data: [
                        {name: 'Count (non-blank)', value: 'COUNT'},
                        {name: 'Sum', value: 'SUM'},
                        {name: 'Min', value: 'MIN'},
                        {name: 'Max', value: 'MAX'},
                        {name: 'Mean', value: 'MEAN'},
                        {name: 'Median', value: 'MEDIAN'}
                    ]
                }),
                listeners: {
                    scope: this,
                    change: function(combo, newValue, oldValue) {
                        var rec = combo.getStore().findRecord('value', newValue);
                        this.selection.aggregate = rec != null ? rec.data : newValue;
                        var labelEls = Ext4.DomQuery.select('div.field-selection-text', view.getEl().dom);
                        if (labelEls.length > 0)
                        {
                            var aggLabelEl = labelEls[0];
                            aggLabelEl.innerText = combo.rawValue + ' of ' + this.selection.label;
                        }
                    },
                    render: function(){
                        this.doLayout(); // needed for resetting the field panel height
                    }
                }
            });
        }
    },

    getFieldAreaDropText : function()
    {
        if (!this.fieldAreaDropTextCmp)
        {
            this.fieldAreaDropTextCmp = Ext4.create('Ext.Component', {
                cls: 'field-area-drop-text',
                html: 'Drag and drop column here',
                hidden: true
            })
        }

        return this.fieldAreaDropTextCmp;
    },

    createDropTarget : function(ddGroup, grid, callbackFn, callbackScope)
    {
        var me = this;

        this.addCls('drop-target');
        if (this.getSelection() == null)
            this.getFieldAreaDropText().show();

        return new Ext4.dd.DropTarget(this.getEl(), {
            ddGroup: ddGroup,
            notifyEnter: function(ddSource, e, data)
            {
                me.addCls('drop-target-over');
            },
            notifyOut: function(ddSource, e, data)
            {
                me.removeCls('drop-target-over');
            },
            notifyDrop: function(ddSource, e, data)
            {
                me.setSelection(data.records[0]);
                me.removeCls('drop-target-over');
                if (grid) {
                    grid.getSelectionModel().deselectAll();
                }

                if (Ext4.isFunction(callbackFn)) {
                    callbackFn.call(callbackScope, me);
                }

                return true;
            }
        });
    },

    removeDropTargetCls : function()
    {
        this.removeCls('drop-target');
        this.getFieldAreaDropText().hide();
    },

    flagIfRequired : function()
    {
        var missingAltFieldValue = this.hasAlternateField() && this.field.altFieldCmp.getValue() == null,
            missingSelection = !this.hasAlternateField() && this.selection == null;

        if (this.field.required && (missingSelection || missingAltFieldValue))
        {
            this.addCls('missing-required');
            this.setFieldTitle(this.field.label + ' (Required)');
        }
    },

    hasAlternateField : function()
    {
        return Ext4.isDefined(this.field.altFieldCmp);
    },

    toggleYAxisSide : function(index, record)
    {
        var newSide = record.get('yAxis') == 'left' ? 'right' : 'left';

        if (Ext4.isObject(this.selection))
            this.selection.yAxis = newSide;
        else if (Ext4.isArray(this.selection))
            this.selection[index].yAxis = newSide;

        record.set('yAxis', newSide);
    },

    removeSelection : function(index, record)
    {
        // if the selection is an array, just remove the selected record
        // otherwise, we are clearing the only selection so reset to an empty field area
        if (Ext4.isArray(this.selection) && this.selection.length > 1)
        {
            this.selection.splice(index, 1);
            this.getFieldArea().getStore().remove(record);
            this.fireEvent('selectionchange', this);
        }
        else
        {
            this.setSelection(null);

            if (this.hasCls('drop-target'))
                this.getFieldAreaDropText().show();
        }
    },

    setSelection : function(column)
    {
        var newSelection = Ext4.clone(column && column.data ? column.data : column),
            newRecord;

        if (newSelection != null)
        {
            // default new selections to left side
            if (this.field.allowMultiple)
                newSelection.yAxis = 'left';

            if (this.field.allowMultiple && this.selection != null)
            {
                // first check if this column is already in the selection, in which case do nothing
                var selectionHasColumn = false;
                Ext4.each(this.getCurrentSelectionAsArray(), function(currSelection)
                {
                    if (currSelection.schemaName == newSelection.schemaName
                        && currSelection.queryName == newSelection.queryName
                        && currSelection.name == newSelection.name)
                    {
                        selectionHasColumn = true;
                        return false; // break
                    }
                });

                if (!selectionHasColumn)
                {
                    // either append this selection to the existing array or make a new array of the previous and new selections
                    if (Ext4.isArray(this.selection))
                        this.selection.push(newSelection);
                    else
                        this.selection = [this.selection, newSelection];

                    newRecord = this.getFieldArea().getStore().add(newSelection);
                }
            }
            else
            {
                this.selection = newSelection;

                this.getFieldArea().getStore().removeAll();
                newRecord = this.getFieldArea().getStore().add(newSelection);
            }

            // tell the view to select this new item
            if (this.field.allowMultiple && newRecord)
                this.getFieldArea().getSelectionModel().select(newRecord);

            this.removeCls('missing-required');
            this.setFieldTitle();
        }
        else
        {
            this.selection = null;
            this.getFieldArea().getStore().removeAll();
        }

        this.fireEvent('selectionchange', this);
    },

    getCurrentSelectionAsArray : function()
    {
        if (this.selection)
            return Ext4.isArray(this.selection) ? this.selection : [this.selection];

        return [];
    },

    getSelection : function()
    {
        return this.selection;
    },

    getAllowableTypes : function()
    {
        if (this.allowableTypes == null)
        {
            var numericTypes = ['int', 'float', 'double', 'INTEGER', 'DOUBLE'],
                nonNumericTypes = ['string', 'date', 'boolean', 'STRING', 'TEXT', 'DATE', 'BOOLEAN'],
                numericAndDateTypes = numericTypes.concat(['date','DATE']);

            if (this.field.altSelectionOnly)
                this.allowableTypes = [];
            else if (this.field.numericOnly)
                this.allowableTypes = numericTypes;
            else if (this.field.nonNumericOnly)
                this.allowableTypes = nonNumericTypes;
            else if (this.field.numericOrDateOnly)
                this.allowableTypes = numericAndDateTypes;
            else
                this.allowableTypes = numericTypes.concat(nonNumericTypes);
        }

        return this.allowableTypes;
    }
});

Ext4.define('LABKEY.vis.ChartTypeModel', {
    extend: 'Ext.data.Model',
    fields: [
        {name: 'name', type: 'string'},
        {name: 'title', type: 'string'},
        {name: 'hidden', type: 'boolean'},
        {name: 'imgUrl', type: 'string'},
        {name: 'active', type: 'boolean', defaultValue: true},
        // array of field selection object definitions of the type:
        // {name: 'x', label: 'X Axis Grouping', required: true, numericOnly: true, nonNumericOnly: true, allowMultiple: false}
        {name: 'fields', defaultValue: []},
        // object to control which chart layout options to show for the given chart type
        {name: 'layoutOptions', defaultValue: {}}
    ]
});

Ext4.define('LABKEY.vis.QueryColumnModel',{
    extend: 'Ext.data.Model',
    fields: [
        {name: 'schemaName', type: 'string', defaultValue: undefined},
        {name: 'queryName', type: 'string', defaultValue: undefined},
        {name: 'queryLabel', type: 'string', defaultValue: undefined},
        {name: 'alias', type: 'string', convert: function(value, record) {
            return value || record.data.name;
        }},
        {name: 'name', type: 'string'},
        {name: 'fieldKey', type: 'string'},
        {name: 'shortCaption', type: 'string', defaultValue: undefined},
        {name: 'label', type: 'string', convert: function(value, record) {
            return value || record.data.shortCaption;
        }},
        {name: 'hidden', type: 'boolean'},
        {name: 'isMeasure', type: 'boolean', defaultValue: undefined},
        {name: 'measure', type: 'boolean', convert: function(value, record) {
            return Ext4.isBoolean(value) ? value : record.data.isMeasure;
        }},
        {name: 'isDimension', type: 'boolean', defaultValue: undefined},
        {name: 'dimension', type: 'boolean', convert: function(value, record) {
            return Ext4.isBoolean(value) ? value : record.data.isDimension;
        }},
        {name: 'type'},
        {name: 'displayFieldJsonType'},
        {name: 'normalizedType', convert: function(value, record){
            // We take the displayFieldJSONType if available because if the column is a look up the record.type will
            // always be INT. The displayFieldJSONType is the actual type of the lookup.
            if (record.data.displayFieldJsonType)
                return record.data.displayFieldJsonType;

            return record.data.type;
        }},
        {name: 'yAxis', defaultValue: undefined},
        {name: 'aggregate', defaultValue: undefined}
    ]
});

Ext4.define('LABKEY.vis.SchemaModel',{
    extend: 'Ext.data.Model',
    fields: [
        {name: 'schemaName', type: 'string'},
        {name: 'queryName', type: 'string'},
        {name: 'queryLabel', type: 'string'}
    ]
});