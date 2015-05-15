/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Legacy style date picker, renders separate day, month, year combos.
 */
Ext4.define('LABKEY.ext4.form.field.DatePicker', {
    extend: 'Ext.form.FieldContainer',
    alias: 'widget.lkdatepicker',

    constructor: function (config)
    {
        Ext4.applyIf(config, {
            frame   : false,
            border  : false,
            width   : 350,
            layout  : 'hbox',
            startYear   : 1925,
            endYear     : 2015,
            defaultYear : 1970
        });

        this.monthMap = {
            'January'   : 31,
            'February'  : 28,
            'March'     : 31,
            'April'     : 30,
            'May'       : 31,
            'June'      : 30,
            'July'      : 31,
            'August'    : 31,
            'September' : 30,
            'October'   : 31,
            'November'  : 31,
            'December'  : 30
        };
        this.callParent([config]);
    },

    initComponent: function ()
    {
        // initialize the combo stores
        var months = [];
        // convert any lookup display values back to keys
        for (var key in this.monthMap){
            if (this.monthMap.hasOwnProperty(key)){

                months. push({name : key, value : this.monthMap[key]});
            }
        }

        var years = [];
        for (var year = this.startYear; year < this.endYear; year++){
            years.push({name : year, value : year});
        }

        this.items = [{
            xtype   : 'combo',
            itemId  : 'monthCombo',
            value   : 'January',
            store : {
                fields  : ['name', 'value'],
                data    : months
            },
            valueField      : 'name',
            displayField    : 'name',
            editable        : false,
            flex            : 1.3,
            scope           : this,
            listeners       : {
                change : {
                    fn : function(cmp, newVal, oldVal){
                        this.month = newVal;
                        this.updateDayCombo();
                    },
                    scope : this
                }
            }
        },{
            xtype   : 'combo',
            itemId  : 'dayCombo',
            store : {
                fields  : ['name', 'value'],
                data    : this.createDayStore('January')
            },
            valueField      : 'value',
            displayField    : 'name',
            editable        : false,
            flex            : 1,
            scope           : this,
            listeners       : {
                change : {
                    fn : function(cmp, newVal, oldVal){
                        this.day = newVal;
                    },
                    scope : this
                }
            }
        },{
            xtype   : 'combo',
            store : {
                fields  : ['name', 'value'],
                data    : years
            },
            valueField      : 'name',
            displayField    : 'name',
            value           : this.defaultYear,
            editable        : false,
            flex            : 1.1,
            scope           : this,
            listeners       : {
                change : {
                    fn : function(cmp, newVal, oldVal){
                        this.year = newVal;
                        if (this.isLeap()){
                            this.updateDayCombo();
                        }
                    },
                    scope : this
                }
            }
        }];
        this.callParent();
    },

    updateDayCombo : function() {
        var data = this.createDayStore(this.month);
        var combo = this.getComponent('dayCombo');
        if (combo){
            combo.clearValue();
            combo.getStore().loadData(data);
        }
    },

    isLeap : function() {

        return (this.month === 'February' && ((this.year % 4) == 0));
    },

    createDayStore : function(month){

        var days = [];

        for (var i=1; i <= this.monthMap[month]; i++){
            days.push({name : i, value : i});
        }

        // check leap year
        if (this.isLeap()){
            days.push({name : 29, value : 29});
        }
        return days;
    }
});