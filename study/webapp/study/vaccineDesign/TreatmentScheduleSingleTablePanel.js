/*
 * Copyright (c) 2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.VaccineDesign.TreatmentScheduleSingleTablePanel', {
    extend : 'LABKEY.VaccineDesign.TreatmentSchedulePanelBase',

    width: 1400,

    initComponent : function()
    {
        this.items = [this.getTreatmentScheduleGrid(), this.getButtonBar()];

        this.callParent();

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    getTreatmentScheduleGrid : function()
    {
        if (!this.treatmentScheduleGrid)
        {
            this.treatmentScheduleGrid = Ext4.create('LABKEY.VaccineDesign.TreatmentScheduleSingleTableGrid', {
                padding: '20px 0',
                disableEdit: this.disableEdit,
                subjectNoun: this.subjectNoun,
                visitNoun: this.visitNoun,
                productRoles: this.productRoles
            });

            this.treatmentScheduleGrid.on('dirtychange', this.enableSaveButton, this);
            this.treatmentScheduleGrid.on('celledited', this.enableSaveButton, this);
        }

        return this.treatmentScheduleGrid;
    }

});

Ext4.define('LABKEY.VaccineDesign.TreatmentScheduleSingleTableGrid', {
    extend : 'LABKEY.VaccineDesign.TreatmentScheduleGridBase',

    studyDesignQueryNames : ['StudyDesignRoutes', 'Product', 'DoseAndRoute'],

    //Override
    onStudyTreatmentScheduleStoreLoad : function()
    {
        this.getTreatmentsStore();
    },

    getTreatmentsStore : function()
    {
        if (!this.treatmentsStore)
        {
            var me = this;
            this.treatmentsStore = Ext4.create('Ext.data.Store', {
                storeId : 'TreatmentsGridStore',
                model : 'LABKEY.VaccineDesign.Treatment',
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL("study-design", "getStudyTreatments", null, {splitByRole: true}),
                    reader: {
                        type: 'json',
                        root: 'treatments'
                    }
                },
                sorters: [{ property: 'RowId', direction: 'ASC' }],
                autoLoad: true,
                listeners: {
                    scope: this,
                    load: function (store)
                    {
                        me.getStore().fireEvent('load', me.getStore());
                    }
                }
            });
        }

        return this.treatmentsStore;
    },

    getTreatmentFieldConfig : function()
    {
        var me = this;
        return {
            hideFieldLabel: true,
            name: 'VisitMap',
            width: 135,
            readOnly: true,
            enableKeyEvents: false,
            cls: 'treatment-input-cell',
            listeners: {
                render: function(cmp) {
                    if (cmp.value)
                        cmp.getEl().dom.title = cmp.value; //tooltip

                    cmp.getEl().on('click', function(){
                        if (me.productRoles == null || me.productRoles.length == 0) {
                            Ext4.Msg.show({
                                title: 'Error',
                                msg: 'No study products have been defined.',
                                icon: Ext4.Msg.ERROR,
                                buttons: Ext4.Msg.OK
                            });
                            return;
                        }

                        var win;
                        var popupConfig = {
                            productRoles: me.productRoles,
                            autoScroll  : true,
                            buttonAlign : 'right',
                            modal: true,
                            width: 400,
                            height: 500,
                            border: false,
                            closable: false,
                            title: 'Treatment',
                            draggable: false,
                            buttons: [{
                                text: 'Cancel',
                                onClick : function () {
                                    win.close();
                                }
                            },{
                                text: 'OK',
                                cls: 'commentSubmit',
                                onClick : function () {
                                    var isFormDirty = win.getForm().getForm().isDirty();
                                    if (!isFormDirty) {
                                        win.close();
                                        return;
                                    }

                                    var treatment = win.getTreatmentFormValues();
                                    if (treatment && treatment.Products.length == 0) {
                                        win.close();
                                        cmp.treatmentId = null;
                                        cmp.setValue(null);
                                        cmp.getEl().dom.title = '';
                                        return;
                                    }
                                    var treatments = [treatment];
                                    LABKEY.Ajax.request({
                                        url     : LABKEY.ActionURL.buildURL('study-design', 'updateTreatments.api'),
                                        method  : 'POST',
                                        jsonData: {
                                            treatments: treatments
                                        },
                                        scope: this,
                                        success: function(response)
                                        {
                                            var resp = Ext4.decode(response.responseText);
                                            if (resp.success) {
                                                win.close();
                                                me.fireEvent('celledited');
                                                cmp.treatmentId = resp.treatmentIds[0];
                                                cmp.setValue(treatments[0].Label);
                                                cmp.getEl().dom.title = treatments[0].Label;
                                                me.getTreatmentsStore().load();
                                            }
                                            else
                                                this.onFailure();
                                        },
                                        failure: function(response)
                                        {
                                            var resp = Ext4.decode(response.responseText);
                                            if (resp.errors)
                                                this.onFailure(Ext4.Array.pluck(resp.errors, 'message').join('<br/>'));
                                            else
                                                this.onFailure(resp.exception);
                                        }
                                    });

                                }
                            }]

                        };
                        if (cmp.treatmentId) {
                            Ext4.Ajax.request({
                                url : LABKEY.ActionURL.buildURL("study-design", "getStudyTreatments", null, {splitByRole: true, treatmentId: cmp.treatmentId}),
                                method : 'POST',
                                success: LABKEY.Utils.getCallbackWrapper(function(response){
                                    popupConfig.treatmentDetails = response.treatments.length > 0 ? response.treatments[0] : null;
                                    win = new LABKEY.VaccineDesign.TreatmentDialog(popupConfig);
                                    win.show();

                                }, me)
                            });
                        }
                        else {
                            win = new LABKEY.VaccineDesign.TreatmentDialog(popupConfig);
                            win.show();
                        }
                    });
                }
            }
        };
    },

    //Override
    getTreatmentFieldEditorType: function()
    {
        return 'Ext.form.field.Text';
    },

    //Override
    isFieldTreatmentLookup: function()
    {
        return true;
    },

    //Override
    getTreatmentValue : function(column, newValue, field) {
        var value = newValue;
        if (column.lookupStoreId && field && field.treatmentId)
            value = field.treatmentId;
        return value;
    }
});