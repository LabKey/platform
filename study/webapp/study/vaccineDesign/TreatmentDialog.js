/*
 * Copyright (c) 2014-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.VaccineDesign.TreatmentDialog', {

    extend: 'Ext.window.Window',

    bodyStyle: 'overflow-y: auto; padding: 10px;',

    cls: 'treatment-dialog',

    initComponent : function()
    {
        this.items = this.getForm();
        this.callParent();
    },

    getForm : function()
    {
        if (!this.formPanel) {
            this.formPanel = Ext4.create('Ext.form.Panel',{
                border : false,
                layout : {
                    type : 'vbox',
                    align: 'center'
                },
                fieldDefaults  :{
                    labelAlign : 'top',
                    labelWidth : 130
                },
                items : this.getFormItems(),
                scope : this
            });
        }
        return this.formPanel;
    },

    getFormItems : function() {
        var productRolesItems = {}, columns = [];
        Ext4.each(this.productRoles, function(role){
            var checkedProducts = this.treatmentDetails ? this.treatmentDetails[role] : [];
            productRolesItems[role] = [];
            var values = this.getProductAndDoseRouteValues(role);
            Ext4.each(values, function(val){
                var checked = false;
                Ext4.each(checkedProducts, function(product){
                    if (product.ProductDoseRoute == val.ProductDoseRoute) {
                        checked = true;
                        return false;
                    }
                });
                productRolesItems[role].push({
                    boxLabel: val.Label,
                    name: role,
                    inputValue: val.ProductDoseRoute,
                    productLabel: val.ProductLabel,
                    checked: checked,
                    width: 350,
                    cls: 'dialog-product-label',
                    listeners: {
                        render: function (cmp)
                        {
                            //tooltip
                            cmp.getEl().dom.title = val.Label;
                        }
                    }
                });
            }, this);
        }, this);

        Ext4.iterate(productRolesItems, function(key, val){
            var column = {
                xtype: 'checkboxgroup',
                fieldLabel: key,
                colspan: 1,
                columns: 1,
                flex: 0.75,
                height: 22 * val.length + 22,
                style: 'margin-left: 20px;',
                items: val

            };
            columns.push(column);
        });
        return columns;
    },

    getProductAndDoseRouteValues : function(productRole)
    {
        var data = [];

        Ext4.each(Ext4.getStore('Product').getRange(), function(product)
        {
            if (!product.get('RowId') || product.get('Role') != productRole)
                return;
            var hasDoseRoute = false;
            Ext4.each(Ext4.getStore('DoseAndRoute').getRange(), function(dose)
            {
                if (dose.get('RowId') != null && dose.get('ProductId') == product.get('RowId') && product.get('Role') == productRole) {
                    var productDose = Ext4.clone(dose.data);
                    productDose.ProductLabel = product.get('Label');
                    productDose.Label = product.get('Label') + ' - ' + dose.get('Label');
                    productDose.ProductDoseRoute = dose.get('ProductId') + '-#-' + dose.get('Label');
                    data.push(productDose);
                    hasDoseRoute = true;
                }
            }, this);
            if (!hasDoseRoute)
            {
                var productDose = Ext4.clone(product.data);
                productDose.ProductLabel = product.get('Label');
                productDose.Label = product.get('Label');
                productDose.ProductDoseRoute = product.get('RowId') + '-#-';
                data.push(productDose);
            }
        }, this);

        return data;
    },

    getTreatmentFormValues: function() {
        var fields = this.getForm().getForm().getFields().items, treatmentLabel = '';
        for (var f = 0; f < fields.length; f++) {
            var field = fields[f];
            var data = field.getSubmitData();
            if (Ext4.isObject(data) && field.productLabel) {
                if (treatmentLabel != '') {
                    treatmentLabel += '|';
                }
                treatmentLabel += field.productLabel;
            }
        }

        var productValues = this.getForm().getValues(), treatment = {Label: treatmentLabel, Products: []};
        Ext4.iterate(productValues, function(key, val){
            treatment[key] = [];
            if (val) {
                if (Ext4.isArray(val)){
                    Ext4.each(val, function(v){
                        treatment[key].push({ProductDoseRoute: v});
                        treatment.Products.push({ProductDoseRoute: v});
                    })
                }
                else {
                    treatment[key].push({ProductDoseRoute: val});
                    treatment.Products.push({ProductDoseRoute: val});
                }
            }
        });
        return treatment;
    }
});

