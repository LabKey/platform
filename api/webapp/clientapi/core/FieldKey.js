/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2012 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

/**
 * The FieldKey constructor is private - use fromString() or fromParts() instead.
 *
 * @class FieldKey identifies a column from a table or query.
 * Use {@link LABKEY.FieldKey.fromString()} or {@link LABKEY.FieldKey.fromParts()} to create new FieldKeys.
 * <p>
 * Example: Create a new FieldKey for column "C" from foreign key column "A,B".
 <pre name="code">
 var fieldKey = LABKEY.FieldKey.fromParts(["A,B", "C"]);

 fieldKey.name;
 // => "C"
 fieldKey.parent;
 // => LABKEY.FieldKey for "A,B"
 fieldKey.toDisplayString();
 // => "A,B/C"
 fieldKey.toString();
 // => "A$CB/C"
 fieldKey.equals(LABKEY.FieldKey.fromString(fieldKey.toString()));
 // => true
 </pre>
 *
 * @constructor
 * @param {LABKEY.FieldKey} parent The parent FieldKey or null.
 * @param {string} name The FieldKey's unencoded name.
 */
LABKEY.FieldKey = function (parent, name)
{
    /**
     * The parent FieldKey or null.
     * @type LABKEY.FieldKey
     */
    this.parent = parent;
    /**
     * The FieldKey name.
     * @type string
     */
    this.name = name;
};

/**
 * Get the unencoded FieldKey name.
 * @returns {string}
 */
LABKEY.FieldKey.prototype.getName = function ()
{
    return this.name;
};

/**
 * Compares FieldKeys for equality.
 * @param {FieldKey} other
 * @returns {boolean} true if this FieldKey and the other are the same.
 */
LABKEY.FieldKey.prototype.equals = function(other)
{
    return other != null && this.toString().toLowerCase() == other.toString().toLowerCase();
};

/**
 * Returns an Array of unencoded FieldKey parts.
 * @returns {Array} Array of unencoded FieldKey parts.
 */
LABKEY.FieldKey.prototype.getParts = function()
{
    var ret = [];
    if (this.parent)
    {
        ret = this.parent.getParts();
    }
    ret.push(this.name);
    return ret;
};

/**
 * Returns an encoded FieldKey string suitable for sending to the server.
 * @returns {string} Encoded FieldKey string.
 */
LABKEY.FieldKey.prototype.toString = function()
{
    var parts = this.getParts();
    for (var i = 0; i < parts.length; i ++)
    {
        parts[i] = LABKEY.FieldKey.encodePart(parts[i]);
    }
    return parts.join('/');
};

/**
 * Returns a string suitable for display to the user.
 * @returns {string} Unencoded display string.
 */
LABKEY.FieldKey.prototype.toDisplayString = function()
{
    return this.getParts().join('.');
};

/**
 * @private
 * @returns {string} Returns a dotted string with any SQL identifiers quoted.
 */
LABKEY.FieldKey.prototype.toSQLString = function()
{
    var parts = this.getParts();
    for (var i = 0; i < parts.length; i ++)
    {
        if (LABKEY.FieldKey.needsQuotes(parts[i]))
        {
            parts[i] = LABKEY.FieldKey.quote(parts[i]);
        }
    }
    return parts.join('.');
};

/**
 * Use FieldKey encoding to encode a single part.
 * This matches org.labkey.api.query.FieldKey.encodePart() in the Java API.
 * @private
 * @static
 * @returns {string}
 */
LABKEY.FieldKey.encodePart = function(s)
{
    return s.replace(/\$/g, "$D").replace(/\//g, "$S").replace(/\&/g, "$A").replace(/\}/g, "$B").replace(/\~/g, "$T").replace(/\,/g, "$C");
};

/**
 * Use FieldKey encoding to decode a single part.
 * This matches org.labkey.api.query.FieldKey.decodePart() in the Java API.
 * @private
 * @static
 * @returns {string}
 */
LABKEY.FieldKey.decodePart = function(s)
{
    return s.replace(/\$C/g, ',').replace(/\$T/g, '~').replace(/\$B/g, '}').replace(/\$A/g, '&').replace(/\$S/g, '/').replace(/\$D/g, '$');
};

/**
 * Returns true if the part needs to be SQL quoted.
 * @private
 * @static
 * @returns {Boolean}
 */
LABKEY.FieldKey.needsQuotes = function(s)
{
    if (!s.match(/^[a-zA-Z][_\$a-zA-Z0-9]*$/))
        return true;
    if (s.match(/^(all|any|and|as|asc|avg|between|class|count|delete|desc|distinct|elements|escape|exists|false|fetch|from|full|group|having|in|indices|inner|insert|into|is|join|left|like|limit|max|min|new|not|null|or|order|outer|right|select|set|some|sum|true|union|update|user|versioned|where|case|end|else|then|when|on|both|empty|leading|member|of|trailing)$/i))
        return true;
    return false;
};

/**
 * SQL quotes a bare string.
 * @private
 * @static
 * @param {string} s String to be quoted.
 * @returns {string} Quoted string.
 */
LABKEY.FieldKey.quote = function(s)
{
    return '"' + s.replace(/\"/g, '""') + '"';
};

/**
 * Create new FieldKey from a FieldKey encoded string with parts separated by '/' characters.
 *
 * @static
 * @param {string} str FieldKey string with FieldKey encoded parts separated by '/' characters.
 * @returns {LABKEY.FieldKey}
 */
LABKEY.FieldKey.fromString = function(str)
{
    var rgStr = str.split('/');
    var ret = null;
    for (var i = 0; i < rgStr.length; i ++)
    {
        ret = new LABKEY.FieldKey(ret, LABKEY.FieldKey.decodePart(rgStr[i]));
    }
    return ret;

};

/**
 * Create new FieldKey from an Array of unencoded FieldKey string parts.
 *
 * @static
 * @param {Array} parts Array of unencoded FieldKey string parts.
 * @returns {LABKEY.FieldKey}
 */
LABKEY.FieldKey.fromParts = function(parts)
{
    var ret = null;
    for (var i = 0; i < parts.length; i ++)
    {
        ret = new LABKEY.FieldKey(ret, parts[i]);
    }
    return ret;
};
