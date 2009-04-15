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
                        this.successCallback.call(this.scope);
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

LABKEY.AggregateTypes = {
        SUM: 'sum',
        AVG: 'avg',
        COUNT: 'count',
        MIN: 'min',
        MAX: 'max'
};

