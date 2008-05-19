/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 8.1
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
 * @namespace Assay static class to retrieve read-only assay definitions.
*/
LABKEY.Assay = new function()
{
    function getAssays(successCallback, failureCallback, parameters, containerPath)
    {
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("assay", "assayList", containerPath),
            method : 'GET',
            success: successCallback,
            failure: failureCallback,
            jsonData : parameters,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function getSuccessCallbackWrapper(successCallback)
    {
        return function(response, options)
        {
            var data = Ext.util.JSON.decode(response.responseText);
            successCallback(data.definitions);
        }
    }
    /** @scope LABKEY.Assay.prototype */
    return {

	/**
	* Gets all assays.
	* @param {Function} successCallback Required. Function called when the
			"getAll" function executes successfully.  Will be called with the argument: 
			{@link LABKEY.Assay.AssayDesign[]}.
	* @param {Function} [failureCallback] Function called when execution of the "getAll" function fails.
	* @param {String} [containerPath] The container path in which the requested Assays are defined.
	*       If not supplied, the current container path will be used.
	* @example Example:
<pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
	LABKEY.requiresClientAPI();
&lt;/script&gt;
&lt;script type="text/javascript"&gt;
	function successHandler(assayArray) 
	{ 
		var html = ''; 
		for (var defIndex = 0; defIndex < assayArray.length; defIndex ++) 
		{ 
			var definition = assayArray[defIndex ]; 
			html += '&lt;b&gt;' + definition.type + '&lt;/b&gt;: ' 
				+ definition.name + '&lt;br&gt;'; 
			for (var domain in definition.domains) 
			{ 
				html += '&nbsp;&nbsp;&nbsp;' + domain + '&lt;br&gt;'; 
				var properties = definition.domains[domain]; 
				for (var propertyIndex = 0; propertyIndex 
					< properties.length; propertyIndex++) 
				{ 
					var property = properties[propertyIndex]; 
					html += '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;' + property.name + 
						' - ' + property.typeName + '&lt;br&gt;'; 
				} 
			} 
		} 
		document.getElementById('testDiv').innerHTML = html; 
	} 

	function errorHandler(error) 
	{ 
		alert('An error occurred retrieving data.'); 
	}
	
	LABKEY.Assay.getAll(successHandler, errorHandler); 
&lt;/script&gt;
&lt;div id='testDiv'&gt;Loading...&lt;/div&gt;
</pre>
	  * @see LABKEY.Assay.AssayDesign
	  */
        getAll : function(successCallback, failureCallback, containerPath)
        {
            getAssays(getSuccessCallbackWrapper(successCallback), failureCallback, {}, containerPath);
        },
	  /**
	  * Gets an assay by name.
	  * @param {Function(LABKEY.Assay.AssayDesign[])} successCallback Function called when the "getByName" function executes successfully.
	  * @param {Function} [failureCallback] Function called when execution of the "getByName" function fails.
	  * @param {String} name String name of the assay.
	  * @param {String} [containerPath] The container path in which the requested Assay is defined.
	  *       If not supplied, the current container path will be used.
	  * @see LABKEY.Assay.AssayDesign
	  */
        getByName : function(successCallback, failureCallback, name, containerPath)
        {
            getAssays(getSuccessCallbackWrapper(successCallback), failureCallback, { name : name}, containerPath);
        },

	  /**
	  * Gets an assay by type.
	  * @param {Function(LABKEY.Assay.AssayDesign[])} successCallback Function called
				when the "getByType" function executes successfully.
	  * @param {Function} [failureCallback] Function called when execution of the "getByType" function fails.
	  * @param {String} type String name of the assay type.  "ELISpot", for example.
	  * @param {String} [containerPath] The container path in which the requested Assays are defined.
	  *       If not supplied, the current container path will be used.
 	  * @see LABKEY.Assay.AssayDesign
	  */
        getByType : function(successCallback, failureCallback, type, containerPath)
        {
            getAssays(getSuccessCallbackWrapper(successCallback), failureCallback, { type : type }, containerPath);
        },

	 /**
	 * Gets an assay by its ID.
	 * @param {Function(LABKEY.Assay.AssayDesign[])} successCallback Function called
				when the "getById" function executes successfully.
	 * @param {Function} [failureCallback] Function called when execution of the "getById" function fails.
	 * @param {Integer} id Unique integer ID for the assay.
	  * @param {String} [containerPath] The container path in which the requested Assay is defined.
	  *       If not supplied, the current container path will be used.
	 * @see LABKEY.Assay.AssayDesign
	 */
        getById : function(successCallback, failureCallback, id, containerPath)
        {
            getAssays(getSuccessCallbackWrapper(successCallback), failureCallback, { id : id }, containerPath);
        }
    }
};

/**
* @namespace
* @description AssayDesign static class to describe the shape and fields of an assay.  Each of the {@link LABKEY.Assay}
            'get' methods passes its success callback function an array of AssayDesigns.
* @property {String} name String name of the assay.
* @property {Integer} id Unique integer ID for the assay.
* @property {String} type String name of the assay type.  Example:  "ELISpot"
* @property {Bool} projectLevel Boolean indicating whether this is a project-level assay.
* @property {String} description String containing the assay description.
* @property {String} plateTemplate String containing the plate template name if the
            assay is plate based.  Undefined otherwise.
* @property {Object} domains Object mapping from String domain name to an array of
       {@link LABKEY.Assay.DomainFieldObject}s.
*/
LABKEY.Assay.AssayDesign = new function() {};

/**
* @namespace
* @description DomainFieldObject static class to describe a domain field for an assay.  See also {@link LABKEY.Assay} and
            {@link LABKEY.Assay.AssayDesign}.
* @property {String} name String name of the domain field.
* @property {String} typeName String name of the type of the domain field. (Human readable.)
* @property {String} typeURI String URI uniquely identifying the domain field type.
           (Not human readable.)
* @property {String} label String domain field label.
* @property {String} description String domain field description.
* @property {String} formatString String format string applied to the domain field.
* @property {Bool} required Boolean indicating whether a value is required for this domain field.
* @property {String} lookupContainer If this domain field is a lookup, lookupContainer holds the
            String path to the lookup container or null if the lookup in the
            same container.  Undefined otherwise.
* @property {String} lookupSchema If this domain field object is a lookup, lookupSchema holds the
           String name of the lookup schema.  Undefined otherwise.
* @property {String} lookupQuery If this domain field object is a lookup, lookupQuery holds the String
           name of the lookup query.  Undefined otherwise.
*/ LABKEY.Assay.DomainFieldObject = new function() {};

