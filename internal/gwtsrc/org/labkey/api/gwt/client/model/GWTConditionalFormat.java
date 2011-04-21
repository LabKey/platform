/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.io.Serializable;

/**
 * User: jeckels
 * Date: Aug 23, 2010
 */
public class GWTConditionalFormat implements Serializable, IsSerializable
{
    public static final String COLOR_REGEX = "[0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]";

    public static final String DATA_REGION_NAME = "format";
    public static final String COLUMN_NAME = "column";

    private boolean _bold = false;
    private boolean _italic = false;
    private boolean _strikethrough = false;
    private String _textColor = null;
    private String _backgroundColor = null;

    private String _filter;

    public GWTConditionalFormat() {}
    
    public GWTConditionalFormat(GWTConditionalFormat f)
    {
        setBold(f.isBold());
        setItalic(f.isItalic());
        setStrikethrough(f.isStrikethrough());
        setTextColor(f.getTextColor());
        setBackgroundColor(f.getBackgroundColor());

        setFilter(f.getFilter());
    }

    public boolean isBold()
    {
        return _bold;
    }

    public void setBold(boolean bold)
    {
        _bold = bold;
    }

    public boolean isItalic()
    {
        return _italic;
    }

    public void setItalic(boolean italic)
    {
        _italic = italic;
    }

    public boolean isStrikethrough()
    {
        return _strikethrough;
    }

    public void setStrikethrough(boolean strikethrough)
    {
        _strikethrough = strikethrough;
    }

    public String getTextColor()
    {
        return _textColor;
    }

    public void setTextColor(String textColor)
    {
        _textColor = textColor;
    }

    public String getBackgroundColor()
    {
        return _backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor)
    {
        _backgroundColor = backgroundColor;
    }

    public String getFilter()
    {
        return _filter;
    }

    public void setFilter(String filter)
    {
        _filter = filter;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof GWTConditionalFormat)) return false;

        GWTConditionalFormat that = (GWTConditionalFormat) o;

        if (_bold != that._bold) return false;
        if (_italic != that._italic) return false;
        if (_strikethrough != that._strikethrough) return false;
        if (_backgroundColor != null ? !_backgroundColor.equals(that._backgroundColor) : that._backgroundColor != null)
            return false;
        if (_filter != null ? !_filter.equals(that._filter) : that._filter != null) return false;
        if (_textColor != null ? !_textColor.equals(that._textColor) : that._textColor != null) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = (_bold ? 1 : 0);
        result = 31 * result + (_italic ? 1 : 0);
        result = 31 * result + (_strikethrough ? 1 : 0);
        result = 31 * result + (_textColor != null ? _textColor.hashCode() : 0);
        result = 31 * result + (_backgroundColor != null ? _backgroundColor.hashCode() : 0);
        result = 31 * result + (_filter != null ? _filter.hashCode() : 0);
        return result;
    }
}
