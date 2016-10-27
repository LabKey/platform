/*
 * Copyright (c) 2014-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.VaccineDesign.TreatmentDialog', {

    extend: 'Ext.window.Window',

    bodyStyle: 'overflow-y: auto; padding: 10px;',

    cls: 'treatment-dialog',

    listeners: {
        resize: function (cmp)
        {
            cmp.doLayout();
        }
    },

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
                    align: 'stretch'
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
            var values = this.getProductAndDoseRouteValues(role, checkedProducts);
            Ext4.each(values, function(val){
                var checked = false;
                Ext4.each(checkedProducts, function(product){
                    if (product.ProductDoseRoute == val.ProductDoseRoute) {
                        checked = true;
                        return false;
                    }
                });
                productRolesItems[role].push({
                    boxLabel: Ext4.util.Format.htmlEncode(val.Label),
                    name: role,
                    inputValue: val.ProductDoseRoute,
                    productLabel: val.ProductLabel,
                    checked: checked,
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

    getProductAndDoseRouteValues : function(productRole, checkedProducts)
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
            else {
                //Issue 28273: No products selected in Treatment dialog for single table manage treatment page if product does not have dose/route selected
                Ext4.each(checkedProducts, function(checked){
                    if (checked.ProductId == product.get('RowId') && !checked.DoseAndRoute && !checked.Dose && !checked.Route)
                    {
                        var productDose = Ext4.clone(checked);
                        productDose.ProductLabel = checked['ProductId/Label'];
                        productDose.Label = checked['ProductId/Label'];
                        data.push(productDose);
                    }
                });
            }
        }, this);

        var me = this;
        data.sort(function(a, b){
            // if different product, sort by product
            if (a.ProductLabel && b.ProductLabel && a.ProductLabel != b.ProductLabel) {
               return me.natural(a.ProductLabel, b.ProductLabel);
            }
            // if same product, sort by dose/route
            var doseA = a.ProductDoseRoute.replace(a.ProductLabel, ''), doseB = b.ProductDoseRoute.replace(b.ProductLabel, '');
            return me.natural(doseA, doseB);
        });

        return data;
    },

    natural : function (aso, bso) {
        // http://stackoverflow.com/questions/19247495/alphanumeric-sorting-an-array-in-javascript
        var a, b, a1, b1, i= 0, n, L,
                rx=/(\.\d+)|(\d+(\.\d+)?)|([^\d.]+)|(\.\D+)|(\.$)/g;
        if (aso === bso) return 0;
        a = aso.toLowerCase().match(rx);
        b = bso.toLowerCase().match(rx);

        if (a == 'null' || b == 'null') {
            var aEmpty = a == 'null';
            var bEmpty = b == 'null';

            // both are empty
            if (aEmpty && bEmpty) {
                return 0;
            }

            return aEmpty ? -1 : 1;
        }

        L = a.length;
        while (i < L) {
            if (!b[i]) return 1;
            a1 = a[i]; b1 = b[i++];
            if (a1 !== b1) {
                n = a1 - b1;
                if (!isNaN(n)) return n;
                return a1 > b1 ? 1 : -1;
            }
        }
        return b[i] ? -1 : 0;
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

