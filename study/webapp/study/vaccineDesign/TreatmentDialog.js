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
                layout : 'hbox',
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
                    checked: checked,
                    width: 170,
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
            Ext4.each(Ext4.getStore('DoseAndRoute').getRange(), function(dose)
            {
                if (dose.get('RowId') != null && dose.get('ProductId') == product.get('RowId') && product.get('Role') == productRole) {
                    var productDose = Ext4.clone(dose.data);
                    productDose.Label = product.get('Label') + ' - ' + dose.get('Label');
                    productDose.ProductDoseRoute = dose.get('ProductId') + '-#-' + dose.get('Label');
                    data.push(productDose);
                }
            }, this);
        }, this);

        return data;
    },

    getTreatmentFormValues: function() {
        var productValues = this.getForm().getValues(), params = {};
        Ext4.iterate(productValues, function(key, val){
            params[key] = [];
            if (val) {
                if (Ext4.isArray(val)){
                    Ext4.each(val, function(v){
                        params[key].push({ProductDoseRoute: v})
                    })
                }
                else {
                    params[key].push({ProductDoseRoute: val})
                }
            }
        });
        return params;
    }
});

