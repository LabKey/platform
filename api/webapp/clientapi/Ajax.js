/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2011 LabKey Corporation
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

LABKEY.Ajax = new function ()
{
    return {
        request : function (config)
        {
            // NOTE: in client-side scripts, Ext.Ajax.request will return a transaction id
            // for the async request.  In a server-side script, Ext.Ajax.request will return
            // the response object.  In addition, responseJSON will be a JSON object for "application/json" responses.
            var o = Ext.Ajax.request(config);
            if (Ext.isObject(o))
                return o.responseJSON || o.responseXML || o.responseText;
            return o;
        }
    }
};
