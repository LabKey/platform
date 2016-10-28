/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2009-2016 LabKey Corporation
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

Ext.namespace("LABKEY", "LABKEY.ext");

/* NOTE: This has been updated for Ext 3.1.1
*  and required many changes from the Ext 2.0 version.
*  Because it relies on overriding non-public
*  member functions, this is likely to break in
*  future versions of Ext.
*/

LABKEY.ext.ExtendedJsonReader = Ext.extend(Ext.data.JsonReader, {
    /* Overridden to add .value to the id accessor */
    buildExtractors : function() {
        if(this.ef){
            return;
        }
        var s = this.meta, Record = this.recordType,
            f = Record.prototype.fields, fi = f.items, fl = f.length;

        if(s.totalProperty) {
            this.getTotal = this.createAccessor(s.totalProperty);
        }
        if(s.successProperty) {
            this.getSuccess = this.createAccessor(s.successProperty);
        }
        if (s.messageProperty) {
            this.getMessage = this.createAccessor(s.messageProperty);
        }
        this.getRoot = s.root ? this.createAccessor(s.root) : function(p){return p;};
        if (s.id || s.idProperty) {
            var g = this.createAccessor((s.id || s.idProperty) + ".value");  //MODIFIED: id column value is in .value property
            this.getId = function(rec) {
                var r = g(rec);
                return (r === undefined || r === '') ? null : r;
            };
        } else {
            this.getId = function(){return null;};
        }
        var ef = [];
        for(var i = 0; i < fl; i++){
            f = fi[i];
            var map = (f.mapping !== undefined && f.mapping !== null) ? f.mapping : f.name;
            ef.push(this.createAccessor(map));
        }
        this.ef = ef;
    },
    /**
     * Overridden to handle the new extended format
     * type-casts a single row of raw-data from server
     * @param {Object} data
     * @param {Array} items
     * @param {Integer} len
     * @private
     */
    extractValues : function(data, items, len) {
        var f, values = {};
        for(var j = 0; j < len; j++){
            f = items[j];
            if (this.ef[j]) //MODIFIED: silently ignore cases where this is no accessor--securityAdmin.js creates new fields for sorting
            {
                var v = this.ef[j](data);
                var value = undefined === v ? undefined : v.value;  //MODIFIED: column value is in .value property
                if (value == null)
                {
                    // Don't let Ext transforms null values to the "default" primitive values - 0, false, etc
                    values[f.name] = value;
                }
                else
                {
                    values[f.name] = f.convert((value !== undefined) ? value : f.defaultValue, data);
                }
            }
        }
        return values;
    }

});
