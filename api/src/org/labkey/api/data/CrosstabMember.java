/*
 * Copyright (c) 2007 LabKey Software Foundation
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
    public static final String VALUE_TOKEN = "${value}";
    public static final String CAPTION_TOKEN = "${caption}";

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

    public String replaceTokens(String template)
    {
        if(null == template)
            return null;
        String ret = template.replace(VALUE_TOKEN, getValue().toString());
        return ret.replace(CAPTION_TOKEN, getCaption());
    }
}
