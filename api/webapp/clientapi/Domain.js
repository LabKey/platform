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
 * @namespace Domain static class to retrieve and edit domain definitions.
*/
LABKEY.Domain = new function()
{

    function getDomain(successCallback, failureCallback, parameters, containerPath)
    {
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("property", "getDomain", containerPath),
            method : 'GET',
            success: LABKEY.Utils.getCallbackWrapper(successCallback),
            failure: LABKEY.Utils.getCallbackWrapper(failureCallback, this, true),
            params : parameters
        });
    }

    function saveDomain(successCallback, failureCallback, parameters, containerPath)
    {
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("property", "saveDomain", containerPath),
            method : 'POST',
            success: LABKEY.Utils.getCallbackWrapper(successCallback),
            failure: LABKEY.Utils.getCallbackWrapper(failureCallback, this, true),
            jsonData : parameters,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    /** @scope LABKEY.Domain.prototype */
    return {

	/**
	* Gets a domain design.
	* @param {Function} successCallback Required. Function called if the
	*	"get" function executes successfully.  This function will be passed the following parameters,
    *    which describe the shape and fields of a domain:
    * <ul>
    * <li><b>domainId:</b> The uinque ID of this domain. (integer)</li>
    * <li><b>name:</b> The name of this domain. (string) </li>
    * <li><b>domainURI:</b> The URI of this domain. (string)</li>
    * <li><b>description:</b> The description of this domain. (string)</li>
    * <li><b>fields:</b> An array of objects that each describe a domain field.  Each object has the following fields:
    *      <ul>
    *          <li><b>propertyId:</b> The unique ID of this property. (integer)</li>
    *          <li><b>propertyURI:</b> The URI of this property. (string)</li>
    *          <li><b>ontologyURI:</b> The URI of the ontology this property belongs to. (string)</li>
    *          <li><b>name:</b> The name of this property. (string)</li>
    *          <li><b>description:</b> The description of this property (may be blank). (string)</li>
    *          <li><b>rangeURI:</b> The URI for this property's range definition. (string)</li>
    *          <li><b>conceptURI:</b> The URI of this property's concept. (string)</li>
    *          <li><b>label:</b> The friendly label for this property. (string)</li>
    *          <li><b>searchTerms:</b> The search terms for this property. (string)</li>
    *          <li><b>semanticType:</b> The semantic type of this property. (string)</li>
    *          <li><b>format:</b> The format string defined for this property. (string)</li>
    *          <li><b>required:</b> Indicates whether this field is required to have a value (i.e. cannot be null). (boolean)</li>
    *          <li><b>lookupContainer:</b> If this domain field is a lookup, this holds the container in which to look. (string)</li>
    *          <li><b>lookupSchema:</b> If this domain field is a lookup, this holds the schema in which to look. (string)</li>
    *          <li><b>lookupQuery:</b> if this domain field is a lookup, this holds the query in which to look. (string)</li>
    *      </ul>
    * </li>
    * </ul>
	* @param {Function} [failureCallback] Function called if execution of the "get" function fails.
	* @param {String} schemaName Name of the schema
	* @param {String} queryName Name of the query
	* @param {String} [containerPath] The container path in which the requested Domain is defined.
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
        get : function(successCallback, failureCallback, schemaName, queryName, containerPath)
        {
            getDomain(
                successCallback,
                failureCallback,
                {schemaName:schemaName, queryName:queryName}, 
                containerPath);
        },

        /**
         * Saves the provided domain design
         * @param {Function} successCallback Required. Function called if this
                  function executes successfully. No parameters will be passed to the success callback.
         * @param {Function} [failureCallback] Function called if execution of this function fails.
         * @param {Object} domainDesign The domain design to save. This must be an object of type {@link LABKEY.Domain.DomainDesign}
         * @param {String} schemaName Name of the schema
         * @param {String} queryName Name of the query
         * @param {String} [containerPath] The container path in which the requested Domain is defined.
         *       If not supplied, the current container path will be used.
         */
        save : function(successCallback, failureCallback, domainDesign, schemaName, queryName, containerPath)
        {
            saveDomain(
                successCallback,
                failureCallback,
                {domainDesign:domainDesign, schemaName:schemaName, queryName:queryName},
                containerPath);
        }
    };


};
