
Ext4.define('LABKEY.VaccineDesign.Utils', {

    singleton: true,

    /**
     * Helper function to get field editor config object for a study design lookup combo or a text field.
     * @param name Field name
     * @param required Whether or not this field should be allowed to be blank
     * @param width Field width
     * @param queryName If combo, the queryName for the store
     * @param displayField The field name of the combo store displayField
     * @returns {Object} Field config
     */
    getStudyDesignFieldEditorConfig : function(name, required, width, queryName, displayField)
    {
        if (queryName != undefined && queryName != null)
        {
            // config for LABKEY.ext4.ComboBox
            return {
                hideFieldLabel: true,
                name: name,
                width: width || 150,
                allowBlank: !required,
                forceSelection : false, // allow usage of inactive types
                editable : false,
                queryMode : 'local',
                // TODO: this does not htmlEncode the display value in expanded options list
                displayField : displayField || 'Label',
                valueField : 'Name',
                store : LABKEY.VaccineDesign.Utils.getStudyDesignStore(queryName)
            };
        }
        else
        {
            // config for Ext.form.field.Text
            return {
                hideFieldLabel: true,
                name: name,
                width: width || 150,
                allowBlank: !required
            }
        }
    },

    /**
     * Create a new LABKEY.ext4.Store for the given queryName from the study schema.
     * @param queryName
     * @returns {LABKEY.ext4.Store}
     */
    getStudyDesignStore : function(queryName)
    {
        var store = Ext4.getStore(queryName);
        if (Ext4.isDefined(store))
            return store;

        return Ext4.create('LABKEY.ext4.Store', {
            storeId: queryName,
            schemaName: 'study',
            queryName: queryName,
            columns: 'Name,Label',
            filterArray: [LABKEY.Filter.create('Inactive', false)],
            containerFilter: LABKEY.container.type == 'project' ? 'Current' : 'CurrentPlusProject',
            sort: '-Container/Path,Label',
            autoLoad: true,
            listeners: {
                load: function(store)
                {
                    store.insert(0, {Name: null});
                }
            }
        });
    },

    getLabelFromStore : function(queryName, value)
    {
        var store = LABKEY.VaccineDesign.Utils.getStudyDesignStore(queryName);

        var record = store.findRecord('Name', value, 0, false, true, true);
        if (record)
            return record.get("Label");

        return value;
    }
});