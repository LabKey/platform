package org.labkey.api.query;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.ColumnInfo;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Maps to a column name. The full string is separated by slashes, where
 * each token is a lookup.
 * There are many different senses of "column names" for ColumnInfo's.
 * There is:
 * 1. The name of the column in the underlying real table that the
 * ColumnInfo gets its value from (ColumnInfo.getValueSQL()).
 * 2. The name of the column in the ResultSet (ColumnInfo.getAlias())
 * 3. The name of the column in an URL filter, as a POST param in an update
 * form, etc. (ColumnInfo.getName()).
 * 4. The name of a column in LabKey SQL.
 *
 * FieldKey's should only ever be used for #3 and #4.
 *
 */
public class FieldKey implements Comparable
{
    /**
     * Construct a FieldKey from a string that may have been returned by ColumnInfo.getName()
     * or by FieldKey.toString(), or from an URL filter.
     * Try to avoid calling this on strings that are hard-coded in the source code.
     * Use FieldKey.fromParts(...) instead.  That version handles escaping the individual
     * parts of the FieldKey, and will enable us to maintain flexibility to change the
     * escaping algorithm. 
     */
    static public FieldKey fromString(String str)
    {
        if (str == null)
            return null;
        String[] encodedParts = StringUtils.splitPreserveAllTokens(str, "/");
        FieldKey ret = null;
        for (String encodedPart : encodedParts)
        {
            ret = new FieldKey(ret, decodePart(encodedPart));
        }
        return ret;
    }

    static public FieldKey fromString(FieldKey parent, String str)
    {
        List<String> parts = fromString(str).getParts();
        for (String part : parts)
        {
            parent = new FieldKey(parent, part);
        }
        return parent;
    }
    
    static public FieldKey fromParts(List<String> parts)
    {
        if (parts.size() == 0)
            return null;
        FieldKey parent = FieldKey.fromParts(parts.subList(0, parts.size() - 1));
        return new FieldKey(parent, parts.get(parts.size() - 1));
    }

    static public FieldKey fromParts(String...parts)
    {
        return fromParts(Arrays.asList(parts));
    }

    static public FieldKey fromParts(FieldKey ... parts)
    {
        FieldKey cur = null;
        for (FieldKey key : parts)
        {
            if (cur == null)
            {
                cur = key;
            }
            else
            {
                for (String part : key.getParts())
                {
                    cur = new FieldKey(cur, part);
                }
            }
        }
        return cur;
    }
    
    static public String encodePart(String str)
    {
        str = StringUtils.replace(str, "$", "$D");
        str = StringUtils.replace(str, "/", "$S");
        str = StringUtils.replace(str, "&", "$A");
        str = StringUtils.replace(str, "}", "$B");
        str = StringUtils.replace(str, "~", "$T");
        str = StringUtils.replace(str, ",", "$C");
        return str;
    }

    static public String decodePart(String str)
    {
        str = StringUtils.replace(str, "$C", ",");
        str = StringUtils.replace(str, "$T", "~");
        str = StringUtils.replace(str, "$B", "}");
        str = StringUtils.replace(str, "$A", "&");
        str = StringUtils.replace(str, "$S", "/");
        str = StringUtils.replace(str, "$D", "$");

        return str;
    }

    final FieldKey _parent;
    final String _name;

    public FieldKey(FieldKey parent, String name)
    {
        _parent = parent;
        _name = name;
    }

    public FieldKey getTable()
    {
        return _parent;
    }

    public FieldKey getParent()
    {
        return _parent;
    }

    public String getName()
    {
        return _name;
    }

    public boolean equals(Object other)
    {
        if (!(other instanceof FieldKey))
            return false;
        FieldKey that = (FieldKey) other;
        return ObjectUtils.equals(this._name, that._name) &&
                ObjectUtils.equals(this._parent, that._parent);
    }

    public int hashCode()
    {
        return ObjectUtils.hashCode(_name) ^ ObjectUtils.hashCode(_parent);
    }

    public List<String> getParts()
    {
        if (_parent == null)
            return Collections.singletonList(_name);
        List<String> ret = new ArrayList(_parent.getParts());
        ret.add(_name);
        return ret;
    }

    public String toString()
    {
        List<String> encodedParts = new ArrayList();
        for (String part : getParts())
        {
            String encodedPart = encodePart(part);
            encodedParts.add(encodedPart);
        }

        return StringUtils.join(encodedParts.iterator(), "/");
    }

    public String getLabel()
    {
        return getName();
    }

    public String getCaption()
    {
        return ColumnInfo.captionFromName(getName());
    }

    public int compareTo(Object o)
    {
        return toString().compareTo(String.valueOf(o));
    }

    /**
     * Returns a string appropriate for display to the user.
     */
    public String getDisplayString()
    {
        return StringUtils.join(getParts().iterator(), ".");
    }

    public boolean isAllColumns()
    {
        return false;
        // return getName().equals("*");
    }
}
