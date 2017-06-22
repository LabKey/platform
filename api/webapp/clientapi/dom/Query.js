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

    return impl;

}(LABKEY.Query, jQuery);
