
Ext4.define('LABKEY.VaccineDesign.Utils', {

    singleton: true,

    /**
     * Helper function to get field editor config object for a study design lookup combo.
     * @param name Field name
     * @param width Field width
     * @param queryName If combo, the queryName for the store
     * @param filter LABKEY.Filter.create() object
     * @param displayField The field name of the combo store displayField
     * @param valueField The field name of the combo store valueField
     * @returns {Object} Field config
     */
    getStudyDesignComboConfig : function(name, width, queryName, filter, displayField, valueField)
    {
        return {
            hideFieldLabel: true,
            name: name,
            width: width || 150,
            forceSelection : false, // allow usage of inactive types
            editable : false,
            queryMode : 'local',
            // TODO: this does not htmlEncode the display value in expanded options list
            displayField : displayField || 'Label',
            valueField : valueField || 'Name',
            store : LABKEY.VaccineDesign.Utils.getStudyDesignStore(queryName, filter)
        };
    },

    /**
     * Helper function to get field editor config object for a study design text or text area field.
     * @param name Field name
     * @param width Field width
     * @param height Field height
     * @returns {Object} Field config
     */
    getStudyDesignTextConfig : function(name, width, height)
    {
        return {
            hideFieldLabel: true,
            name: name,
            width: width,
            height: height
        }
    },

    /**
     * Helper function to get field editor config object for a study design number field.
     * @param name Field name
     * @param width Field width
     * @returns {Object} Field config
     */
    getStudyDesignNumberConfig : function(name, width)
    {
        return {
            hideFieldLabel: true,
            name: name,
            width: width,
            minValue: 0,
            allowDecimal: false
        }
    },

    /**
     * Create a new LABKEY.ext4.Store for the given queryName from the study schema.
     * @param queryName
     * @param filter LABKEY.Filter.create() object
     * @returns {LABKEY.ext4.Store}
     */
    getStudyDesignStore : function(queryName, filter)
    {
        var key = Ext4.isDefined(filter) ? queryName +  '|' + filter.getColumnName() + '|' + filter.getValue() : queryName,
            store = Ext4.getStore(key),
            hasStudyDesignPrefix = queryName.indexOf('StudyDesign') == 0;

        if (Ext4.isDefined(store))
            return store;

        return Ext4.create('LABKEY.ext4.Store', {
            storeId: key,
            schemaName: 'study',
            queryName: queryName,
            columns: 'RowId,Name,Label,DisplayOrder',
            filterArray: Ext4.isDefined(filter) ? [filter] : (hasStudyDesignPrefix ? [LABKEY.Filter.create('Inactive', false)] : []),
            containerFilter: LABKEY.container.type == 'project' || Ext4.isDefined(filter) || !hasStudyDesignPrefix ? 'Current' : 'CurrentPlusProject',
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

    /**
     * Lookup a label for a given value using a store generated from the queryName.
     * @param queryName
     * @param value
     * @returns {String}
     */
    getLabelFromStore : function(queryName, value)
    {
        var store = LABKEY.VaccineDesign.Utils.getStudyDesignStore(queryName),
            storeCols = Ext4.Array.pluck(store.getColumns(), 'dataIndex'),
            record = null;

        if (storeCols.indexOf('RowId') > -1)
            record = store.findRecord('RowId', value, 0, false, true, true);

        if (record == null && storeCols.indexOf('Name') > -1)
            record = store.findRecord('Name', value, 0, false, true, true);

        return record != null ? record.get("Label") : value;
    },

    /**
     * Check if the given object has an properties which have data (i.e. non null, not an empty string, or is an array)
     * @param obj The object to test
     * @returns {boolean}
     */
    objectHasData : function(obj)
    {
        var hasNonNull = false;

        if (Ext4.isObject(obj))
        {
            Ext4.Object.each(obj, function (key, value)
            {
                if ((Ext4.isArray(value) && value.length > 0) || (value != null && value != ''))
                {
                    hasNonNull = true;
                    return false; // break
                }
            });
        }

        return hasNonNull;
    },

    /**
     * Get the matching row index from an array based on a given row's object property name and value.
     * @param arr The array to traverse
     * @param filterPropName The name of the row's object property to compare
     * @param filterPropValue The value of the row's object property that indicates a match
     * @returns {Object}
     */
    getMatchingRowIndexFromArray : function(arr, filterPropName, filterPropValue)
    {
        if (Ext4.isString(filterPropName) && Ext4.isDefined(filterPropValue) && Ext4.isArray(arr))
        {
            for (var i = 0; i < arr.length; i++)
            {
                if (arr[i].hasOwnProperty(filterPropName) && arr[i][filterPropName] == filterPropValue)
                    return i;
            }
        }

        return -1;
    }
});