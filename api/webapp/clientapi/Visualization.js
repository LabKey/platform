function getSuccessCallbackWrapper(createMeasureFn, fn, scope)
{
    return function(response, options)
    {
        //ensure response is JSON before trying to decode
        var json = null;
        var measures = null;
        if (response && response.getResponseHeader && response.getResponseHeader('Content-Type')
                && response.getResponseHeader('Content-Type').indexOf('application/json') >= 0)
        {
            json = Ext.util.JSON.decode(response.responseText);
            measures = createMeasureFn(json);
        }

        if(fn)
            fn.call(scope || this, measures, response);
    };
}

LABKEY.Visualization = new function() {

    /*-- public methods --*/
    /** @scope LABKEY.Visualization */
    return {

        Types : {
            TABULAR : 'tabular',
            EXCEL : 'excel',
            SCATTER : 'scatter',
            TIMEPLOT : 'timeplot'},

        getTypes : function(config) {

            return [Types.TABULAR, Types.EXCEL, Types.SCATTER, Types.TIMEPLOT];
        },

        getMeasures : function(config) {

            function createMeasures(json)
            {
                var measures = [];
                if (json.measures && json.measures.length)
                {
                    for (var i=0; i < json.measures.length; i++)
                        measures.push(new LABKEY.Visualization.Measure(json.measures[i]));
                }
                return measures;
            }

            Ext.Ajax.request(
            {
                url : LABKEY.ActionURL.buildURL("reports", "getMeasures", null, config),
                method : 'GET',
                success: getSuccessCallbackWrapper(createMeasures, config.successCallback, config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(config.failureCallback, config.scope, true)
            });
        }
    };
};

LABKEY.Visualization.Measure = Ext.extend(Object, {

    constructor : function(config)
    {
        LABKEY.Visualization.Measure.superclass.constructor.call(this, config);
        Ext.apply(this, config);
    },

    getQueryName : function() {
        return this.queryName;
    },

    getSchemaName : function() {
        return this.schemaName;
    },

    getName : function() {
        return this.name;
    },

    getLabel : function() {
        return this.label;
    },

    getType : function() {
        return this.type;
    },

    getDescription : function() {
        return this.description;
    },

    getDimensions : function(config) {

        Ext.applyIf(config, {query: this.queryName, schema: this.schemaName});
        function createDimensions(json)
        {
            var dimensions = [];
            if (json.dimensions && json.dimensions.length)
            {
                for (var i=0; i < json.dimensions.length; i++)
                    dimensions.push(new LABKEY.Visualization.Dimension(json.dimensions[i]));
            }
            return dimensions;
        }

        Ext.Ajax.request(
        {
            url : LABKEY.ActionURL.buildURL("reports", "getDimensions", null, config),
            method : 'GET',
            success: getSuccessCallbackWrapper(createDimensions, config.successCallback, config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(config.failureCallback, config.scope, true)
        });
    }
});

LABKEY.Visualization.Dimension = Ext.extend(Object, {

    constructor : function(config)
    {
        LABKEY.Visualization.Dimension.superclass.constructor.call(this, config);
        Ext.apply(this, config);
    },

    getQueryName : function() {
        return this.query;
    },

    getSchemaName : function() {
        return this.schema;
    },

    getName : function() {
        return this.name;
    },

    getLabel : function() {
        return this.label;
    },

    getType : function() {
        return this.type;
    },

    getDescription : function() {
        return this.description;
    },

    getValues : function(config) {

        Ext.applyIf(config, {query: this.queryName, schema: this.schemaName, name: this.name});
        function createValues(json)
        {
            if (json.success && json.values)
                return json.values;
            return [];
        }

        Ext.Ajax.request(
        {
            url : LABKEY.ActionURL.buildURL("reports", "getDimensionValues", null, config),
            method : 'GET',
            success: getSuccessCallbackWrapper(createValues, config.successCallback, config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(config.failureCallback, config.scope, true)
        });
    }
});