/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2014-2017 LabKey Corporation
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
LABKEY.Query = new function(impl, $) {

    // Insert a hidden <form> into to page, put the JSON into it, and submit it - the server's response
    // will make the browser pop up a dialog
    function submitForm(url, formData) {
        if (!formData['X-LABKEY-CSRF'])
            formData['X-LABKEY-CSRF'] = LABKEY.CSRF;

        var formId = LABKEY.Utils.generateUUID();

        var html = '<form method="POST" id="' + formId + '"action="' + url + '">';
        for (var name in formData)
        {
            if (!formData.hasOwnProperty(name))
                continue;

            var value = formData[name];
            if (value == undefined)
                continue;

            html += '<input type="hidden"' +
                    ' name="' + LABKEY.Utils.encodeHtml(name) + '"' +
                    ' value="' + LABKEY.Utils.encodeHtml(value) + '" />';
        }
        html += "</form>";

        $('body').append(html);
        $('form#' + formId).submit();
    }

    /**
     * Execute arbitrary LabKey SQL and export the results to Excel or TSV. After this method is
     * called, the user will be prompted to accept a file from the server, and most browsers will allow
     * the user to either save it or open it in an appropriate application.
     * For more information, see the
     * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql">
     * LabKey SQL Reference</a>.
     * @param config An object which contains the following configuration properties.
     * @param {String} config.schemaName name of the schema to query.
     * @param {String} config.sql The LabKey SQL to execute.
     * @param {String} [config.format] The desired export format. May be either 'excel' or 'tsv'. Defaults to 'excel'.
     * @param {String} [config.containerPath] The path to the container in which the schema and query are defined,
     *       if different than the current container. If not supplied, the current container's path will be used.
     * @param {String} [config.containerFilter] One of the values of {@link LABKEY.Query.containerFilter} that sets
     *       the scope of this query. Defaults to containerFilter.current, and is interpreted relative to
     *       config.containerPath.
     */
    impl.exportSql = function(config) {

        var url = LABKEY.ActionURL.buildURL("query", "exportSql", config.containerPath);
        var formData = {
            sql: config.sql,
            schemaName: config.schemaName,
            format: config.format,
            containerFilter: config.containerFilter
        };

        submitForm(url, formData);
    };

    /**
     * @private Not yet official API
     * Export a set of tables
     * @param config An object which contains the following:
     * @param {String} config.schemas An object with the following structure:
     * <pre>
     * {
     *    schemas: {
     *
     *      // export the named queries from schema "A" using the default view or the named view
     *      "A": [{
     *          queryName: "a"
     *          filters: [ LABKEY.Filters.create("Name", "bob", LABKEY.Filter.Types.NEQ) ],
     *          sort: "Name"
     *      },{
     *          queryName: "b",
     *          viewName: "b-view"
     *      }]
     *
     *    }
     * }
     * </pre>
     * @param {String} [config.headerType] Column header type
     *
     */
    impl.exportTables = function (config) {

        var formData = {};

        if (config.headerType)
            formData.headerType = config.headerType;

        // Create a copy of the schema config that we can mutate
        var schemas = LABKEY.Utils.merge({}, config.schemas);
        for (var schemaName in schemas)
        {
            if (!schemas.hasOwnProperty(schemaName))
                continue;

            var queryList = schemas[schemaName];
            for (var i = 0; i < queryList.length; i++)
            {
                var querySettings = queryList[i];
                var o = LABKEY.Utils.merge({}, querySettings);

                delete o.filter;
                delete o.filterArray;
                delete o.sort;

                // Turn the filters array into a filters map similar to LABKEY.QueryWebPart
                o.filters = LABKEY.Filter.appendFilterParams(null, querySettings.filters || querySettings.filterArray);

                if (querySettings.sort)
                    o.filters["query.sort"] = querySettings.sort;

                queryList[i] = o;
            }
        }

        formData.schemas = JSON.stringify(schemas);

        var url = LABKEY.ActionURL.buildURL("query", "exportTables.view");
        submitForm(url, formData);
    };

    function loadingSelect(select) {
        select.prop('disabled', true);
        select.empty().append($('<option>', {text: 'Loading...'}));
    }

    function populateSelect(select, options, valueProperty, textProperty, initialValue) {
        select.empty().append($('<option>'));
        $.each(options, function (i, option) {
            var value = valueProperty ? option[valueProperty] : option;
            var text = textProperty ? option[textProperty] : option;
            var selected = initialValue && value === initialValue;
            select.append($('<option>', { value: value,  text: text,  selected: selected}));
        });

        select.prop('disabled', false);
    }

    function sortObjectArrayByTitle(a, b){
        var aTitle = a.title ? a.title : a.caption;
        var bTitle = b.title ? b.title : b.caption;
        return ((aTitle < bTitle) ? -1 : ((aTitle > bTitle) ? 1 : 0));
    }

    var SCHEMA_QUERIES_CACHE = {}; // cache of queries by schema
    function loadQueries(schemaSelect, querySelect, selectedSchema, initialValue) {
        schemaSelect.prop('disabled', true);
        loadingSelect(querySelect);

        if (SCHEMA_QUERIES_CACHE[selectedSchema]) {
            populateSelect(querySelect, SCHEMA_QUERIES_CACHE[selectedSchema], 'name', 'title', initialValue);
            schemaSelect.prop('disabled', false);
        }
        else {
            LABKEY.Query.getQueries({
                schemaName: selectedSchema,
                includeColumns: false,
                success: function(data) {
                    // add the sorted set of queries for this schema to the cache
                    SCHEMA_QUERIES_CACHE[selectedSchema] = data.queries.sort(sortObjectArrayByTitle);

                    populateSelect(querySelect, SCHEMA_QUERIES_CACHE[selectedSchema], 'name', 'title', initialValue);
                    schemaSelect.prop('disabled', false);

                    // if there is a selected query, fire the change event
                    if (querySelect.val()) {
                        querySelect.trigger('change');
                    }
                }
            });
        }
    }

    var QUERY_COLUMNS_CACHE = {}; // cache of columns by schema|query
    function loadColumns(select, schemaName, queryName, filterFn, initValue) {
        loadingSelect(select);

        var queryKey = schemaName + '|' + queryName;
        if (QUERY_COLUMNS_CACHE[queryKey]) {
            populateColumnsWithFilterFn(select, QUERY_COLUMNS_CACHE[queryKey], filterFn, initValue);
        }
        else {
            LABKEY.Query.getQueryDetails({
                schemaName: schemaName,
                queryName: queryName,
                success: function(data) {
                    // find the default view from the views array returned
                    // NOTE: in the future if we allow this to work for view other than the default, this logic will need to change
                    var queryView = null;
                    $.each(data.views, function(i, view) {
                        if (view.default) {
                            queryView = view;
                            return false;
                        }
                    });

                    QUERY_COLUMNS_CACHE[queryKey] = [];
                    if (queryView) {
                        QUERY_COLUMNS_CACHE[queryKey] = queryView.fields.sort(sortObjectArrayByTitle);
                    }

                    populateColumnsWithFilterFn(select, QUERY_COLUMNS_CACHE[queryKey], filterFn, initValue);
                }
            });
        }
    }

    function populateColumnsWithFilterFn(select, origFields, filterFn, initValue) {
        var fields = [];
        $.each(origFields, function(i, field) {
            var includeField = true;

            // allow for a filter function to be called for each field
            if (filterFn && LABKEY.Utils.isFunction(filterFn)) {
                includeField = filterFn.call(this, field);
            }

            if (includeField) {
                fields.push($.extend({}, field));
            }
        });

        if (fields.length > 0) {
            populateSelect(select, fields, 'name', 'caption', initValue);
        }
        else {
            select.empty().append($('<option>', {text: 'No columns available'}));
        }
    }

    /**
     * Load the set of user visible schemas from the given container into a standard <select> input element.
     * @param config An object which contains the following configuration properties.
     * @param {String} config.renderTo the id of the <select> input to load the LabKey queries into.
     * @param {String} config.initValue the initial value to try and set the <select> element value after it loads.
     */
    impl.schemaSelectInput = function(config) {
        var SCHEMA_SELECT;

        if (!config || !config.renderTo) {
            console.error('Invalid config object. Missing renderTo property for the <select> element.');
            return;
        }

        SCHEMA_SELECT = $("select[id='" + config.renderTo + "']");
        if (SCHEMA_SELECT.length !== 1) {
            console.error('Invalid config object. Expect to find exactly one <select> element for the renderTo provided (found: ' + SCHEMA_SELECT.length + ').');
            return;
        }

        loadingSelect(SCHEMA_SELECT);
        LABKEY.Query.getSchemas({
            includeHidden: false,
            success: function(data) {
                populateSelect(SCHEMA_SELECT, data.schemas.sort(), null, null, config.initValue);

                // if there is a selected schema, fire the change event
                if (SCHEMA_SELECT.val()) {
                    SCHEMA_SELECT.trigger('change', [SCHEMA_SELECT.val()]);
                }
            }
        });
    };

    /**
     * Load the set of queries from this container for a given schema into a standard <select> input. The config object
     * must define which <select> input is for the schemas and which <select> input is for the queries. This function
     * also then associates the two <select> inputs so that a selection change in the schema input will update the
     * query input accordingly.
     * @param config An object which contains the following configuration properties.
     * @param {String} config.renderTo the id of the <select> input to load the LabKey queries into.
     * @param {String} config.schemaInputId the id of the <select> input to load the LabKey schemas into.
     * @param {String} config.initValue the initial value to try and set the <select> element value after it loads.
     */
    impl.querySelectInput = function(config) {
        var SCHEMA_SELECT, QUERY_SELECT;

        if (!config || !config.renderTo || !config.schemaInputId) {
            var msg = 'Invalid config object. ';
            if (!config.renderTo) {
                msg += 'Missing renderTo property for the <select> element. ';
            }
            if (!config.schemaInputId) {
                msg += 'Missing schemaInputId property for the parent <select> element. ';
            }
            console.error(msg);
            return;
        }

        QUERY_SELECT = $("select[id='" + config.renderTo + "']");
        if (QUERY_SELECT.length !== 1) {
            console.error('Invalid config object. Expect to find exactly one <select> element with the name provided (found: ' + QUERY_SELECT.length + ').');
            return;
        }

        SCHEMA_SELECT = $("select[id='" + config.schemaInputId + "']");
        if (SCHEMA_SELECT.length !== 1) {
            console.error('Invalid config object. Expect to find exactly one <select> element with the name provided (found: ' + SCHEMA_SELECT.length + ').');
            return;
        }

        SCHEMA_SELECT.on('change', function (event, schemaName) {
            loadQueries(SCHEMA_SELECT, QUERY_SELECT, schemaName || event.target.value, config.initValue);
        });
    };

    /**
     * Load the set of columns for a given schema/query into a standard <select> input. The config object
     * must define the schemaName and queryName to be used to source the column listing.
     * @param config An object which contains the following configuration properties.
     * @param {String} config.renderTo the id of the <select> input to load the LabKey queries into. Required.
     * @param {String} config.schemaName the name of the schema. Required.
     * @param {String} config.queryName the name of the query. Required.
     * @param {String} config.initValue the initial value to try and set the <select> element value after it loads. Optional.
     * @param {String} config.filterFn a function to call to filter the column set (ex. by data type). Optional.
     */
    impl.columnSelectInput = function(config) {
        var COLUMN_SELECT;

        if (!config || !config.renderTo || !config.schemaName || !config.queryName) {
            var msg = 'Invalid config object. ';
            if (!config.renderTo) {
                msg += 'Missing renderTo property for the <select> element. ';
            }
            if (!config.schemaName) {
                msg += 'Missing schemaName property. ';
            }
            if (!config.queryName) {
                msg += 'Missing queryName property. ';
            }
            console.error(msg);
            return;
        }

        COLUMN_SELECT = $("select[id='" + config.renderTo + "']");
        if (COLUMN_SELECT.length !== 1) {
            console.error('Invalid config object. Expect to find exactly one <select> element with the name provided (found: ' + COLUMN_SELECT.length + ').');
            return;
        }

        loadColumns(COLUMN_SELECT, config.schemaName, config.queryName, config.filterFn, config.initValue);
    };

    return impl;

}(LABKEY.Query, jQuery);
