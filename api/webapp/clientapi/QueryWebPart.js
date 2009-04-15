/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 9.2
 * @license Copyright (c) 2008-2009 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

/**
 * @description Constructs a LABKEY.QueryWebPart class instance
 * @class The LABKEY.QueryWebPart simplifies the task of dynamically adding a query web part to your page.
 * @constructor
 * @param {Object} config A configuration object with the following possible properties:
 * @param {String} config.schemaName The name of the schema the web part will query
 * @param {String} config.queryName The name of the query within the schema the web part will select and display
 * @param {String} [config.renderTo] The id of the element inside of which the part should be rendered. This is typically a &lt;div&gt;.
 * If not supplied in the configuration, you must call the render() method to render the part into the page.
 * @param {String} [config.title] An optional title for the web part. If not supplied, the query name will be used as the title.
 * @param {String} [config.titleHref] If supplied, the title will be rendered as a hyperlink with this value as the href attribute.
 * @param {String} [config.buttonBarPosition] Specifies the button bar position for the web part.
 * This may be one of the following: 'none', 'top', 'bottom', 'both'.
 * @param {String} [config.sort] A base sort order to use. This may be a comma-separated list of column names, each of
 * which may have a - prefix to indicate a descending sort.
 * @param {Array} [config.filters] A base set of filters to apply. This should be an array of LABKEY.Filter objects
 * each of which is created using the LABKEY.Filter.create() method.
 * For compatibility with the LABKEY.Query object, you may also specify base filters using config.filterArray.
 * @param {Array} [config.aggregates] A array of aggregate definitions. The objects in this array should have two
 * properties: 'column' and 'type'. The column property is the column name, and the type property may be one of the
 * the {@link LABKEY.AggregateTypes} values.
 * @param {String} [config.dataRegionName] The name to be used for the data region. This should be unique within
 * the set of query views on the page. If not supplied, a unique name is generated for you.
 * @param {String} [config.frame] The frame style to use for the web part. This may be one of the following:
 * 'div', 'portal', 'none', 'dialog', 'title', left-nav'.
 * @param {String} [config.bodyClass] A CSS style class that will be added to the enclosing element for the web part.
 * @param {Function} [config.successCallback] A function to call after the part has been rendered.
 * @param {Object} [config.scope] An object to use as the callback function's scope. Defaults to this.
 * @example
 * &lt;div id='queryTestDiv1'/&gt;
 * &lt;script type="text/javascript"&gt;
var qwp1 = new LABKEY.QueryWebPart({
	renderTo: 'queryTestDiv1',
	title: 'My Query Web Part',
	schemaName: 'lists',
	queryName: 'People',
	buttonBarPosition: 'none',
	aggregates: [
		{column: 'First', type: LABKEY.AggregateTypes.COUNT},
		{column: 'Age', type: LABKEY.AggregateTypes.AVG}
	],
	filters: [
		LABKEY.Filter.create('Last', 'Flintstone')
	],
    sort: '-Last'
});

 //note that you may also register for the 'render' event
 //instead of using the successCallback config property.
 //registering for events is done using Ext event registration.
 //Example:
 qwp1.on("render", onRender);
 function onRender()
 {
    //...do something after the part has rendered...
 }
 
*/
LABKEY.QueryWebPart = Ext.extend(Ext.util.Observable, {
    _paramTranslationMap : {
        frame: 'webpart.frame',
        bodyClass: 'webpart.bodyClass',
        title: 'webpart.title',
        titleHref: 'webpart.titleHref',
        aggregates: false,
        renderTo: false,
        sort: false,
        filterArray: false,
        filters: false
    },

    constructor : function(config)
    {
        LABKEY.QueryWebPart.superclass.constructor.apply(this, arguments);

        //need to require DataRegion.js now--the returned web part HTML
        //will require it then, and will attempt to use it almost immediately
        LABKEY.requiresScript('DataRegion.js', true);
        
        Ext.apply(this, config, {
            dataRegionName: Ext.id(undefined, "aqwp")
        });

        this.filters = this.filters || this.filterArray;

        this.addEvents({
            "render": true
        });

        if(this.renderTo)
            this.render();
    },

    /**
     * Requests the query web part content and renders it within the element identified by the renderTo parameter.
     * Note that you do not need to call this method explicitly if you specify a renderTo property on the config object
     * handed to the class constructor. If you do not specify renderTo in the config, then you must call this method
     * passing the id of the element in which you want the part rendered
     * @name render
     * @function
     * @memberOf LABKEY.QueryWebPart
     * @param renderTo The id of the element in which you want the part rendered.
     */
    render : function(renderTo) {

        var idx = 0; //array index counter

        //allow renderTo param to override config property
        if(renderTo)
            this.renderTo = renderTo;

        if(!this.renderTo)
            Ext.Msg.alert("Configuration Error", "You must supply a renderTo property either in the configuration object, or as a parameter to the render() method!");

        //setup the params
        var params = {};
        params["webpart.name"] = "Query";
        LABKEY.Utils.applyTranslated(params, this, this._paramTranslationMap, true);

        //handle base-filters and sorts (need data region prefix)
        if(this.sort)
            params[this.dataRegionName + ".sort"] = this.sort;

        if(this.filters)
        {
            for(idx = 0; idx < this.filters.length; ++idx)
                params[this.filters[idx].getURLParameterName(this.dataRegionName)] = this.filters[idx].getURLParameterValue();
        }
        
        //handle aggregates separately
        if(this.aggregates)
        {
            for(idx = 0; idx < this.aggregates.length; ++idx)
            {
                if(this.aggregates[idx].type &&  this.aggregates[idx].column)
                    params[this.dataRegionName + '.agg.' + this.aggregates[idx].column] = this.aggregates[idx].type;
            }
        }

        //forward query string parameters for this data region
        var qsParams = LABKEY.ActionURL.getParameters();
        for(var qsParam in qsParams)
        {
            if(-1 != qsParam.indexOf(this.dataRegionName + "."))
                params[qsParam] = qsParams[qsParam];
        }

        //Ext uses a param called _dc to defeat caching, and it may be
        //on the URL if the Query web part has done a sort or filter
        //strip it if it's there so it's not included twice (Ext always appends one)
        delete params["_dc"];

        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("project", "getWebPart", this.containerPath),
            success: function(response) {
                var targetElem = Ext.get(this.renderTo);
                if(targetElem)
                {
                    targetElem.update(response.responseText, true); //execute scripts
                    if(this.successCallback)
                        this.successCallback.call(this.scope || this);
                    this.fireEvent("render");
                }
                else
                    Ext.Msg.alert("Rendering Error", "The element '" + this.renderTo + "' does not exist in the document!");
            },
            failure: LABKEY.Utils.getCallbackWrapper(this.errorCallback, this.scope, true),
            method: 'GET',
            params: params,
            scope: this
        });
    }
});

/**
 * @namespace A predefined set of aggregate types, for use in the config.aggregates array in the
 * {@link LABKEY.QueryWebPart} constructor.
 */
LABKEY.AggregateTypes = {
    /**
     * Displays the sum of the values in the specified column
     */
        SUM: 'sum',
    /**
     * Displays the average of the values in the specified column
     */
        AVG: 'avg',
    /**
     * Displays the count of the values in the specified column
     */
        COUNT: 'count',
    /**
     * Displays the maximum value from the specified column
     */
        MIN: 'min',
    /**
     * Displays the minimum values from the specified column
     */
        MAX: 'max'
};

