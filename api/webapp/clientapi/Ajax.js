/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2011 LabKey Corporation
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
 * @namespace
 * Adapter for <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.Ajax">Ext.Ajax</a>.
 */
LABKEY.Ajax = new function ()
{
    /** @scope LABKEY.Ajax */
    return {
        /**
         * Adapter for
         * <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=request" target="_blank">Ext.Ajax.request</a>.
         *
         * @param config See the config object documented in
         * <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=request" target="_blank">Ext.Ajax.request</a>.
         * 
         * @returns Mixed In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the response JSON (for "application/json" responses),
         * responseXML (for "text/xml" responses), or the responseText.
         */
        request : function (config)
        {
            var o = Ext.Ajax.request(config);
            if (Ext.isObject(o))
                return o.responseJSON || o.responseXML || o.responseText;
            return o;
        }
    }
};
