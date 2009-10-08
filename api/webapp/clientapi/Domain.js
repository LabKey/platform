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
			"get" function executes successfully.  Will be called with the argument:
			{@link LABKEY.Domain.DomainDesign}.
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

/**
* @namespace DomainDesign static class to describe the shape and fields of a domain.  The {@link LABKEY.Domain}
            'get' method passes its success callback function a DomainDesign.
* @property {Integer} domainId The uinque ID of this domain.
* @property {String} name  The name of this domain.
* @property {String} domainURI The URI of this domain.
* @property {String} description The description of this domain.
* @property {Object[]} fields An array of {@link LABKEY.Domain.DomainFieldObject}s.
*/
LABKEY.Domain.DomainDesign = new function() {};

/**
* @namespace DomainFieldObject static class to describe a domain field for a domain.  See also {@link LABKEY.Domain} and
            {@link LABKEY.Domain.DomainDesign}.
* @property {Integer} propertyId The unique ID of this property.
* @property {String} propertyURI The URI of this property.
* @property {String} ontologyURI The URI of the ontology this property belongs to.
* @property {String} name The name of this property.
* @property {String} description The description of this property (may be blank).
* @property {String} rangeURI The URI for this property's range definition.
* @property {String} conceptURI The URI of this property's concept.
* @property {String} label The friendly label for this property.
* @property {String} searchTerms The search terms for this property.
* @property {String} semanticType The semantic type of this property.
* @property {String} format The format string defined for this property.
* @property {Boolean} required indicates if this field is required to have a value (i.e. cannot be null)
* @property {String} lookupContainer If this domain field is a lookup, this holds the container in which to look
* @property {String} lookupSchema If this domain field is a lookup, this holds the schema in which to look
* @property {String} lookupQuery if this domain field is a lookup, this holds the query in which to look
*/
LABKEY.Domain.DomainFieldObject = new function() {};
