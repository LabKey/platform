Ext4.ns('LABKEY.ext');

Ext4.define('LABKEY.ext.ModulePropertiesAdminPanel', {
    extend: 'Ext.form.Panel',
    config: {
        modules: null
    },
    initComponent: function(){
        Ext4.QuickTips.init();

        Ext4.apply(this, {
            bodyStyle: 'padding: 5px;',
            border: false,
            width: 800,
            items:  [{
                html: 'Loading...',
                border: false
            }],
            //buttonAlign: 'left',
            buttons: [{
                text: 'Save Changes',
                handler: this.onSubmit,
                scope: this
            }]
        });

        this.callParent(arguments);

        this.pendingRequests = this.modules.length;
        this.fetchProperties();
    },

    fetchProperties: function(){
        Ext4.each(this.modules, function(m){
            LABKEY.Ajax.request({
                url : LABKEY.ActionURL.buildURL('core', 'getModuleProperties', null),
                method : 'POST',
                scope: this,
                success: this.onLoad,
                failure: LABKEY.Utils.displayAjaxErrorResponse,
                jsonData: {
                    moduleName: m,
                    includePropertyDescriptors: true,
                    includePropertyValues: true
                },
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        }, this);
    },

    onLoad: function(result, opts){
        this.pendingRequests--;

        var module = opts.jsonData.moduleName;
        this.propertyMap = this.propertyMap || {};
        this.propertyMap[module] = Ext4.decode(result.responseText);

        if (this.pendingRequests == 0)
            this.renderForm();
    },

    renderForm: function(){
        var toAdd = [{
            html: 'This page allows you to view and set module properties.  Below are the properties that can be set.  For each property, you will see value set on the current folder and parent folders.  If you do not have permission to edit this property in any of those folders, the property will appear read-only.  To see more detail about each property, hover over the question mark next to the property name.',
            border: false,
            style: 'padding-bottom: 10px;'
        }];

        for(var module in this.propertyMap){
            toAdd.push({
                html: '<h3>Module: ' + module + '</h3>',
                border: false,
                style: 'padding-top: 10px;padding-bottom: 10px;'
            });

            for(var p in this.propertyMap[module].properties){
                toAdd.push(this.getEditorForProperty(module, p));
            }
        }
        this.removeAll();
        this.add(toAdd);
    },

    getEditorForProperty: function(module, name){
        var pd = this.propertyMap[module].properties[name];
        var values = this.propertyMap[module].values[name];

        var tooltip = [
            'Module: ' + pd.module,
            'Required: ' + pd.required,
            'Default Value: ' + (Ext4.isEmpty(pd.defaultValue) ? '' : pd.defaultValue),
            'Description: ' + (pd.description || ''),
            'Can Set Per Folder: ' + pd.canSetPerContainer,
            //'Can Set Per User: ' + pd.canSetPerUser,
            'Required: ' + pd.required
        ].join(',<br>');

        var cfg = {
            border: false,
            items: [{
                name: pd.name,
                html: '<i>Property: ' + pd.name + '</i><a href="#" data-qtip="' + tooltip + '"><span class="labkey-help-pop-up">?</span></a>',
                border: false,
                bodyStyle: 'padding-left: 5px; padding-bottom: 10px;'
            },{
                xtype: 'container',
                style: 'padding-left: 10px;',
                items: []
//                items: [{
//                    xtype: 'displayfield',
//                    fieldLabel: 'Effective Value',
//                    value: values.effectiveValue,
//                    style: 'margin-bottom: 5px;'
//                }]
            }]
        };

        Ext4.each(values.siteValues, function(v){
            cfg.items[1].items.push({
                fieldLabel: v.container.name || "Site Default",
                moduleName: module,
                moduleProp: v,
                propName: name,
                containerPath: v.container.path,
                xtype: (v.canEdit ? 'textfield' : 'displayfield'),
                value: v.value
            });
        }, this);

        return cfg;
    },

    onSubmit: function(btn){
        var toSave = [];
        this.getForm().getFields().each(function(item){
            if(item.isDirty()){
                toSave.push({
                    container: item.moduleProp.container.id,
                    userId: 0, //currently do not support individualized properties
                    value: item.getValue(),
                    propName: item.propName,
                    moduleName: item.moduleName
                })
            }
        }, this);

        if(!toSave.length){
            Ext4.Msg.alert('No changes', 'There are no changes to save.');
            return;
        }

        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL('core', 'saveModuleProperties', null),
            method : 'POST',
            scope: this,
            success: function(response){
                var response = Ext4.decode(response.responseText);
                if(response.success)
                    Ext4.Msg.alert('Success', 'Properties saved');
                else
                    Ext4.Msg.alert('Error', 'There was an error saving the properties');
            },
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            jsonData: {
                properties: toSave
            },
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }
});
