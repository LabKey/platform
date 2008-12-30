/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 9.1
 * @license Copyright (c) 2008 LabKey Corporation
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
 * @namespace Experiment static class to allow creating hidden run groups and other experiment-related functionality.
 */
LABKEY.Experiment = new function()
{
    function getCallbackWrapper(fn, scope)
    {
        return function(response, options)
        {
            //ensure response is JSON before trying to decode
            var json = null;
            if(response && response.getResponseHeader && response.getResponseHeader['Content-Type']
                    && response.getResponseHeader['Content-Type'].indexOf('application/json') >= 0)
                json = Ext.util.JSON.decode(response.responseText);

            if(fn)
                fn.call(scope || this, json, response);
        };
    }

    /** @scope LABKEY.Experiment.prototype */
    return {

        /**
         * Create or recycle an existing run group. Run groups are the basis for some operations, like comparing
         * MS2 runs to one another.
         * @param config A configuration object with the following properties:
         * @param {function} config.successCallback A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>runGroup:</b> an object containing properties about the run group
         * This object will have the following shape:
         *  <ul>
         *  <li>container
         *      <ul>
         *          <li>rowId: the row id for the run group</li>
         *          <li>LSID: the LSID for the run group</li>
         *          <li>hidden: a boolean indicating if the group is hidden (always true for this method)</li>
         *          <li>name: a name for the group, may not be meaningful for hidden groups</li>
         *      </ul>
         *  </li>
         * </ul>
         * </li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {int[]} config.runIds An array of integer ids for the runs to be members of the group.
         * @param {function} [config.errorCallback] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @param {object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
         */
        createHiddenRunGroup : function (config)
        {
            if(!config.successCallback)
                Ext.Msg.alert("Programming Error", "You must specify a value for the config.successCallback when calling LABKEY.Security.getUserPermissions()!");

            Ext.Ajax.request(
            {
                url : LABKEY.ActionURL.buildURL("experiment", "createHiddenRunGroup", config.containerPath),
                method : 'POST',
                jsonData : { runIds : config.runIds },
                success: getCallbackWrapper(config.successCallback, config.scope),
                failure: getCallbackWrapper(config.failureCallback, config.scope),
                headers :
                {
                    'Content-Type' : 'application/json'
                }
            });
        }
    };

}
