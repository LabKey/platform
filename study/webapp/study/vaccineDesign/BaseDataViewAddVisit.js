/*
 * Copyright (c) 2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.VaccineDesign.BaseDataViewAddVisit', {
    extend: 'LABKEY.VaccineDesign.BaseDataView',

    getVisitStore : function()
    {
        throw new Error("getVisitStore must be overridden in subclass");
    },

    //Override
    getAddNewRowTpl : function(columns, dataIndex)
    {
        var tplArr = [];

        if (!this.disableEdit)
        {
            tplArr.push('<tr>');
            tplArr.push('<td class="cell-display">&nbsp;</td>');
            tplArr.push('<td class="cell-display action" colspan="' + columns.length + '">');
            tplArr.push('<i class="' + this.ADD_ICON_CLS + ' add-new-row outer-add-new-row"> Add new row</i>&nbsp;&nbsp;&nbsp;');
            tplArr.push('<i class="' + this.ADD_ICON_CLS + ' add-visit-column"> Add new ' + this.visitNoun.toLowerCase() + '</i>');
            tplArr.push('</td>');
            tplArr.push('</tr>');
        }

        return tplArr;
    },

    //Override
    attachAddRowListeners : function(view)
    {
        this.callParent([view]);

        var addIconEls = Ext4.DomQuery.select('i.add-visit-column', view.getEl().dom);

        Ext4.each(addIconEls, function(addIconEl)
        {
            Ext4.get(addIconEl).on('click', this.addNewVisitColumn, this);
        }, this);
    },

    addNewVisitColumn : function()
    {
        var win = Ext4.create('LABKEY.VaccineDesign.VisitWindow', {
            title: 'Add ' + this.visitNoun,
            visitNoun: this.visitNoun,
            visitStore: this.getVisitStore(),
            listeners: {
                scope: this,
                closewindow: function(){
                    win.close();
                },
                selectexistingvisit: function(w, visitId){
                    win.close();

                    // if the 'ALL' option was selected, show all of the visits in the table
                    if (visitId == 'ALL')
                    {
                        Ext4.each(this.getVisitStore().getRange(), function(record)
                        {
                            record.set('Included', true);
                        }, this);
                    }
                    // set the selected visit to be included
                    else
                    {
                        this.getVisitStore().findRecord('RowId', visitId).set('Included', true);
                    }

                    this.updateDataViewTemplate();
                },
                newvisitcreated: function(w, newVisitData){
                    win.close();

                    // add the new visit to the store
                    var newVisitRec = LABKEY.VaccineDesign.Visit.create(newVisitData);
                    newVisitRec.set('Included', true);
                    this.getVisitStore().add(newVisitRec);

                    this.updateDataViewTemplate();
                }
            }
        });

        win.show();
    },

    updateDataViewTemplate : function()
    {
        // explicitly clear the column configs so the new visit column will be added
        this.columnConfigs = null;
        this.getDataView().setTemplate(this.getDataViewTpl());
    }
});