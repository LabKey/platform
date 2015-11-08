/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2009-2015 LabKey Corporation
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
 * @class The LABKEY.QueryWebPart simplifies the task of dynamically adding a query web part to your page.  Please use
 * this class for adding query web parts to a page instead of {@link LABKEY.WebPart},
 * which can be used for other types of web parts.
 *              <p>Additional Documentation:
 *              <ul>
 *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=webPartConfig">
 *  				        Web Part Configuration Properties</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
 *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql">
 *                      LabKey SQL Reference</a></li>
 *              </ul>
 *           </p>
 * @constructor
 * @param {Object} config A configuration object with the following possible properties:
 * @param {String} config.schemaName The name of the schema the web part will query.
 * @param {String} config.queryName The name of the query within the schema the web part will select and display.
 * @param {String} [config.viewName] the name of a saved view you wish to display for the given schema and query name.
 * @param {String} [config.reportId] the report id of a saved report you wish to display for the given schema and query name.
 * @param {Mixed} [config.renderTo] The element id, DOM element, or Ext element inside of which the part should be rendered. This is typically a &lt;div&gt;.
 * If not supplied in the configuration, you must call the render() method to render the part into the page.
 * @param {Mixed} [config.maskEl] A element id, DOM element, or Ext element that should be masked while the part is rendered. (default renderTo).
 * @param {String} [config.errorType] A parameter to specify how query parse errors are returned. (default 'html'). Valid
 * values are either 'html' or 'json'. If 'html' is specified the error will be rendered to an HTML view, if 'json' is specified
 * the errors will be returned to the callback handlers as an array of objects named 'parseErrors' with the following properties:
 * <ul>
 *  <li><b>msg</b>: The error message.</li>
 *  <li><b>line</b>: The line number the error occurred at (optional).</li>
 *  <li><b>col</b>: The column number the error occurred at (optional).</li>
 *  <li><b>errorStr</b>: The line from the source query that caused the error (optional).</li>
 * </ul>
 * @param {String} [config.sql] A SQL query that can be used instead of an existing schema name/query name combination.
 * @param {Object} [config.metadata] Metadata that can be applied to the properties of the table fields. Currently, this option is only
 * available if the query has been specified through the config.sql option. For full documentation on
 * available properties, see <a href="https://www.labkey.org/download/schema-docs/xml-schemas/schemas/tableInfo_xsd/schema-summary.html">LabKey XML Schema Reference</a>.
 * This object may contain the following properties:
 * <ul>
 *  <li><b>type</b>: The type of metadata being specified. Currently, only 'xml' is supported.</li>
 *  <li><b>value</b>: The metadata XML value as a string. For example: <code>'&lt;tables xmlns=&quot;http://labkey.org/data/xml&quot;&gt;&lt;table tableName=&quot;Announcement&quot; tableDbType=&quot;NOT_IN_DB&quot;&gt;&lt;columns&gt;&lt;column columnName=&quot;Title&quot;&gt;&lt;columnTitle&gt;Custom Title&lt;/columnTitle&gt;&lt;/column&gt;&lt;/columns&gt;&lt;/table&gt;&lt;/tables&gt;'</code></li>
 * </ul>
 * @param {String} [config.title] A title for the web part. If not supplied, the query name will be used as the title.
 * @param {String} [config.titleHref] If supplied, the title will be rendered as a hyperlink with this value as the href attribute.
 * @param {String} [config.buttonBarPosition] DEPRECATED--see config.buttonBar.position
 * @param {boolean} [config.allowChooseQuery] If the button bar is showing, whether or not it should be include a button
 * to let the user choose a different query.
 * @param {boolean} [config.allowChooseView] If the button bar is showing, whether or not it should be include a button
 * to let the user choose a different view.
 * @param {String} [config.detailsURL] Specify or override the default details URL for the table with one of the form
 * "/controller/action.view?id=${RowId}" or "org.labkey.package.MyController$ActionAction.class?id=${RowId}"
 * @param {boolean} [config.showDetailsColumn] If the underlying table has a details URL, show a column that renders a [details] link (default true).  If true, the record selectors will be included regardless of the 'showRecordSelectors' config option.
 * @param {String} [config.updateURL] Specify or override the default updateURL for the table with one of the form
 * "/controller/action.view?id=${RowId}" or "org.labkey.package.MyController$ActionAction.class?id=${RowId}"
 * @param {boolean} [config.showUpdateColumn] If the underlying table has an update URL, show a column that renders an [edit] link (default true).
 * @param {String} [config.insertURL] Specify or override the default insert URL for the table with one of the form
 * "/controller/insertAction.view" or "org.labkey.package.MyController$InsertActionAction.class"
 * @param {String} [config.importURL] Specify or override the default bulk import URL for the table with one of the form
 * "/controller/importAction.view" or "org.labkey.package.MyController$ImportActionAction.class"
 * @param {String} [config.deleteURL] Specify or override the default delete URL for the table with one of the form
 * "/controller/action.view" or "org.labkey.package.MyController$ActionAction.class". The keys for the selected rows
 * will be included in the POST.
 * @param {boolean} [config.showInsertNewButton] If the underlying table has an insert URL, show an "Insert New" button in the button bar (default true).
 * @param {boolean} [config.showDeleteButton] Show a "Delete" button in the button bar (default true).
 * @param {boolean} [config.showReports] If true, show reports on the Views menu (default true).
 * @param {boolean} [config.showExportButtons] Show the export button menu in the button bar (default true).
 * @param {boolean} [config.showBorders] Render the table with borders (default true).
 * @param {boolean} [config.showSurroundingBorder] Render the table with a surrounding border (default true).
 * @param {boolean} [config.showRecordSelectors] Render the select checkbox column (default undefined, meaning they will be shown if the query is updatable by the current user).
 *  If 'showDeleteButton' is true, the checkboxes will be  included regardless of the 'showRecordSelectors' config option.
 * @param {boolean} [config.showPagination] Show the pagination links and count (default true).
 * @param {boolean} [config.shadeAlternatingRows] Shade every other row with a light gray background color (default true).
 * @param {boolean} [config.suppressRenderErrors] If true, no alert will appear if there is a problem rendering the QueryWebpart. This is most often encountered if page configuration changes between the time when a request was made and the content loads. Defaults to false.
 * @param {Object} [config.buttonBar] Button bar configuration. This object may contain any of the following properties:
 * <ul>
 *  <li><b>position</b>: Configures where the button bar will appear with respect to the data grid: legal values are 'top', 'bottom', 'none', or 'both'. Default is 'both'.</li>
 *  <li><b>includeStandardButtons</b>: If true, all standard buttons not specifically mentioned in the items array will be included at the end of the button bar. Default is false.</li>
 *  <li><b>items</b>: An array of button bar items. Each item may be either a reference to a standard button, or a new button configuration.
 *                  to reference standard buttons, use one of the properties on {@link #standardButtons}, or simply include a string
 *                  that matches the button's caption. To include a new button configuration, create an object with the following properties:
 *      <ul>
 *          <li><b>text</b>: The text you want displayed on the button (aka the caption).</li>
 *          <li><b>url</b>: The URL to navigate to when the button is clicked. You may use LABKEY.ActionURL to build URLs to controller actions.
 *                          Specify this or a handler function, but not both.</li>
 *          <li><b>handler</b>: A reference to the JavaScript function you want called when the button is clicked.</li>
 *          <li><b>permission</b>: Optional. Permission that the current user must possess to see the button. 
 *                          Valid options are 'READ', 'INSERT', 'UPDATE', 'DELETE', and 'ADMIN'.
 *                          Default is 'READ' if permissionClass is not specified.</li>
 *          <li><b>permissionClass</b>: Optional. If permission (see above) is not specified, the fully qualified Java class
 *                           name of the permission that the user must possess to view the button.</li>
 *          <li><b>requiresSelection</b>: A boolean value (true/false) indicating whether the button should only be enabled when
 *                          data rows are checked/selected.</li>
 *          <li><b>items</b>: To create a drop-down menu button, set this to an array of menu item configurations.
 *                          Each menu item configuration can specify any of the following properties:
 *              <ul>
 *                  <li><b>text</b>: The text of the menu item.</li>
 *                  <li><b>handler</b>: A reference to the JavaScript function you want called when the menu item is clicked.</li>
 *                  <li><b>icon</b>: A url to an image to use as the menu item's icon.</li>
 *                  <li><b>items</b>: An array of sub-menu item configurations. Used for fly-out menus.</li>
 *              </ul>
 *          </li>
 *      </ul>
 *  </li>
 * </ul>
 * @param {String} [config.sort] A base sort order to use. This is a comma-separated list of column names, each of
 * which may have a - prefix to indicate a descending sort. It will be treated as the final sort, after any that the user
 * has defined in a custom view or through interacting with the grid column headers.
 * @param {String} [config.removeableSort] An additional sort order to use. This is a comma-separated list of column names, each of
 * which may have a - prefix to indicate a descending sort. It will be treated as the first sort, before any that the user
 * has defined in a custom view or through interacting with the grid column headers.
 * @param {Array} [config.filters] A base set of filters to apply. This should be an array of {@link LABKEY.Filter} objects
 * each of which is created using the {@link LABKEY.Filter.create} method. These filters cannot be removed by the user
 * interacting with the UI.
 * For compatibility with the {@link LABKEY.Query} object, you may also specify base filters using config.filterArray.
 * @param {Array} [config.removeableFilters] A set of filters to apply. This should be an array of {@link LABKEY.Filter} objects
 * each of which is created using the {@link LABKEY.Filter.create} method. These filters can be modified or removed by the user
 * interacting with the UI.
 * @param {Object} [config.parameters] Map of name (string)/value pairs for the values of parameters if the SQL
 * references underlying queries that are parameterized. For example, the following passes two parameters to the query: {'Gender': 'M', 'CD4': '400'}.
 * The parameters are written to the request URL as follows: query.param.Gender=M&query.param.CD4=400.  For details on parameterized SQL queries, see
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=paramsql">Parameterized SQL Queries</a>.
 * @param {Array} [config.aggregates] An array of aggregate definitions. The objects in this array should have the properties:
 * <ul>
 *     <li><b>column:</b> The name of the column to be aggregated.</li>
 *     <li><b>type:</b> The aggregate type (see {@link LABKEY.AggregateTypes})</li>
 *     <li><b>label:</b> Optional label used when rendering the aggregate row.
 * </ul>
 * @param {String} [config.showRows] Either 'paginated' (the default) 'selected', 'unselected', 'all', or 'none'.
 *        When 'paginated', the maxRows and offset parameters can be used to page through the query's result set rows.
 *        When 'selected' or 'unselected' the set of rows selected or unselected by the user in the grid view will be returned.
 *        You can programatically get and set the selection using the {@link LABKEY.DataRegion.setSelected} APIs.
 *        Setting <code>config.maxRows</code> to -1 is the same as 'all'
 *        and setting <code>config.maxRows</code> to 0 is the same as 'none'.
 * @param {Integer} [config.maxRows] The maximum number of rows to return from the server (defaults to 100).
 *        If you want to return all possible rows, set this config property to -1.
 * @param {Integer} [config.offset] The index of the first row to return from the server (defaults to 0).
 *        Use this along with the maxRows config property to request pages of data.
 * @param {String} [config.dataRegionName] The name to be used for the data region. This should be unique within
 * the set of query views on the page. If not supplied, a unique name is generated for you.
 * @param {String} [config.linkTarget] The name of a browser window/tab in which to open URLs rendered in the
 * QueryWebPart. If not supplied, links will generally be opened in the same browser window/tab where the QueryWebPart. 
 * @param {String} [config.frame] The frame style to use for the web part. This may be one of the following:
 * 'div', 'portal', 'none', 'dialog', 'title', 'left-nav'.
 * @param {String} [config.showViewPanel] Open the customize view panel after rendering.  The value of this option can be "true" or one of "ColumnsTab", "FilterTab", or "SortTab".
 * @param {String} [config.bodyClass] A CSS style class that will be added to the enclosing element for the web part.
 * @param {Function} [config.success] A function to call after the part has been rendered. It will be passed two arguments:
 * <ul>
 * <li><b>dataRegion:</b> the LABKEY.DataRegion object representing the rendered QueryWebPart</li>
 * <li><b>request:</b> the XMLHTTPRequest that was issued to the server</li>
 * </ul>
 * @param {Function} [config.failure] A function to call if the request to retrieve the content fails. It will be passed three arguments:
 * <ul>
 * <li><b>json:</b> JSON object containing the exception.</li>
 * <li><b>response:</b> The XMLHttpRequest object containing the response data.</li>
 * <li><b>options:</b> The parameter to the request call.</li>
 * </ul>
 * @param {Object} [config.scope] An object to use as the callback function's scope. Defaults to this.
 * @param {int} [config.timeout] A timeout for the AJAX call, in milliseconds. Default is 30000 (30 seconds).
 * @param {String} [config.containerPath] The container path in which the schema and query name are defined. If not supplied, the current container path will be used.
 * @param {String} [config.containerFilter] One of the values of {@link LABKEY.Query.containerFilter} that sets the scope of this query. If not supplied, the current folder will be used. 
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
		{column: 'First', type: LABKEY.AggregateTypes.COUNT, label: 'Total People'},
		{column: 'Age', type: LABKEY.AggregateTypes.AVG}
	],
	filters: [
		LABKEY.Filter.create('Last', 'Flintstone')
	],
    sort: '-Last'
});

 //note that you may also register for the 'render' event
 //instead of using the success config property.
 //registering for events is done using Ext event registration.
 //Example:
 qwp1.on("render", onRender);
 function onRender()
 {
    //...do something after the part has rendered...
 }

 ///////////////////////////////////////
 // Custom Button Bar Example

var qwp1 = new LABKEY.QueryWebPart({
    renderTo: 'queryTestDiv1',
    title: 'My Query Web Part',
    schemaName: 'lists',
    queryName: 'People',
    buttonBar: {
        includeStandardButtons: true,
        items:[
          LABKEY.QueryWebPart.standardButtons.views,
          {text: 'Test', url: LABKEY.ActionURL.buildURL('project', 'begin')},
          {text: 'Test Script', onClick: "alert('Hello World!'); return false;"},
          {text: 'Test Handler', handler: onTestHandler},
          {text: 'Test Menu', items: [
            {text: 'Item 1', handler: onItem1Handler},
		    {text: 'Fly Out', items: [
              {text: 'Sub Item 1', handler: onItem1Handler}
            ]},
            '-', //separator
            {text: 'Item 2', handler: onItem2Handler}
          ]},
          LABKEY.QueryWebPart.standardButtons.exportRows
        ]
    }
});

function onTestHandler(dataRegion)
{
    alert("onTestHandler called!");
    return false;
}

function onItem1Handler(dataRegion)
{
    alert("onItem1Handler called!");
}

function onItem2Handler(dataRegion)
{
    alert("onItem2Handler called!");
}

&lt;/script&gt;
*/
LABKEY.QueryWebPart = Ext.extend(Ext.util.Observable,
/** @lends LABKEY.QueryWebPart.prototype */
{
    _paramTranslationMap : {
        frame: 'webpart.frame',
        bodyClass: 'webpart.bodyClass',
        title: 'webpart.title',
        titleHref: 'webpart.titleHref',
        aggregates: false,
        renderTo: false,
        sort: false,
        filterArray: false,
        filters: false,
        _paramTranslationMap: false,
        events: false,
        filterOptRe: false,
        userFilters: false,
        userSort: false,
        qsParamsToIgnore: false,
        buttonBar: false,
        showRows: false,
        maxRows: false,
        offset: false,
        scope: false,
        metadata: false,
        parameters: false,
        showViewPanel: false,
        _success: false,
        _failure: false
    },

    constructor : function(config)
    {
        this.addEvents(
                /**
                 * @memberOf LABKEY.QueryWebPart#
                 * @name render
                 * @event
                 * @description Fired after the web part html is rendered into the page.
                 */
                "render"
        );

        if (config.dataRegion) {
            this.constructFromDataRegion(config);
            return;
        }

        this.errorType = 'html';

        Ext.apply(this, config, {
            dataRegionName: Ext.id(undefined, "aqwp"),
            returnURL: window.location.href,
            _success : LABKEY.Utils.getOnSuccess(config),
            _failure : LABKEY.Utils.getOnFailure(config)
        });

        LABKEY.QueryWebPart.superclass.constructor.apply(this, arguments);

        this.filters = this.filters || this.filterArray;

        this.initializeParameters();
        
        if (config.removeableFilters)
        {
            // Translate the set of filters that are removeable to be included as the initial set of URL parameters
            LABKEY.Filter.appendFilterParams(this.userFilters, config.removeableFilters, this.dataRegionName);
        }

        if (config.removeableSort)
        {
            this.userSort = this.userSort + config.removeableSort;
        }

        if (config.removeableContainerFilter)
        {
            this.userContainerFilter = this.removeableContainerFilter;
        }

        // XXX: Uncomment when UI supports adding/removing aggregates as URL parameters just like filters/sorts
        //if (config.removeableAggregates)
        //{
        //    this.userAggregates = LABKEY.Filter.appendAggregateParams({}, config.removeableAggregates, this.dataRegionName);
        //}

        if (this.renderTo)
            this.render();
    },

    /**
     * Acts as another constructor -- requires a LABKEY.DataRegion be provided at instantiation
     * NOTE: This will ignore most parameters provided and source solely from the DataRegion.
     * Still respects the following configuration options:
     * success, failure, listeners, scope, and parameters
     */
    constructFromDataRegion : function(config) {

        if (!config.dataRegion) {
            Ext.Msg.alert('Construction Error', 'A dataRegion configuration must be provided.');
        }

        var dr = config.dataRegion;

        Ext.apply(this, {
            schemaName : dr.schemaName,
            queryName  : dr.queryName,
            dataRegionName : dr.name,
            frame      : false, // never render a frame,
            parameters : config.parameters,
            listeners  : config.listeners,
            _success   : LABKEY.Utils.getOnSuccess(config),
            _failure   : LABKEY.Utils.getOnFailure(config),
            scope      : config.scope
        });

        LABKEY.QueryWebPart.superclass.constructor.apply(this, arguments);

        this._attachListeners(dr);
        this.updateRenderElement(dr);

        this.initializeParameters();
    },

    updateRenderElement : function(dr) {
        var renderEl = Ext.get(dr.name).parent('div'); // Ext will assign an ID if one is not found
        this.renderTo = renderEl.id;
    },

    initializeParameters : function() {
        // 12187: Copy user's current URL filters/sort and add them to qsParamsToIgnore so they aren't added when building the DataRegion's URL.
        var params = LABKEY.ActionURL.getParameters();
        this.qsParamsToIgnore = {};
        this.userFilters = {};
        this.userSort = "";
        this.userContainerFilter = "";
        for (var key in params)
        {
            if (!params.hasOwnProperty(key))
                continue;
            if (key.indexOf(this.dataRegionName + ".") == 0 && key.indexOf("~") > -1)
            {
                this.userFilters[key] = params[key];
                this.qsParamsToIgnore[key] = true;
            }
            else if (key == this.dataRegionName + ".sort")
            {
                this.userSort = params[key];
                this.qsParamsToIgnore[key] = true;
            }
            else if (key == this.dataRegionName + ".containerFilterName")
            {
                this.userContainerFilter = params[key];
                this.qsParamsToIgnore[key] = true;
            }
        }
    },

    createParams : function () {
        //setup the params
        var params = {};
        var name;
        params["webpart.name"] = "Query";
        LABKEY.Utils.applyTranslated(params, this, this._paramTranslationMap, true, false);

        // 10197: Add queryName and viewName parameters both with and without dataRegionName prefix.
        // Unprefixed queryName and viewName parameters are required to bind when 'allowChooseQuery' or 'allowChooseView' are false.
        // Prefixed queryName and viewName parameters are required when generating export urls -- any non-prefixed parameters will be stripped in QueryView.urlFor().
        params[this.dataRegionName + ".queryName"] = this.queryName;
        if (this.viewName)
            params[this.dataRegionName + ".viewName"] = this.viewName;
        if (this.reportId)
            params[this.dataRegionName + ".reportId"] = this.reportId;
        if (this.containerFilter)
            params[this.dataRegionName + ".containerFilterName"] = this.containerFilter;
        if (this.showRows)
            params[this.dataRegionName + ".showRows"] = this.showRows;
        if (this.maxRows >= -1)
            params[this.dataRegionName + ".maxRows"] = this.maxRows;
        if (this.offset)
            params[this.dataRegionName + ".offset"] = this.offset;
        if (this.quickChartDisabled)
            params[this.dataRegionName + ".quickChartDisabled"] = this.quickChartDisabled;

        // Sorts configured by the user when interacting with the grid. We need to pass these as URL parameters.
        if (this.userSort && this.userSort.length > 0)
            params[this.dataRegionName + ".sort"] = this.userSort;

        //add user filters (already in encoded form)
        if (this.userFilters)
        {
            for (name in this.userFilters)
            {
                if (this.userFilters.hasOwnProperty(name))
                    params[name] = this.userFilters[name];
            }
        }

        if (this.userContainerFilter)
        {
            params[this.dataRegionName + ".containerFilterName"] = this.userContainerFilter;
        }

        if (this.parameters)
        {
            for (name in this.parameters)
            {
                if (this.parameters.hasOwnProperty(name))
                {
                    var key = name;
                    if (key.indexOf(this.dataRegionName + ".param.") !== 0)
                        key = this.dataRegionName + ".param." + name;

                    params[key] = this.parameters[name];
                }
            }
        }

        // XXX: Uncomment when UI supports adding/removing aggregates as URL parameters just like filters/sorts
        //if (this.userAggregates)
        //{
        //    for (var name in this.userAggregates)
        //    {
        //        if (this.userAggregates.hasOwnProperty(name))
        //            params[name] = this.userAggregates[name];
        //    }
        //}

        //forward query string parameters for this data region
        //except those we should specifically ignore
        var qsParams = LABKEY.ActionURL.getParameters();

        for (var param in qsParams)
        {
            if (qsParams.hasOwnProperty(param)) {
                if (-1 != param.indexOf(this.dataRegionName + ".") && !this.qsParamsToIgnore[param]) {
                    params[param] = qsParams[param];
                }
            }
        }

        //Ext uses a param called _dc to defeat caching, and it may be
        //on the URL if the Query web part has done a sort or filter
        //strip it if it's there so it's not included twice (Ext always appends one)
        delete params["_dc"];

        return params;
    },

    //
    // Returns a parameters object like DataRegion.getParameters()
    //
    getParameters : function() {
        var params = {}, s;
        for (var p in this.parameters)
        {
            if (this.parameters.hasOwnProperty(p))
            {
                s = p.split('.');
                params[s[s.length-1]] = this.parameters[p];
            }
        }
        return params;
    },

    /**
     * Requests the query web part content and renders it within the element identified by the renderTo parameter.
     * Note that you do not need to call this method explicitly if you specify a renderTo property on the config object
     * handed to the class constructor. If you do not specify renderTo in the config, then you must call this method
     * passing the id of the element in which you want the part rendered
     * @param renderTo The id of the element in which you want the part rendered.
     */
    render : function(renderTo) {

        var dr = LABKEY.DataRegions[this.dataRegionName];

        //allow renderTo param to override config property
        if (renderTo)
            this.renderTo = renderTo;

        if (!this.renderTo)
            Ext.Msg.alert("Configuration Error", "You must supply a renderTo property either in the configuration object, or as a parameter to the render() method!");

        var params = this.createParams(), json = {};

        // ensure SQL is not on the URL -- we allow any property to be pulled through when creating parameters.
        if (params['sql'])
        {
            json.sql = params.sql;
            delete params.sql;
        }

        //add the button bar config if any
        if (this.buttonBar && (this.buttonBar.position || (this.buttonBar.items && this.buttonBar.items.length > 0)))
            json.buttonBar = this.processButtonBar();

        // 10505: add non-removable sorts and filters to json (not url params).  These will be handled in QueryWebPart.java
        json.filters = {};
        if (this.filters)
            LABKEY.Filter.appendFilterParams(json.filters, this.filters, this.dataRegionName);

        // Non-removeable sorts. We need to pass these as JSON, not on the URL.
        if (this.sort)
            json.filters[this.dataRegionName + ".sort"] = this.sort;

        // Non-removable aggregates. We need to pass these as JSON, not on the URL.
        if (this.aggregates)
            LABKEY.Filter.appendAggregateParams(json.filters, this.aggregates, this.dataRegionName);

        if (this.metadata)
            json.metadata = this.metadata;

        // re-open designer after update
        var customizeViewTab = this.showViewPanel;
        if (dr)
        {
            if (dr.customizeView)
            {
                if (dr.customizeView.isVisible())
                {
                    // Currently open designer tab overrides the showViewPanel config option.
                    var tab = dr.customizeView.getActiveDesignerTab();
                    customizeViewTab = tab ? tab.name : true;
                }
                dr.hideCustomizeView(false);
            }
        }

        var timerId = function () {
            timerId = 0;
            this.mask("Loading...");
        }.defer(500, this);

        Ext.Ajax.request({
            timeout: (this.timeout == undefined) ? 30000 : this.timeout,
            url: LABKEY.ActionURL.buildURL("project", "getWebPart", this.containerPath),
            method: 'POST',
            params: params,
            jsonData: json,
            success: function(response, options) {
                if (timerId > 0) {
                    clearTimeout(timerId);
                }

                var target;
                if (LABKEY.Utils.isString(this.renderTo)) {
                    target = this.renderTo;
                }
                else {
                    target = this.renderTo.id;
                }

                var targetElem = jQuery('#' + target); // TODO: Make whole class jQuery dependent
                if (targetElem.length > 0)
                {
                    this.unmask();
                    if (dr)
                        dr.destroy();

                    LABKEY.Utils.loadAjaxContent(response, targetElem, function()
                    {
                        //get the data region and subscribe to events
                        var dr = LABKEY.DataRegions[this.dataRegionName];
                        if (dr)
                        {
                            this._attachListeners(dr);
                            LABKEY.DataRegions[this.dataRegionName].setQWP(this);

                            if (customizeViewTab)
                                dr.showCustomizeView(customizeViewTab, false, false);

                            if (this._success) //11425 : Make callback consistent with documentation
                                Ext.onReady(function(){this._success.call(this.scope || this, dr, response);}, this); //8721: need to use onReady()
                            this.fireEvent('success', dr, response);
                        }
                        else
                        {
                            // We've failed to get the data region (could be bad query params) and have probably displayed
                            // error message.  Should failure be called?  Or should we add a new failure/success callback pair
                            // for the webpart itself (as opposed to the webpart's contents)?

                            this.dataRegion = null;
                        }

                        this.fireEvent("render", this);
                    }, this);
                }
                else
                {
                    if(!this.suppressRenderErrors)
                        Ext.Msg.alert("Rendering Error", "The element '" + this.renderTo + "' does not exist in the document!");
                }
            },
            failure: LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                if (timerId > 0)
                    clearTimeout(timerId);

                var targetElem = Ext.get(this.renderTo);
                if (targetElem)
                {
                    this.unmask();

                    if (this.errorType == 'html' || !this._failure)
                        targetElem.update("<div class='labkey-error'>" + Ext.util.Format.htmlEncode(json.exception) + "</div>");

                    if (this._failure)
                        this._failure.call(this.scope || this, json, response, options);
                }
                else
                {
                    if(!this.suppressRenderErrors)
                        Ext.Msg.alert("Rendering Error", "The element '" + this.renderTo + "' does not exist in the document!");
                }
            }, this, true),
            scope: this
        });
    },

    _attachListeners : function(dr) {
        dr.on("beforeoffsetchange", this.beforeOffsetChange, this);
        dr.on("beforemaxrowschange", this.beforeMaxRowsChange, this);
        dr.on("beforesortchange", this.beforeSortChange, this);
        dr.on("beforeclearsort", this.beforeClearSort, this);
        dr.on("beforefilterchange", this.beforeFilterChange, this);
        dr.on("beforeclearfilter", this.beforeClearFilter, this);
        dr.on("beforeclearallfilters", this.beforeClearAllFilters, this);
        dr.on("beforeclearallparameters", this.beforeClearAllParameters, this);
        dr.on("beforechangeview", this.beforeChangeView, this);
        dr.on("beforeshowrowschange", this.beforeShowRowsChange, this);
        dr.on("beforesetparameters", this.beforeSetParameters, this);
        dr.on("buttonclick", this.onButtonClick, this);
        dr.on("beforerefresh", this.beforeRefresh, this);
    },

    mask : function(message) {
        if (this.masking === false) return;            
        var el = Ext.get(this.maskEl || this.renderTo);
        if (el)
        {
            if (el.getWidth() == 0 || el.getHeight() == 0)
                el.update("<p>&nbsp;</p>");
            el.mask(message);
        }
    },

    unmask : function() {
        if (this.masking === false) return;
        var el = Ext.get(this.maskEl || this.renderTo);
        if (el)
            el.unmask();
    },

    processButtonBar : function() {
        this.processButtonBarItems(this.buttonBar.items);
        return this.buttonBar;
    },

    processButtonBarItems : function(items) {
        if (!items || !items.length || items.length <= 0)
            return;

        var item;
        for (var idx = 0; idx < items.length; ++idx)
        {
            item = items[idx];
            if (item.handler && Ext.isFunction(item.handler))
            {
                item.id = item.id || Ext.id(undefined, "");
                item.onClick = "return LABKEY.DataRegions['" + this.dataRegionName + "'].onButtonClick('"
                        + item.id + "');";
            }
            if (item.items)
                this.processButtonBarItems(item.items);
        }
    },

    onButtonClick : function(buttonId, dataRegion) {
        var item = this.findButtonById(this.buttonBar.items, buttonId);
        if (item && item.handler && Ext.isFunction(item.handler))
        {
            try
            {
                return item.handler.call(item.scope || this, dataRegion);
            }
            catch(ignore) {}
        }
        return false;
    },

    findButtonById : function(items, id) {
        if (!items || !items.length || items.length <= 0)
            return null;

        var ret;
        for (var idx = 0; idx < items.length; ++idx)
        {
            if (items[idx].id == id)
                return items[idx];
            ret = this.findButtonById(items[idx].items, id);
            if (null != ret)
                return ret;
        }
        return null;
    },

    beforeOffsetChange : function(dataRegion, newoffset) {
        this.offset = newoffset;
        this.render();
        return false;
    },

    beforeMaxRowsChange : function(dataRegion, newmax) {
        this.maxRows = newmax;
        this.offset = 0;
        delete this.showRows;
        this.render();
        return false;
    },

    beforeSortChange : function(dataRegion, fieldKey, sortDirection) {
        this.userSort = dataRegion.alterSortString(this.userSort, fieldKey, sortDirection);
        this.render();
        return false;
    },

    beforeClearSort : function(dataRegion, fieldKey) {
        this.userSort = dataRegion.alterSortString(this.userSort, fieldKey, null);
        this.render();
        return false;
    },

    beforeFilterChange : function(dataRegion, filterPairs) {
        this.offset = 0;

        // reset the user filters
        this.userFilters = {};
        for (var i = 0; i < filterPairs.length; i++)
        {
            this.userFilters[filterPairs[i][0]] = filterPairs[i][1];
        }
        this.render();

        // prevent the Data Region from continuing
        return false;
    },

    beforeClearFilter : function(dataRegion, columnName) {
        this.offset = 0;
        var namePrefix = this.dataRegionName + "." + columnName + "~";
        if (this.userFilters)
        {
            for (var name in this.userFilters)
            {
                if (this.userFilters.hasOwnProperty(name) && name.indexOf(namePrefix) >= 0)
                    delete this.userFilters[name];
            }
        }
        this.render();
        return false;
    },

    beforeClearAllFilters : function(dataRegion) {
        this.fireEvent('beforeclearallfilters', dataRegion);
        this.offset = 0;
        this.userFilters = null;
        this.render();
        return false;
    },

    beforeClearAllParameters: function(dataRegion) {
        this.fireEvent('beforeclearallparameters', dataRegion);
        this.offset = 0;
        this.parameters = null;
        this.render();
        return false;
    },

    /**
     * Listener for view change events.
     * @param {Object} view An object which contains the following properties.
     * @param {String} [view.type] the type of view, either a 'view' or a 'report'.
     * @param {String} [view.viewName] If the type is 'view', then the name of the view.
     * @param {String} [view.reportId] If the type is 'report', then the report id.
     * @param {Object} urlParameters <b>NOTE: Experimental parameter; may change without warning.</b> A set of filter and sorts to apply as URL parameters when changing the view.
     */
    beforeChangeView : function(dataRegion, view, urlParameters) {

        delete this.offset;
        delete this.viewName;
        delete this.reportId;

        if (view)
        {
            if (view.type == 'report')
                this.reportId = view.reportId;
            else if (view.viewName)
                this.viewName = view.viewName;
        }
        else
        {
            // delete the viewName so it isn't POSTed as empty string
            delete this.viewName;
        }
        this.qsParamsToIgnore[this.getQualifiedParamName("viewName")] = true;

        if (urlParameters)
        {
            this.userFilters = {};
            if (urlParameters.filter && urlParameters.filter.length > 0)
            {
                for (var i = 0; i < urlParameters.filter.length; i++)
                {
                    var filter = urlParameters.filter[i];
                    this.userFilters[this.dataRegionName + "." + filter.fieldKey + "~" + filter.op] = filter.value;
                }
            }

            var userSort = [];
            if (urlParameters.sort && urlParameters.sort.length > 0)
            {
                for (var i = 0; i < urlParameters.sort.length; i++)
                {
                    var sort = urlParameters.sort[i];
                    userSort.push((sort.dir == "+" ? "" : sort.dir) + sort.fieldKey);
                }
            }
            this.userSort = userSort.join(",");

            if (urlParameters.containerFilter)
                this.userContainerFilter = urlParameters.containerFilter;

            // remove all filter, sort, and container filter parameters
            this.qsParamsToIgnore[this.getQualifiedParamName(".")] = true;
            this.qsParamsToIgnore[this.getQualifiedParamName(".sort")] = true;
            this.qsParamsToIgnore[this.getQualifiedParamName(".containerFilterName")] = true;
        }

        this.render();
        return false;
    },

    beforeRefresh : function(dataRegion) {
        this.fireEvent('beforerefresh', dataRegion);
        this.render();
        return false;
    },

    getQualifiedParamName : function(paramName) {
        return this.dataRegionName + "." + paramName;
    },

    beforeShowRowsChange : function(dataRegion, showRowsSetting) {
        this.showRows = showRowsSetting;
        delete this.offset;
        delete this.maxRows;
        this.render();
        return false;
    },

    beforeSetParameters : function(dataRegion, parameters) {
        this.parameters = parameters;
        delete this.offset;
        this.render();
        return false;
    },

    getDataRegion : function(){
        return this.dataRegionName ? LABKEY.DataRegions[this.dataRegionName] : null;
    }
});

/**
 * A read-only object that exposes properties representing standard buttons shown in LabKey data grids.
 * These are used in conjunction with the buttonBar configuration. The following buttons are currently defined:
 * <ul>
 *  <li>LABKEY.QueryWebPart.standardButtons.query</li>
 *  <li>LABKEY.QueryWebPart.standardButtons.views</li>
 *  <li>LABKEY.QueryWebPart.standardButtons.insertNew</li>
 *  <li>LABKEY.QueryWebPart.standardButtons.deleteRows</li>
 *  <li>LABKEY.QueryWebPart.standardButtons.exportRows</li>
 *  <li>LABKEY.QueryWebPart.standardButtons.print</li>
 *  <li>LABKEY.QueryWebPart.standardButtons.pageSize</li>
 * </ul>
 * @name standardButtons
 * @memberOf LABKEY.QueryWebPart#
 */
LABKEY.QueryWebPart.standardButtons = {
    query: 'query',
    views: 'views',
    insertNew: 'insert new',
    deleteRows: 'delete',
    exportRows: 'export',
    print: 'print',
    pageSize: 'page size'
};


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

