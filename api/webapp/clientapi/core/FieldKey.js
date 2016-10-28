/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2012-2016 LabKey Corporation
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
 * @private
 * @class QueryKey The QueryKey is private -- use SchemaKey or FieldKey subclasses .fromString() or .fromParts() instead.
 *
 * @param parent
 * @param name
 */
LABKEY.QueryKey = function (parent, name)
{
    if (parent && !(parent instanceof parent.constructor))
        throw new Error("parent type not of same type as this");

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
 * Get the unencoded QueryKey name.
 * @returns {string}
 */
LABKEY.QueryKey.prototype.getName = function ()
{
    return this.name;
};

/**
 * Compares QueryKeys for equality.
 * @param {QueryKey} other
 * @returns {boolean} true if this QueryKey and the other are the same.
 */
LABKEY.QueryKey.prototype.equals = function(other)
{
    return other != null &&
            this.constructor == other.constructor &&
            this.toString().toLowerCase() == other.toString().toLowerCase();
};

/**
 * Returns an Array of unencoded QueryKey parts.
 * @returns {Array} Array of unencoded QueryKey parts.
 */
LABKEY.QueryKey.prototype.getParts = function()
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
 * @private
 */
LABKEY.QueryKey.prototype.toString = function(divider)
{
    var parts = this.getParts();
    for (var i = 0; i < parts.length; i ++)
    {
        parts[i] = LABKEY.QueryKey.encodePart(parts[i]);
    }
    return parts.join(divider);
};

/**
 * Returns the encoded QueryKey string as the JSON representation of a QueryKey.
 * Called by JSON.stringify().
 * @function
 */
LABKEY.QueryKey.prototype.toJSON = function()
{
    return this.toString();
};

/**
 * Returns a string suitable for display to the user.
 * @function
 * @returns {string} Unencoded display string.
 */
LABKEY.QueryKey.prototype.toDisplayString = function()
{
    return this.getParts().join('.');
};

/**
 * @private
 * @returns {string} Returns a dotted string with any SQL identifiers quoted.
 */
LABKEY.QueryKey.prototype.toSQLString = function()
{
    var parts = this.getParts();
    for (var i = 0; i < parts.length; i ++)
    {
        if (LABKEY.QueryKey.needsQuotes(parts[i]))
        {
            parts[i] = LABKEY.QueryKey.quote(parts[i]);
        }
    }
    return parts.join('.');
};

/**
 * Use QueryKey encoding to encode a single part.
 * This matches org.labkey.api.query.QueryKey.encodePart() in the Java API.
 * @private
 * @static
 * @returns {string}
 */
LABKEY.QueryKey.encodePart = function(s)
{
    return s.replace(/\$/g, "$D").replace(/\//g, "$S").replace(/\&/g, "$A").replace(/\}/g, "$B").replace(/\~/g, "$T").replace(/\,/g, "$C").replace(/\./g, "$P");
};

/**
 * Use QueryKey encoding to decode a single part.
 * This matches org.labkey.api.query.QueryKey.decodePart() in the Java API.
 * @private
 * @static
 * @returns {string}
 */
LABKEY.QueryKey.decodePart = function(s)
{
    return s.replace(/\$P/g, '.').replace(/\$C/g, ',').replace(/\$T/g, '~').replace(/\$B/g, '}').replace(/\$A/g, '&').replace(/\$S/g, '/').replace(/\$D/g, '$');
};

/**
 * Returns true if the part needs to be SQL quoted.
 * @private
 * @static
 * @returns {Boolean}
 */
LABKEY.QueryKey.needsQuotes = function(s)
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
LABKEY.QueryKey.quote = function(s)
{
    return '"' + s.replace(/\"/g, '""') + '"';
};


/**
 * @class SchemaKey identifies a schema path.
 * Use {@link LABKEY.SchemaKey.fromString()} or {@link LABKEY.SchemaKey.fromParts()} to create new SchemaKey.
 *
 * @constructor
 * @extends LABKEY.QueryKey
 * @param {LABKEY.SchemaKey} parent The parent SchemaKey or null.
 * @param {string} name The FieldKey's unencoded name.
 */
LABKEY.SchemaKey = function (parent, name)
{
    LABKEY.QueryKey.call(this, parent, name);
};
LABKEY.SchemaKey.prototype = new LABKEY.QueryKey;
LABKEY.SchemaKey.prototype.constructor = LABKEY.SchemaKey;

/**
 * Returns an encoded SchemaKey string suitable for sending to the server.
 * @function
 * @returns {string} Encoded SchemaKey string.
 */
LABKEY.SchemaKey.prototype.toString = function()
{
    return LABKEY.QueryKey.prototype.toString.call(this, '.');
};

/**
 * Create new SchemaKey from a SchemaKey encoded string with parts separated by '.' characters.
 *
 * @static
 * @param {string} str SchemaKey string with SchemaKey encoded parts separated by '.' characters.
 * @returns {LABKEY.SchemaKey}
 */
LABKEY.SchemaKey.fromString = function(str)
{
    var rgStr = str.split('.');
    var ret = null;
    for (var i = 0; i < rgStr.length; i ++)
    {
        ret = new LABKEY.SchemaKey(ret, LABKEY.QueryKey.decodePart(rgStr[i]));
    }
    return ret;

};

/**
 * Create new SchemaKey from an Array of unencoded SchemaKey string parts.
 *
 * @static
 * @param {Array} parts Array of unencoded SchemaKey string parts.
 * @returns {LABKEY.FieldKey}
 */
LABKEY.SchemaKey.fromParts = function(parts)
{
    var ret = null;
    for (var i = 0; i < arguments.length; i ++)
    {
        var arg = arguments[i];
        if (typeof arg === 'string')
        {
            ret = new LABKEY.SchemaKey(ret, arg);
        }
        else if (arg && arg.length)
        {
            for (var j = 0; j < arg.length; j++)
            {
                ret = new LABKEY.SchemaKey(ret, arg[j]);
            }
        }
        else
        {
            throw "Illegal argument to fromParts: " + arg;
        }
    }
    return ret;
};


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
 * @extends LABKEY.SchemaKey
 * @param {LABKEY.FieldKey} parent The parent FieldKey or null.
 * @param {string} name The FieldKey's unencoded name.
 */
LABKEY.FieldKey = function (parent, name)
{
    LABKEY.QueryKey.call(this, parent, name);
};
LABKEY.FieldKey.prototype = new LABKEY.QueryKey;
LABKEY.FieldKey.prototype.constructor = LABKEY.FieldKey;

/**
 * Returns an encoded FieldKey string suitable for sending to the server.
 * @function
 * @returns {string} Encoded FieldKey string.
 */
LABKEY.FieldKey.prototype.toString = function()
{
    return LABKEY.QueryKey.prototype.toString.call(this, '/');
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
        ret = new LABKEY.FieldKey(ret, LABKEY.QueryKey.decodePart(rgStr[i]));
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
LABKEY.FieldKey.fromParts = function()
{
    var ret = null;
    for (var i = 0; i < arguments.length; i ++)
    {
        var arg = arguments[i];
        if (typeof arg === 'string')
        {
            ret = new LABKEY.FieldKey(ret, arg);
        }
        else if (arg && arg.length)
        {
            for (var j = 0; j < arg.length; j++)
            {
                ret = new LABKEY.FieldKey(ret, arg[j]);
            }
        }
        else
        {
            throw "Illegal argument to fromParts: " + arg;
        }
    }
    return ret;
};

/** docs for inherited methods defined in private class QueryKey */

/**
 * Get the unencoded QueryKey name.
 * @memberOf LABKEY.SchemaKey
 * @function
 * @name getName
 * @returns {string}
 */

/**
 * Compares QueryKeys for equality.
 * @memberOf LABKEY.SchemaKey
 * @function
 * @name equals
 * @param {QueryKey} other
 * @returns {boolean} true if this QueryKey and the other are the same.
 */

/**
 * Returns an Array of unencoded QueryKey parts.
 * @memberOf LABKEY.SchemaKey
 * @function
 * @name getParts
 * @returns {Array} Array of unencoded QueryKey parts.
 */

/**
 * Returns the encoded QueryKey string as the JSON representation of a QueryKey.
 * Called by JSON.stringify().
 * @memberOf LABKEY.SchemaKey
 * @function
 * @name toJSON
 */

/**
 * Returns a string suitable for display to the user.
 * @memberOf LABKEY.SchemaKey
 * @function
 * @name toDisplayString
 */

/**
 * Get the unencoded QueryKey name.
 * @memberOf LABKEY.FieldKey
 * @function
 * @name getName
 * @returns {string}
 */

/**
 * Compares QueryKeys for equality.
 * @memberOf LABKEY.FieldKey
 * @function
 * @name equals
 * @param {QueryKey} other
 * @returns {boolean} true if this QueryKey and the other are the same.
 */

/**
 * Returns an Array of unencoded QueryKey parts.
 * @memberOf LABKEY.FieldKey
 * @function
 * @name getParts
 * @returns {Array} Array of unencoded QueryKey parts.
 */

/**
 * Returns the encoded QueryKey string as the JSON representation of a QueryKey.
 * Called by JSON.stringify().
 * @memberOf LABKEY.FieldKey
 * @function
 * @name toJSON
 */

/**
 * Returns a string suitable for display to the user.
 * @memberOf LABKEY.FieldKey
 * @function
 * @name toDisplayString
 */
