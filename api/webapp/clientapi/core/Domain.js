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

/**
 * @namespace Domain static class to retrieve and edit domain definitions.
 *
 *        <p>A domain is a collection of fields.  Each data type (e.g., Assays, Lists, Datasets, Sample Sets and
 *        Custom Protein Lists) provides specialized handling for the domains it
 *        defines.   The number of domains defined by a data type varies; for example, Assays
 *        define multiple domains (batch, run, etc.), while Lists
 *        and Datasets define only one domain each.</p>
 *
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=propertyFields">LabKey Dataset Domain Fields</a></li>
 *              </ul>
 *           </p>
*/
LABKEY.Domain = new function()
{

    function createDomain(success, failure, parameters, containerPath)
    {
        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL("property", "createDomain.api", containerPath),
            method : 'POST',
            success: LABKEY.Utils.getCallbackWrapper(success),
            failure: LABKEY.Utils.getCallbackWrapper(failure, this, true),
            jsonData : parameters,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function getDomain(success, failure, parameters, containerPath)
    {
        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL("property", "getDomain.api", containerPath),
            method : 'GET',
            success: LABKEY.Utils.getCallbackWrapper(success),
            failure: LABKEY.Utils.getCallbackWrapper(failure, this, true),
            params : parameters
        });
    }

    function saveDomain(success, failure, parameters, containerPath)
    {
        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL("property", "saveDomain.api", containerPath),
            method : 'POST',
            success: LABKEY.Utils.getCallbackWrapper(success),
            failure: LABKEY.Utils.getCallbackWrapper(failure, this, true),
            jsonData : parameters,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function deleteDomain(success, failure, parameters, containerPath)
    {
        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL("property", "deleteDomain.api", containerPath),
            method : 'POST',
            success: LABKEY.Utils.getCallbackWrapper(success),
            failure: LABKEY.Utils.getCallbackWrapper(failure, this, true),
            jsonData : parameters,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    /** @scope LABKEY.Domain */
    return {

        /**
         * Create a new domain with the given kind, domainDesign, and options or
         * specify a <a href='https://www.labkey.org/home/Documentation/wiki-page.view?name=domainTemplates'>domain template</a> to use for the domain creation.
         * Not all domain kinds can be created through this API.  Currently supported domain kinds are:
         * "IntList", "VarList", "SampleSet", and "DataClass".
         *
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required success callback.
         * @param {Function} [config.failure] Failure callback.
         * @param {String} config.kind The domain kind to create. One of "IntList", "VarList", "SampleSet", or "DataClass".
         * @param {String} config.domainName The name of the domain to create.
         * @param {String} config.module The name of a module that contains the domain template group.
         * @param {String} config.domainGroup The name of a domain template group.
         * @param {String} config.domainTemplate The name of a domain template within the domain group.
         * @param {Boolean} config.createDomain When using a domain template, create the domain.  Defaults to true.
         * @param {Boolean} config.importData When using a domain template, import initial data associated in the template.  Defaults to true.
         * @param {LABKEY.Domain.DomainDesign} config.domainDesign The domain design to save.
         * @param {Object} [config.options] Arguments used to create the specific domain type.
         * @param {String} [config.containerPath] The container path in which to create the domain.
         * @example Create domain:
<pre name="code" class="xml">
LABKEY.Domain.create({
  kind: "IntList",
  domainDesign: {
    name: "LookupCodes",
    description: "integer key list",
    fields: [{
      name: "id", rangeURI: "int"
    },{
      name: "code",
      rangeURI: "string", scale: 4
    }]
  },
  options: {
    keyName: "id"
  }
});
</pre>
         * @example Create domain from a <a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=domainTemplate'>domain template</a>:
<pre name="code" class="xml">
LABKEY.Domain.create({
  module: "mymodule",
  domainGroup: "codes",
  domainTemplate: "LookupCodes",
  importData: false
});
         </pre>
         * @example Import the initial data from the domain template of a previously created domain:
<pre name="code" class="xml">
LABKEY.Domain.create({
  module: "mymodule",
  domainGroup: "codes",
  domainTemplate: "LookupCodes",
  createDomain: false,
  importData: true
});
</pre>
         */
        create : function (config)
        {
            // new-style
            if (typeof config === "object")
            {
                createDomain(config.success, config.failure, config, config.containerPath);
            }
            // old-style
            else if (arguments.length > 1)
            {
                var success = arguments[0],
                    failure = arguments[1],
                    params = {},
                    containerPath;

                if ((arguments.length == 4 || arguments.length == 5) && typeof arguments[3] === "string")
                {
                    params.domainGroup = arguments[2];
                    params.domainTemplate = arguments[3];
                    containerPath = arguments[4];
                }
                else
                {
                    params.kind = arguments[2];
                    params.domainDesign = arguments[3];
                    params.options = arguments[4];
                    containerPath = arguments[5];
                }

                createDomain(success, failure, params, containerPath);
            }
        },

	/**
	* Gets a domain design.
     * @param {Object} config An object which contains the following configuration properties.
	* @param {Function} config.success Required. Function called if the
	*	"get" function executes successfully. Will be called with the argument {@link LABKEY.Domain.DomainDesign},
    *    which describes the fields of a domain.
	* @param {Function} [config.failure] Function called if execution of the "get" function fails.
	* @param {String} config.schemaName Name of the schema
	* @param {String} config.queryName Name of the query
	* @param {String} [config.containerPath] The container path in which the requested Domain is defined.
	*       If not supplied, the current container path will be used.
	* @example Example:
<pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
	function successHandler(domainDesign)
	{
		var html = '';

		html += '&lt;b&gt;' + domainDesign.name + ':&lt;/b&gt;&lt;br&gt; ';
		for (var i in domainDesign.fields)
		{
			html += '   ' + domainDesign.fields[i].name + '&lt;br&gt;';
		}
		document.getElementById('testDiv').innerHTML = html;
	}

	function errorHandler(error)
	{
		alert('An error occurred retrieving data: ' + error);
	}

	LABKEY.Domain.get(successHandler, errorHandler, 'study', 'StudyProperties');
&lt;/script&gt;
&lt;div id='testDiv'&gt;Loading...&lt;/div&gt;
</pre>
	  * @see LABKEY.Assay.AssayDesign
	  */
        get : function(config)
        {
            if (arguments.length > 1)
            {
                config = {
                    success: arguments[0],
                    failure: arguments[1],
                    schemaName: arguments[2],
                    queryName: arguments[3],
                    containerPath: arguments[4]
                };
            }

            getDomain(
                config.success,
                config.failure,
                {schemaName: config.schemaName, queryName: config.queryName},
                config.containerPath);
        },

        /**
         * Saves the provided domain design
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required. Function called if this
                  function executes successfully. No parameters will be passed to the success callback.
         * @param {Function} [config.failure] Function called if execution of this function fails.
         * @param {LABKEY.Domain.DomainDesign} config.domainDesign The domain design to save.
         * @param {String} config.schemaName Name of the schema
         * @param {String} config.queryName Name of the query
         * @param {String} [config.containerPath] The container path in which the requested Domain is defined.
         *       If not supplied, the current container path will be used.
         */
        save : function(config)
        {
            if (arguments.length > 1)
            {
                config = {
                    success: arguments[0],
                    failure: arguments[1],
                    domainDesign: arguments[2],
                    schemaName: arguments[3],
                    queryName: arguments[4],
                    containerPath: arguments[5]
                };
            }

            saveDomain(
                config.success,
                config.failure,
                {domainDesign: config.domainDesign, schemaName: config.schemaName, queryName: config.queryName},
                config.containerPath);
        },

        /**
         * Delete a domain.
         *
         * @param config
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success.
         * @param {Function} [config.failure]
         * @param {String} config.schemaName Name of the schema
         * @param {String} config.queryName Name of the query
         * @param {String} [config.containerPath] The container path in which the requested Domain is defined.
         *       If not supplied, the current container path will be used.
         */
        drop : function (config)
        {
            deleteDomain(
                config.success,
                config.failure,
                {domainDesign: config.domainDesign, schemaName: config.schemaName, queryName: config.queryName},
                config.containerPath);
        }
    };


};

/**
* @name LABKEY.Domain.DomainDesign
* @class  DomainDesign static class to describe the shape and fields of a domain.  The {@link LABKEY.Domain}
*             'get' and 'set' methods employ DomainDesign.
*/

/**#@+
* @memberOf LABKEY.Domain.DomainDesign#
* @field
*/

/**
* @name name
* @description   The name of this domain.
* @type String
*/

/**
* @name domainId
* @description The uinque ID of this domain.
* @type Integer
*/

/**
* @name description
* @description  The description of this domain.
* @type String
*/

/**
* @name domainURI
* @description  The URI of this domain.
* @type String
*/

/**
* @name fields
* @description An array of objects that each describe a domain field.  Each object has the following properties:
    *      <ul>
    *          <li><b>propertyId:</b> The unique ID of this field. (integer)</li>
    *          <li><b>propertyURI:</b> The URI of this field. (string)</li>
    *          <li><b>ontologyURI:</b> The URI of the ontology this field belongs to. (string)</li>
    *          <li><b>name:</b> The name of this field. (string)</li>
    *          <li><b>description:</b> The description of this field (may be blank). (string)</li>
    *          <li><b>rangeURI:</b> The URI for this field's range definition. (string)</li>
    *          <li><b>conceptURI:</b> The URI of this field's concept. (string)</li>
    *          <li><b>label:</b> The friendly label for this field. (string)</li>
    *          <li><b>searchTerms:</b> The search terms for this field. (string)</li>
    *          <li><b>semanticType:</b> The semantic type of this field. (string)</li>
    *          <li><b>format:</b> The format string defined for this field. (string)</li>
    *          <li><b>required:</b> Indicates whether this field is required to have a value (i.e. cannot be null). (boolean)</li>
    *          <li><b>lookupContainer:</b> If this domain field is a lookup, this holds the container in which to look. (string)</li>
    *          <li><b>lookupSchema:</b> If this domain field is a lookup, this holds the schema in which to look. (string)</li>
    *          <li><b>lookupQuery:</b> if this domain field is a lookup, this holds the query in which to look. (string)</li>
    *      </ul>
    * @type Object
*/

/**#@-*/
