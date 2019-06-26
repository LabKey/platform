/*
 * Copyright (c) 2012-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.query;

import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.PageFlowUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base class of {@link FieldKey} and {@link SchemaKey}.
 * The only difference between the two is the default divider.
 */
/*package*/ abstract public class QueryKey<T extends QueryKey<T>> implements Comparable<T>, Serializable
{
    // Abstracts the constructor of QueryKey subclasses so we can reuse the static factory
    // methods in QueryKey instead of duplicating them in both FieldKey and SchemaKey.
    protected interface Factory<T extends QueryKey<T>>
    {
        T create(T parent, String name);
    }

    /**
     * same as fromString() but URL encoded
     */
    static protected <T extends QueryKey<T>> T decode(Factory<T> factory, String divider, String str)
    {
        if (str == null)
            return null;
        String[] encodedParts = StringUtils.splitPreserveAllTokens(str, divider);
        T ret = null;
        for (String encodedPart : encodedParts)
            ret = factory.create(ret, PageFlowUtil.decode(encodedPart));
        return ret;
    }

    static protected <T extends QueryKey<T>> T fromString(Factory<T> factory, String divider, String str)
    {
        if (str == null)
            return null;
        String[] encodedParts = StringUtils.splitPreserveAllTokens(str, divider);
        T ret = null;
        for (String encodedPart : encodedParts)
            ret = factory.create(ret, decodePart(encodedPart));
        return ret;
    }

    static protected <T extends QueryKey<T>> T fromString(Factory<T> factory, String divider, T parent, String str)
    {
        List<String> parts = fromString(factory, divider, str).getParts();
        for (String part : parts)
        {
            parent = factory.create(parent, part);
        }
        return parent;
    }

    static protected <T extends QueryKey<T>> T fromParts(Factory<T> factory, List<String> parts)
    {
        if (parts.size() == 0)
            return null;

        if (parts.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("parts contains null: " + StringUtils.join(parts, "."));

        T parent = QueryKey.fromParts(factory, parts.subList(0, parts.size() - 1));
        return factory.create(parent, parts.get(parts.size() - 1));
    }

    static public <T extends QueryKey<T>> T fromParts(Factory<T> factory, T... parts)
    {
        if (Arrays.stream(parts).anyMatch(Objects::isNull))
            throw new IllegalArgumentException("parts contains null: " + StringUtils.join(parts, "."));

        T cur = null;
        for (T key : parts)
        {
            if (cur == null)
                cur = key;
            else
            {
                for (String part : key.getParts())
                    cur = factory.create(cur, part);
            }
        }
        return cur;
    }

    static public String encodePart(String str)
    {
        return StringUtils.replaceEach(str, ILLEGAL, REPLACEMENT);
    }

    static public String decodePart(String str)
    {
        return StringUtils.replaceEach(str, REPLACEMENT, ILLEGAL);
    }

    private static final String[] ILLEGAL = {"$", "/", "&", "}", "~", ",", "."};
    private static final String[] REPLACEMENT = {"$D", "$S", "$A", "$B", "$T", "$C", "$P"};

    /**
     * Check for invalid characters in a <code>divider</code> delimited string.
     * WARNING: Don't use this method to check if a string is encoded or not since
     * it is impossible to do so reliably. The best we can do is check for
     * invalid characters and check if a dollar-replacement character is present.
     * For example, the string "My$Stuff" may or may not be encoded -- this algorithm
     * will indicate that doesn't need encoding since '$S' is already encoded.
     */
    static protected boolean needsEncoding(String str, String divider)
    {
        if (str == null)
            return false;

        String[] encodedParts = StringUtils.splitPreserveAllTokens(str, divider);
        for (String encodedPart : encodedParts)
        {
            // check for one of the illegal characters but handle '$' separately
            for (int i = 0; i < encodedPart.length(); i++)
            {
                char c = encodedPart.charAt(i);
                if (c == '$')
                {
                    // check for a "$" at the end without a replacement character
                    if (i == encodedPart.length()-1)
                        return true;

                    // check the next character is a valid replacement character,
                    // otherwise it's a bare '$' that needs encoding
                    String replacement = encodedPart.substring(i, i+2);
                    if (!contains(REPLACEMENT, replacement))
                        return true;
                    i++;
                }
                else if (contains(ILLEGAL, String.valueOf(c)))
                    return true;
            }
        }

        return false;
    }

    static protected boolean contains(String[] strs, String s)
    {
        for (int i = 0; i < strs.length; i++)
            if (strs[i].equals(s))
                return true;

        return false;
    }


    final QueryKey<T> _parent;
    final String _name;
    final int _hash;


    protected QueryKey(QueryKey<T> parent, @NotNull String name)
    {
        Objects.requireNonNull(name, "name must not be null");
        _parent = parent;
        _name = name;
        _hash = _name.toLowerCase().hashCode() ^ Objects.hashCode(_parent);
    }

    protected QueryKey(QueryKey<T> parent, Enum name)
    {
        this(parent, name.toString());
    }

    protected abstract String getDivider();

    public QueryKey getParent()
    {
        return _parent;
    }

    public @NotNull String getName()
    {
        return _name;
    }

    private boolean strEqualsIgnoreCase(String a, String b)
    {
        if (a == null && b == null)
            return true;
        if (a == null)
            return false;
        return a.equalsIgnoreCase(b);
    }

    public boolean equals(Object other)
    {
        if (other == null || !(getClass().equals(other.getClass())))
            return false;
        QueryKey that = (QueryKey) other;
        return strEqualsIgnoreCase(_name, that._name) &&
                Objects.equals(_parent, that._parent);
    }

    public int hashCode()
    {
        return _hash;
    }

    @JsonValue
    public List<String> getParts()
    {
        if (_parent == null)
            return Collections.singletonList(_name);
        List<String> ret = new ArrayList<>(_parent.getParts());
        ret.add(_name);
        return ret;
    }


    /**
     * Returns the number of parts in this QueryKey. Equivalent to getParts().size(), but more efficient.
     * @return Number of parts that would be returned by getParts()
     */
    public int size()
    {
        return _parent == null ? 1 : _parent.size() + 1;
    }


    /**
     * generate a URL encoded string representing this field key
     * may be parsed by FieldKey.decode()
     */
    public String encode()
    {
        List<String> encodedParts = new ArrayList<>();
        for (String part : getParts())
        {
            String encodedPart = PageFlowUtil.encode(part);
            encodedParts.add(encodedPart);
        }
        return StringUtils.join(encodedParts.iterator(), getDivider());
    }

    public String toString()
    {
        List<String> encodedParts = new ArrayList<>();
        for (String part : getParts())
        {
            String encodedPart = encodePart(part);
            encodedParts.add(encodedPart);
        }

        return StringUtils.join(encodedParts.iterator(), getDivider());
    }

    /**
     * Returns a string appropriate for display to the user.
     */
    public String toDisplayString()
    {
        return StringUtils.join(getParts().iterator(), ".");
    }

    /**
     * Returns a string appropriate for usage in LabKey SQL.
     */
    public String toSQLString()
    {
        List<String> parts = getParts();
        StringBuilder escapedName = new StringBuilder();
        String sep = "";
        for (String part : parts)
        {
            escapedName.append(sep);
            if (needsQuotes(part))
                escapedName.append("\"").append(part.replace("\"", "\"\"")).append("\"");
            else
                escapedName.append(part);
            sep = ".";
        }
        return escapedName.toString();
    }

    protected boolean needsQuotes(String part)
    {
        return true;
    }

    public boolean startsWith(@NotNull QueryKey<? extends QueryKey> prefix)
    {
        if (size() < prefix.size())
            return false;

        List<String> target = getParts();
        List<String> prefixParts = prefix.getParts();

        for (int i = 0; i < prefixParts.size(); i++)
        {
            if (!target.get(i).equalsIgnoreCase(prefixParts.get(i)))
                return false;
        }

        return true;
    }
}
