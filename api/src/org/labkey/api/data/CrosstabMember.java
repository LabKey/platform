/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.Pair;

import java.util.Collections;

/**
 * Represents a member of a dimension -- the pivot values of the dimension.
 * Mostly this is used for the Column axis members.
 * CrosstabMembers are considered equal if the dimension, value, and caption are equal.
 *
 * User: Dave
 * Date: Jan 25, 2008
 * Time: 4:16:03 PM
 */
public class CrosstabMember
{
    public static final String VALUE_NAME = "**value**";
    public static final String VALUE_TOKEN = "${" + VALUE_NAME + "}";
    public static final String CAPTION_NAME = "**caption**";
    public static final String CAPTION_TOKEN = "${" + CAPTION_NAME + "}";

    private @Nullable Object _value = null;
    private @Nullable String _caption = null;
    private @NotNull FieldKey _dimensionFieldKey = null;

    public CrosstabMember(@Nullable Object value, @NotNull CrosstabDimension dimension, @Nullable String caption)
    {
        this(value, dimension.getFieldKey(), caption);
    }

    public CrosstabMember(@Nullable Object value, @NotNull FieldKey dimensionFieldKey, @Nullable String caption)
    {
        assert null != dimensionFieldKey;
        _value = value;
        _caption = caption;
        _dimensionFieldKey = dimensionFieldKey;
    }

    public @Nullable Object getValue()
    {
        return _value;
    }

    public String getValueSQLAlias(SqlDialect dialect)
    {
        // Prefix the value with an underscore to allow us to filter on integer values.  (Otherwise
        // the first digit will be replaced by an underscore, creating collisions between 10 and 20, for example.
        return AliasManager.makeLegalName("_" + String.valueOf(getValue()), dialect);
    }

    public void setValue(@Nullable Object value)
    {
        _value = value;
    }

    public String getCaption()
    {
        return (null == _caption ? String.valueOf(getValue()) : _caption);
    }

    public void setCaption(@Nullable String caption)
    {
        _caption = caption;
    }

    @NotNull
    public FieldKey getDimensionFieldKey()
    {
        return _dimensionFieldKey;
    }

    public void setDimensionFieldKey(@NotNull FieldKey dimensionFieldKey)
    {
        _dimensionFieldKey = dimensionFieldKey;
    }

    public DetailsURL replaceTokens(DetailsURL url)
    {
        ActionURL rewrittenURL = url.getActionURL().clone();
        for (Pair<String, String> param : rewrittenURL.getParameters())
        {
            if (VALUE_TOKEN.equals(param.getValue()))
            {
                rewrittenURL.replaceParameter(param.getKey(), String.valueOf(getValue()));
            }
            if (CAPTION_TOKEN.equals(param.getValue()))
            {
                rewrittenURL.replaceParameter(param.getKey(), getCaption());
            }
        }
        return new DetailsURL(rewrittenURL, Collections.emptyMap());
    }

    // XXX: Use DetailsURL instead
    public String replaceTokens(String template)
    {
        if(null == template)
            return null;
        String ret = template.replace(VALUE_TOKEN, String.valueOf(getValue()));
        return ret.replace(CAPTION_TOKEN, getCaption());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CrosstabMember member = (CrosstabMember) o;

        if (_caption != null ? !_caption.equals(member._caption) : member._caption != null) return false;
        if (_dimensionFieldKey != null ? !_dimensionFieldKey.equals(member._dimensionFieldKey) : member._dimensionFieldKey != null)
            return false;
        if (_value != null ? !_value.equals(member._value) : member._value != null) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _value != null ? _value.hashCode() : 0;
        result = 31 * result + (_caption != null ? _caption.hashCode() : 0);
        result = 31 * result + (_dimensionFieldKey != null ? _dimensionFieldKey.hashCode() : 0);
        return result;
    }
}
