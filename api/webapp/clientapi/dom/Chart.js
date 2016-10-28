/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2016 LabKey Corporation
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
(function($) {

    /**
     * @description Chart class to create and render live charts and imagemaps.
     *            <p>Additional Documentation:
     *              <ul>
     *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=charts">LabKey Chart Views</a></li>
     *              </ul>
     *            </p>
     * @class Chart class to create and render live charts and imagemaps.
     *            <p>Additional Documentation:
     *              <ul>
     *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=charts">LabKey Chart Views</a></li>
     *              </ul>
     *            </p>
     * @constructor
     * @param {Object} config Describes the chart's properties.
     * @param {String} config.schemaName Name of a schema defined within the current
     *                 container.  Example: 'study'.  See also: <a class="link"
     href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
     How To Find schemaName, queryName &amp; viewName</a>.
     * @param {String} config.queryName Name of a query table associated with the
     *                 chosen schema. Details below. Example: The name of one of
     *                 the study demo datasets: 'Physical Exam'.  See also: <a class="link"
     href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
     How To Find schemaName, queryName &amp; viewName</a>.
     * @param {String} [config.viewName] Name of a custom view associated with the
     *                 chosen query. Details below. Example: The name of a custom
     *                 view for the 'Physical Exam' study demo dataset: 'Custom Grid
     *                 View: Join for Cohort Views'. See also: <a class="link"
     href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
     How To Find schemaName, queryName &amp; viewName</a>.
     * @param {String} config.renderTo Element on the page where the chart will render.
     * @param {String} [config.renderImageMapTo] Element on the page where the imagemap will render.
     * @param {Function} [config.failure] Javascript function that can be used as a callback
     *                   if an error occurs. Otherwise, a standard listener will be used.
     * @param {String} config.columnXName Name of the column to plot on the X axis.
     * @param {String|[String]} config.columnYName Name of the column (or columns)
     *                 to plot on the Y axis. If multiple columns are desired, use array
     *                 format. Example: ['APXwtkg', 'APXbpsys', 'APXbpdia'].
     * @param {Bool} [config.logX='false'] Log scale X axis.
     * @param {Bool} [config.logY='false'] Log scale Y axis.
     * @param {LABKEY.Chart.TIME|LABKEY.Chart.XY} config.chartType The type of chart to plot.
     *                 You must use a TIME plot if you wish to plot a time variable on the X axis.
     * @param {Integer} [config.height='480'] Height in pixels
     * @param {Integer} [config.width='640'] Width in pixels
     * @param {Bool} [config.showMultipleYAxis='false'] If there are multiple Y axis
     *                 columns, display a separate Y axis for each column on the same plot.
     *                 This option is mutually exclusive with showMultipleCharts.
     * @param {Bool} [config.showLines='false']	Show lines between symbols.
     * @param {Bool} [config.showMultipleCharts='false'] If multiple Y columns are specified,
     *                 display each in its own plot.
     * @param {Bool} [config.verticalOrientation='false'] If showMultipleCharts is 'true' this
     *                 option controls whether charts are plotted vertically or
     *                 horizontally (the default).
     * @param {Function} [config.imageMapCallback] Javascript function that will be used in the URL's of the generated image map.
     *                 The specified function will be invoked with a single argument, a JSON object with : key, x, and y as fields. If
     *                 no function is specified, then the image map will show only tooltips.
     * @param {String|[String]} [config.imageMapCallbackColumns] Name(s) of additional columns whose names and values should be added
     *               To the JSON object for the config.imageMapCallback function.
     * @param {String} [config.containerPath] The container path in which the data for this chart is defined. If not supplied,
     *                  the current container path will be used.
     * @example Example #1 uses the "Physical Exam" dataset in the <a href = "https://www.labkey.org/Project/home/Study/demo/start.view?">Demo Study</a>
     * to plot a simple chart with one y-value:
     <pre name="code" class="xml">
     &lt;script type="text/javascript"&gt;
     var chartConfig = {
        schemaName: 'study',
        queryName: 'Physical Exam',
        renderTo: 'chartDiv',
        chartType: LABKEY.Chart.XY,
        columnXName: 'APXbpsys',
        columnYName: 'APXbpdia',
    };
     var chart = new LABKEY.Chart(chartConfig);
     chart.render();
     &lt;/script&gt;
     &lt;div id="chartDiv"&gt;&lt;/div&gt;

     Example #2 demonstrates plotting of multiple y-values:

     &lt;script type="text/javascript"&gt;
     var chartConfig2 = {
        queryName: 'Physical Exam',
        schemaName: 'study',
        chartType: LABKEY.Chart.XY,
        renderTo: 'chartDiv2',
        columnXName: 'APXwtkg',
        columnYName: ['APXwtkg', 'APXbpsys', 'APXbpdia']
    };
     var chart = new LABKEY.Chart(chartConfig2);
     chart.render();
     &lt;/script&gt;
     &lt;div id="chartDiv2"&gt;&lt;/div&gt;

     Example #3 demonstrates image map support:

     &lt;script type="text/javascript"&gt;

     var chartConfig = {
        schemaName: 'study',
        queryName: 'Physical Exam',
        renderTo: 'chartDiv3',
        renderImageMapTo: 'imageDiv',
        chartType: LABKEY.Chart.XY,
        columnXName: 'APXbpsys',
        columnYName: 'APXbpdia',
        imageMapCallback: 'showInfo'
    };
     var chart = new LABKEY.Chart(chartConfig);
     chart.render();

     function showInfo(info)
     {
         alert("key: " + info.key + "x: " + info.x + "y: " + info.y);
     }
     &lt;/script&gt;
     &lt;div id="chartDiv3"&gt;&lt;/div&gt;
     &lt;div id="imageDiv"&gt;&lt;/div&gt;
     </pre>
     */
    LABKEY.Chart = function(config)
    {
        // private member variables:
        this.config = config || {};

        var chartDivName = config.renderTo;
        var imageDivName = config.renderImageMapTo;
        var containerPath = config.containerPath;

        // private methods:
        var renderChartInternal = function(response)
        {
            var data = LABKEY.Utils.decode(response.responseText);

            // render the image tag inside the chart div
            if (imageDivName && data.imageMap)
            {
                $('#'+chartDivName).append('<img src="' + data.imageURL + '" usemap="' + imageDivName + '"/>');
                $('#'+imageDivName).append(data.imageMap);
            }
            else
            {
                $('#'+chartDivName).append('<img src="' + data.imageURL + '"/>')
            }
        };

        // public methods:
        /** @scope LABKEY.Chart.prototype */
        return {
            /**
             * Renders the chart to the div tags as specified in the config object passed to the constructor.
             */
            render : function()
            {
                if (!config.schemaName || !config.queryName)
                {
                    console.error("Configuration Error: config.schemaName and config.queryName are required parameters");
                    return;
                }
                if (!chartDivName)
                {
                    config.error("Configuration Error: config.renderTo is a required parameter.");
                    return;
                }
                if (imageDivName)
                {
                    config.imageMapName = imageDivName;
                }

                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL("reports", "plotChartApi", containerPath),
                    success: renderChartInternal,
                    failure: LABKEY.Utils.getOnFailure(config) || LABKEY.Utils.displayAjaxErrorResponse,
                    params: config
                });
            }
        };
    };

    /**
     * Scatterplot-type chart
     * @constant
     */
    LABKEY.Chart.XY = "1";
    /**
     * Time-based chart
     * @constant
     */
    LABKEY.Chart.TIME = "2";

})(jQuery);
