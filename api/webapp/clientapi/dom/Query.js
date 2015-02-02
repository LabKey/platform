/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2014-2015 LabKey Corporation
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
 * @namespace Query static class to programmatically retrieve, insert, update and
 *		delete data from LabKey public queries. <p/>
 *		{@link LABKEY.Query.selectRows} works for all LabKey public queries.  However,
 *		{@link LABKEY.Query.updateRows}, {@link LABKEY.Query.insertRows} and
 *		{@link LABKEY.Query.deleteRows} are not available for all tables and queries.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql">
 *                      LabKey SQL Reference</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
 *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
 *              </ul>
 *           </p>
 */

LABKEY.Query = new function(impl, $) {

    /**
     * Execute arbitrary LabKey SQL and export the results to Excel or TSV. After this method is
     * called, the user will be prompted to accept a file from the server, and most browsers will allow
     * the user to either save it or open it in an apporpriate application.
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
    impl.exportSql = function(config){

        // Insert a hidden <form> into to page, put the JSON into it, and submit it - the server's response
        // will make the browser pop up a dialog
        var formId = LABKEY.Utils.generateUUID();
        var html = '<form method="POST" id="' + formId + '"action="' + LABKEY.ActionURL.buildURL("query", "exportSql", config.containerPath) + '">';
        if (undefined != config.sql)
            html += '<input type="hidden" name="sql" value="' + LABKEY.Utils.encodeHtml(config.sql) + '" />';
        if (undefined != config.schemaName)
            html += '<input type="hidden" name="schemaName" value="' + LABKEY.Utils.encodeHtml(config.schemaName) + '" />';
        if (undefined != config.format)
            html += '<input type="hidden" name="format" value="' + LABKEY.Utils.encodeHtml(config.format) + '" />';
        if (undefined != config.containerFilter)
            html += '<input type="hidden" name="containerFilter" value="' + LABKEY.Utils.encodeHtml(config.containerFilter) + '" />';
        html += "</form>";
        $('body').append(html);
        $('form#' + formId).submit();
    };

    return impl;

}(LABKEY.Query, jQuery);
