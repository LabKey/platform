/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.query.AliasManager;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.Pair;

import java.util.Collections;

/**
 * Represents a member of a dimension. Mostly this is used for the Column axis members.
 *
 * Created by IntelliJ IDEA.
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

    private Object _value = null;
    private String _caption = null;
    private CrosstabDimension _dimension = null;

    public CrosstabMember(Object value, CrosstabDimension dimension)
    {
        this(value, dimension, null);
    }

    public CrosstabMember(Object value, CrosstabDimension dimension, String caption)
    {
        assert null != value && null != dimension;
        _value = value;
        _caption = caption;
        _dimension = dimension;
    }

    public Object getValue()
    {
        return _value;
    }

    public String getValueSQLAlias()
    {
        SqlDialect dialect = getDimension().getSourceColumn().getSqlDialect();
        // Prefix the value with an underscore to allow us to filter on integer values.  (Otherwise
        // the first digit will be replaced by an underscore, creating collisions between 10 and 20, for example.
        return AliasManager.makeLegalName("_" + getValue().toString(), dialect);
    }

    public void setValue(Object value)
    {
        _value = value;
    }

    public String getCaption()
    {
        return (null == _caption ? _value.toString() : _caption);
    }

    public void setCaption(String caption)
    {
        _caption = caption;
    }

    public CrosstabDimension getDimension()
    {
        return _dimension;
    }

    public void setDimension(CrosstabDimension dimension)
    {
        _dimension = dimension;
    }

    public DetailsURL replaceTokens(DetailsURL url)
    {
        ActionURL rewrittenURL = url.getActionURL().clone();
        for (Pair<String, String> param : rewrittenURL.getParameters())
        {
            if (VALUE_TOKEN.equals(param.getValue()))
            {
                rewrittenURL.replaceParameter(param.getKey(), getValue().toString());
            }
            if (CAPTION_TOKEN.equals(param.getValue()))
            {
                rewrittenURL.replaceParameter(param.getKey(), getCaption());
            }
        }
        return new DetailsURL(rewrittenURL, Collections.<String, Object>emptyMap());
    }

    public String replaceTokens(String template)
    {
        if(null == template)
            return null;
        String ret = template.replace(VALUE_TOKEN, getValue().toString());
        return ret.replace(CAPTION_TOKEN, getCaption());
    }
}
