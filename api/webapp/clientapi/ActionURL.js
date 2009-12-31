/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 9.3
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
 * @namespace ActionURL static class to supply the current context path, container and action.
 *            Additionally, builds a URL from a controller and an action.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=url">LabKey URLs</a></li>
 *              </ul>
 *           </p>
 */
LABKEY.ActionURL = new function()
{
    // private member variables

    // private functions
    function buildParameterMap()
    {
        if (LABKEY.postParameters)
        {
            return LABKEY.postParameters;
        }
        var paramString = window.location.search;
        if (paramString.charAt(0) == '?')
            paramString = paramString.substring(1, paramString.length);
        var paramArray = paramString.split('&');
        var parameters = {};
        for (var i = 0; i < paramArray.length; i++)
        {
            var nameValue = paramArray[i].split('=');
            if (nameValue.length == 1 && nameValue[0] != '')
            {
                // Handle URL parameters with a name but no value or =
                nameValue[1] = '';
            }

            if (nameValue.length == 2)
            {
                var name = decodeURIComponent(nameValue[0]);
                if (undefined == parameters[name])
                    parameters[name] = decodeURIComponent(nameValue[1]);
                else
                {
                    var curValue = parameters[name];
                    if (Ext.isArray(curValue))
                        curValue.push(decodeURIComponent(nameValue[1]));
                    else
                        parameters[name] = [curValue, decodeURIComponent(nameValue[1])];
                }
            }
        }
        return parameters;
    }

    /** @scope LABKEY.ActionURL */
    return {
        // public functions

        /**
        * Gets the current context path.  The default context path for LabKey Server is '/labkey'.
		* @return {String} Current context path.
		*/
        getContextPath : function()
        {
            return LABKEY.contextPath;
        },

		/**
		* Gets the current action
		* @return {String} Current action.
		*/
        getAction : function()
        {
            var path = window.location.pathname;
            i = path.lastIndexOf("/");
            path = path.substring(i + 1);
            i = path.indexOf('.');
            return path.substring(0, i);
        },

		/**
		* Gets the current container path
		* @return {String} Current container path.
		*/
        getContainer : function()
        {
            var path = window.location.pathname;
            var start = path.indexOf("/", LABKEY.contextPath.length + 1);
            var end = path.lastIndexOf("/");
            return path.substring(start, end);
        },

        /**
         * Gets the current container's name. For example, if you are in the
         * /Project/SubFolder/MyFolder container, this method would return 'MyFolder'
         * while getContainer() would return the entire path.
         * @return {String} Current container name.
         */
        getContainerName : function()
        {
            var containerPath = LABKEY.ActionURL.getContainer();
            var start = containerPath.lastIndexOf("/");
            return containerPath.substring(start + 1);
        },

        /**
         * Get the current controller name
         * @return {String} Current controller.
         */
        getController : function()
        {
            var path = window.location.pathname;
            var start = LABKEY.contextPath.length + 1;
            var end = path.indexOf("/", start);
            return path.substring(start, end);
        },

        /**
        * Gets a URL parameter by name. Note that if the given parameter name is present more than once
        * in the query string, the returned value will be the first occurance of that parameter name. To get all
        * instances of the parameter, use getParameterArray().
        * @param {String} parameterName The name of the URL parameter.
        * @return {String} The value of the named parameter, or undefined of the parameter is not present.
        */
        getParameter : function(parameterName)
        {
            var val = buildParameterMap()[parameterName];
            return (val && Ext.isArray(val) && val.length > 0) ? val[0] : val;
        },

        /**
         * Gets a URL parameter by name. This method will always return an array of values, one for
         * each instance of the parameter name in the query string. If the parameter name appears only once
         * this method will return a one-element array.
         * @param {String} parameterName The name of the URL parameter.
         */
        getParameterArray : function(parameterName)
        {
            var val = buildParameterMap()[parameterName];
            return (val && !Ext.isArray(val)) ? [val] : val;
        },

        /**
        * Returns an object mapping URL parameter names to parameter values. If a given parameter
        * appears more than once on the query string, the value in the map will be an array instead
        * of a single value. Use Ext.isArray() to determine if the value is an array or not, or use
        * getParameter() or getParameterArray() to retrieve a specific parameter name as a single value
        * or array respectively.
        * @return {Object} Map of parameter names to values.
        */
        getParameters : function()
        {
            return buildParameterMap();
        },

        /**
		* Builds a URL from a controller and an action.  Uses the current container and context path.
		* @param {String} controller The controller to use in building the URL
		* @param {String} action The action to use in building the URL
		* @param {String} [containerPath] The container path to use (defaults to the current container)
		* @param {Object} [parameters] An object with properties corresponding to GET parameters to append to the URL.
		* Parameters will be encoded automatically. Parameter values that are arrays will be appended as multiple parameters
         * with the same name. (Defaults to no parameters)
		* @example Examples:

1. Build the URL for the 'plotChartAPI' action in the 'reports' controller within 
the current container:

	var url = LABKEY.ActionURL.buildURL("reports", "plotChartApi");

2.  Build the URL for the 'getWebPart' action in the 'reports' controller within 
the current container:

	var url = LABKEY.ActionURL.buildURL("project", "getWebPart");

3.  Build the URL for the 'updateRows' action in the 'query' controller within
the container "My Project/My Folder":

	var url = LABKEY.ActionURL.buildURL("query", "updateRows",
	    "My Project/My Folder");

4.  Navigate the browser to the study controller's begin action in the current
container:

    window.location = LABKEY.ActionURL.buildURL("study", "begin");

5.  Navigate the browser to the study controller's begin action in the folder
"/myproject/mystudyfolder":
         
    window.location = LABKEY.ActionURL.buildURL("study", "begin",
        "/myproject/mystudyfolder");

6.  Navigate to the list controller's insert action, passing a returnUrl parameter
that points back to the current page:
         
    window.location = LABKEY.ActionURL.buildURL("list", "insert",
         LABKEY.ActionURL.getContainer(), {listId: 50, returnUrl: window.location});
		* @return {String} URL constructed from the current container and context path,
					plus the specified controller and action.
		*/
        buildURL : function(controller, action, containerPath, parameters)
        {
            if(!containerPath)
                containerPath = this.getContainer();

            //ensure that container path begins and ends with a /
            if(containerPath.charAt(0) != "/")
                containerPath = "/" + containerPath;
            if(containerPath.charAt(containerPath.length - 1) != "/")
                containerPath = containerPath + "/";

            var newUrl = LABKEY.contextPath + "/" + controller + containerPath + action;
            if (-1 == action.indexOf('.'))
                newUrl += '.view';
            if (parameters)
            {
                newUrl += '?';
                for (var parameter in parameters)
                {
                    var pval = parameters[parameter];
                    if (Ext.isArray(pval))
                    {
                        for (var idx = 0; idx < pval.length; ++idx)
                        {
                            newUrl += encodeURIComponent(parameter) + '=' + encodeURIComponent(pval[idx]) + '&';
                        }
                    }
                    else
                        newUrl += encodeURIComponent(parameter) + '=' + encodeURIComponent(pval) + '&';
                }
                newUrl = newUrl.substring(0, newUrl.length - 1);
            }
            return newUrl;
        }
    };
};

