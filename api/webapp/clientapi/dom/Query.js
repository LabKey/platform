/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2014-2019 LabKey Corporation
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


    /**
     * Documentation specified in core/Query.js -- search for "@name exportSql"
     */
    impl.exportSql = function(config) {

        var url = LABKEY.ActionURL.buildURL("query", "exportSql", config.containerPath);
        var formData = {
            sql: config.sql,
            schemaName: config.schemaName,
            format: config.format,
            containerFilter: config.containerFilter
        };

        LABKEY.Utils.postToAction(url, formData);
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
    impl.exportTables = function(config) {

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
        LABKEY.Utils.postToAction(url, formData);
    };

    function loadingSelect(select) {
        select.prop('disabled', true);
        select.empty().append($('<option>', {text: 'Loading...'}));
    }

    function populateSelect(select, options, valueProperty, textProperty, initialValue, isRequired, includeBlankOption) {
        select.empty();

        // if we have duplicate text options, fall back to displaying the value
        var textOptions = {}, duplicates = {};
        $.each(options, function(i, option) {
            var textValue = option[textProperty];
            if (textOptions[textValue] === undefined)
                textOptions[textValue] = true;
            else {
                option[textProperty] = option[valueProperty];
                duplicates[textValue] = true;
            }
        });

        var validInitialValue = false;
        $.each(options, function (i, option) {
            var value = valueProperty ? option[valueProperty] : option;
            var text = textProperty ? option[textProperty] : option;
            if (duplicates[text] !== undefined)
                text = value;
            var selected = initialValue != undefined && value === initialValue;
            if (selected)
                validInitialValue = true;
            select.append($('<option>', { value: value,  text: text,  selected: selected}));
        });

        if (includeBlankOption !== false) {
            var elem = '<option';
            if (isRequired)
                elem += ' hidden';
            if (!validInitialValue)
                elem += ' selected';
            elem += '></option>';
            select.prepend($(elem));
        }

        select.prop('disabled', false);
        select.on('change', function(){
            if (initialValue !== select.val())
                LABKEY.setDirty(true);
        });
    }

    function sortObjectArrayByTitle(a, b){
        var aTitle = a.title ? a.title : a.caption;
        var bTitle = b.title ? b.title : b.caption;
        return aTitle.localeCompare(bTitle);
    }

    var SCHEMA_QUERIES_CACHE = {}; // cache of queries by schema
    function loadQueries(schemaSelect, querySelect, selectedSchema, initialValue, isRequired, includeBlankOption) {
        schemaSelect.prop('disabled', true);
        loadingSelect(querySelect);

        if (SCHEMA_QUERIES_CACHE[selectedSchema]) {
            populateSelect(querySelect, SCHEMA_QUERIES_CACHE[selectedSchema], 'name', 'title', initialValue, isRequired, includeBlankOption);
            schemaSelect.prop('disabled', false);
        }
        else {
            LABKEY.Query.getQueries({
                schemaName: selectedSchema,
                includeColumns: false,
                success: function(data) {
                    // add the sorted set of queries for this schema to the cache
                    SCHEMA_QUERIES_CACHE[selectedSchema] = data.queries.sort(sortObjectArrayByTitle);

                    populateSelect(querySelect, SCHEMA_QUERIES_CACHE[selectedSchema], 'name', 'title', initialValue, isRequired, includeBlankOption);
                    schemaSelect.prop('disabled', false);

                    // if there is a selected query, fire the change event
                    if (querySelect.val()) {
                        querySelect.trigger('change');
                    }
                }
            });
        }
    }

    var QUERY_COLUMNS_CACHE = {}; // cache of columns by schema|query|view
    function loadQueryColumns(select, schemaName, queryName, viewName, filterFn, initValue, isRequired, includeBlankOption, sortFn) {
        loadingSelect(select);

        if (viewName === undefined || viewName === null)
            viewName = ""; //'default' view has an empty string as its name

        var queryKey = schemaName + '|' + queryName + "|" + viewName;
        if (LABKEY.Utils.isArray(QUERY_COLUMNS_CACHE[queryKey])) {
            populateColumnsWithFilterFn(select, QUERY_COLUMNS_CACHE[queryKey], filterFn, initValue, isRequired, includeBlankOption, sortFn);
            LABKEY.Utils.signalWebDriverTest("queryColumnsLoaded"); // used for test
        }
        else if (QUERY_COLUMNS_CACHE[queryKey] === 'loading') {
            setTimeout(loadQueryColumns, 500, select, schemaName, queryName, viewName, filterFn, initValue, isRequired, includeBlankOption, sortFn);
        }
        else {
            QUERY_COLUMNS_CACHE[queryKey] = 'loading';
            LABKEY.Query.getQueryDetails({
                schemaName: schemaName,
                queryName: queryName,
                viewName: "*",
                success: function(data) {
                    var queryView = null;
                    $.each(data.views, function(i, view) {
                        if (view['name'] === viewName) {
                            queryView = view;
                            return false;
                        }
                    });

                    QUERY_COLUMNS_CACHE[queryKey] = [];
                    if (queryView) {
                        QUERY_COLUMNS_CACHE[queryKey] = queryView.fields.sort(sortObjectArrayByTitle);
                    }

                    populateColumnsWithFilterFn(select, QUERY_COLUMNS_CACHE[queryKey], filterFn, initValue, isRequired, includeBlankOption, sortFn);
                    LABKEY.Utils.signalWebDriverTest("queryColumnsLoaded"); // used for test
                }
            });
        }
    }

    function populateColumnsWithFilterFn(select, origFields, filterFn, initValue, isRequired, includeBlankOption, sortFn) {
        var fields = [];
        $.each(origFields, function(i, field) {
            var includeField = true;

            // allow for a filter function to be called for each field
            if (filterFn && LABKEY.Utils.isFunction(filterFn)) {
                includeField = filterFn.call(this, field);
            }

            // issue 34203: if the field doesn't have a caption, don't include it
            if (field.caption == null || field.caption ==='' || field.caption === '&nbsp;') {
                includeField = false;
            }

            if (includeField) {
                fields.push($.extend({}, field));
            }
        });

        if (fields.length > 0) {
            // allow for a sort function to be called to order fields
            if (sortFn && LABKEY.Utils.isFunction(sortFn)) {
                fields.sort(sortFn);
            }
            populateSelect(select, fields, 'name', 'caption', initValue, isRequired, includeBlankOption);
        }
        else {
            select.empty().append($('<option>', {text: 'No columns available'}));
        }
    }

    var QUERY_VIEWS_CACHE = {}; // cache of columns by schema|query
    function loadQueryViews(schemaSelect, querySelect, queryViewselect, initValue) {
        var schemaName = schemaSelect.val(), queryName = querySelect.val();
        if (!schemaName || !queryName)
            return;

        schemaSelect.prop('disabled', true);
        querySelect.prop('disabled', true);

        loadingSelect(queryViewselect);

        var queryKey = schemaName + '|' + queryName;
        if (LABKEY.Utils.isArray(QUERY_VIEWS_CACHE[queryKey])) {
            populateViews(queryViewselect, QUERY_VIEWS_CACHE[queryKey], initValue);
            schemaSelect.prop('disabled', false);
            querySelect.prop('disabled', false);
        }
        else if (QUERY_VIEWS_CACHE[queryKey] === 'loading') {
            setTimeout(loadQueryViews, 500, schemaSelect, querySelect, queryViewselect, initValue);
        }
        else {
            QUERY_COLUMNS_CACHE[queryKey] = 'loading';
            LABKEY.Query.getQueryViews({
                schemaName: schemaName,
                queryName: queryName,
                success: function(data) {
                    var views = [];
                    $.each(data.views, function(i, view) {
                        if (!view.hidden) {
                            views.push(view)
                        }
                    });

                    QUERY_VIEWS_CACHE[queryKey] = views;

                    schemaSelect.prop('disabled', false);
                    querySelect.prop('disabled', false);
                    populateViews(queryViewselect, QUERY_VIEWS_CACHE[queryKey], initValue);
                }
            })
        }
    }

    function populateViews(select, views, initValue) {
        if (views.length > 0) {
            populateSelect(select, views, 'name', 'label', initValue, true, false);
        }
        else {
            select.empty().append($('<option>', {text: 'No views available'}));
        }
    }

    /**
     * Documentation specified in core/Query.js -- search for "@name schemaSelectInput"
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
                populateSelect(SCHEMA_SELECT, data.schemas.sort(), null, null, config.initValue, config.isRequired, config.includeBlankOption);

                // if there is a selected schema, fire the change event
                if (SCHEMA_SELECT.val()) {
                    SCHEMA_SELECT.trigger('change', [SCHEMA_SELECT.val()]);
                }
            }
        });
    };

    /**
     * Documentation specified in core/Query.js -- search for "@name querySelectInput"
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
            loadQueries(SCHEMA_SELECT, QUERY_SELECT, schemaName || event.target.value, config.initValue, config.isRequired, config.includeBlankOption);
        });
    };

    /**
     * Documentation specified in core/Query.js -- search for "@name columnSelectInput"
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

        loadQueryColumns(COLUMN_SELECT, config.schemaName, config.queryName, config.viewName, config.filterFn, config.initValue, config.isRequired, config.includeBlankOption, config.sortFn);
    };

    impl.queryViewSelectInput = function(config) {
        var QUERYVIEW_SELECT, SCHEMA_SELECT, QUERY_SELECT;

        if (!config || !config.renderTo || !config.schemaInputId || !config.queryInputId) {
            var msg = 'Invalid config object. ';
            if (!config.renderTo) {
                msg += 'Missing renderTo property for the <select> element. ';
            }
            if (!config.schemaInputId) {
                msg += 'Missing schemaInputId property for the schema <select> element. ';
            }
            if (!config.queryInputId) {
                msg += 'Missing queryInputId property for the query <select> element. ';
            }
            console.error(msg);
            return;
        }

        if (!config.initValue) {
            config.initValue = '';
        }

        QUERYVIEW_SELECT = $("select[id='" + config.renderTo + "']");
        if (QUERYVIEW_SELECT.length !== 1) {
            console.error('Invalid config object. Expect to find exactly one <select> element with the name provided (found: ' + QUERYVIEW_SELECT.length + ').');
            return;
        }

        SCHEMA_SELECT = $("select[id='" + config.schemaInputId + "']");
        if (SCHEMA_SELECT.length !== 1) {
            console.error('Invalid config object. Expect to find exactly one <select> element with the name provided (found: ' + SCHEMA_SELECT.length + ').');
            return;
        }

        QUERY_SELECT = $("select[id='" + config.queryInputId + "']");
        if (QUERY_SELECT.length !== 1) {
            console.error('Invalid config object. Expect to find exactly one <select> element with the name provided (found: ' + QUERY_SELECT.length + ').');
            return;
        }

        QUERY_SELECT.on('change', function () {
            loadQueryViews(SCHEMA_SELECT, QUERY_SELECT, QUERYVIEW_SELECT, config.initValue, config.isRequired); //never include a blank option
        });
    };

    /**
     * Documentation specified in core/Query.js -- search for "@name importData"
     */
    impl.importData = function(config) {
        if (!window.FormData) {
            throw new Error('modern browser required');
        }

        var form = new FormData();

        form.append('schemaName', config.schemaName);
        form.append('queryName', config.queryName);
        if (config.text)
            form.append('text', config.text);
        if (config.path)
            form.append('path', config.path);
        if (config.format)
            form.append('format', config.format);
        if (config.module)
            form.append('module', config.module);
        if (config.moduleResource)
            form.append('moduleResource', config.moduleResource);
        if (config.importIdentity)
            form.append('importIdentity', config.importIdentity);
        if (config.importLookupByAlternateKey !== undefined)
            form.append('importLookupByAlternateKey', config.importLookupByAlternateKey);
        if (config.saveToPipeline !== undefined)
            form.append('saveToPipeline', config.saveToPipeline);
        if (config.insertOption !== undefined)
            form.append('insertOption', config.insertOption);

        if (config.file) {
            if (config.file instanceof File)
                form.append('file', config.file);
            else if (config.file.tagName === 'INPUT' && config.file.files.length > 0)
                form.append('file', config.file.files[0]);
        }

        return LABKEY.Ajax.request({
            url: config.importUrl || LABKEY.ActionURL.buildURL('query', 'import.api', config.containerPath),
            method: 'POST',
            success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope, false),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
            form: form,
            timeout: config.timeout
        });
    };

    return impl;

}(LABKEY.Query || new function() { return {}; }, jQuery);
