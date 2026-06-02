/*
 * Copyright (c) 2011-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.reader.jxl;

import jxl.format.Alignment;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellPropertyType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Color;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class JxlCellStyle implements CellStyle
{
    private static final String DEFAULT_FORMAT = "General";

    private final jxl.Cell _cell;
    private final jxl.format.CellFormat _format;

    public JxlCellStyle(jxl.Cell cell)
    {
        _cell = cell;
        _format = cell.getCellFormat();
    }

    @Override
    public short getIndex()
    {
        return 0;
    }

    @Override
    public void setDataFormat(short fmt)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getDataFormat()
    {
        return 0;
    }

    @Override
    public String getDataFormatString()
    {
        if (_format != null)
            return StringUtils.defaultIfBlank(_format.getFormat().getFormatString(), DEFAULT_FORMAT);

        return DEFAULT_FORMAT;
    }

    @Override
    public void setFont(Font font)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getFontIndex()
    {
        return 0;
    }

    @Override
    public void setHidden(boolean hidden)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getHidden()
    {
        return _cell.isHidden();
    }

    @Override
    public void setLocked(boolean locked)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getLocked()
    {
        return _format != null && _format.isLocked();
    }

    private static final Map<Alignment, HorizontalAlignment> HORIZONTAL_ALIGNMENT_MAP = Map.of(
            Alignment.GENERAL, HorizontalAlignment.GENERAL,
            Alignment.LEFT, HorizontalAlignment.LEFT,
            Alignment.CENTRE, HorizontalAlignment.CENTER,
            Alignment.RIGHT, HorizontalAlignment.RIGHT,
            Alignment.FILL, HorizontalAlignment.FILL,
            Alignment.JUSTIFY, HorizontalAlignment.JUSTIFY);
        // Note: No JXL options for CENTER_SELECTION or DISTRIBUTED

    @Override
    public HorizontalAlignment getAlignment()
    {
        Alignment jxlAlignment = (_format != null ? _format.getAlignment() : Alignment.GENERAL);
        return HORIZONTAL_ALIGNMENT_MAP.get(jxlAlignment);
    }

    @Override
    public void setWrapText(boolean wrapped)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getWrapText()
    {
        return _format != null && _format.getWrap();
    }

    private static final Map<jxl.format.VerticalAlignment, VerticalAlignment> VERTICAL_ALIGNMENT_MAP = new HashMap<>();

    static
    {
        VERTICAL_ALIGNMENT_MAP.put(jxl.format.VerticalAlignment.TOP, VerticalAlignment.TOP);
        VERTICAL_ALIGNMENT_MAP.put(jxl.format.VerticalAlignment.CENTRE, VerticalAlignment.CENTER);
        VERTICAL_ALIGNMENT_MAP.put(jxl.format.VerticalAlignment.BOTTOM, VerticalAlignment.BOTTOM);
        VERTICAL_ALIGNMENT_MAP.put(jxl.format.VerticalAlignment.JUSTIFY, VerticalAlignment.JUSTIFY);

        // Note: No JXL option for DISTRIBUTED
    }

    @Override
    public VerticalAlignment getVerticalAlignment()
    {
        jxl.format.VerticalAlignment jxlVerticalAlignment = (_format != null ? _format.getVerticalAlignment() : jxl.format.VerticalAlignment.TOP);
        return VERTICAL_ALIGNMENT_MAP.get(jxlVerticalAlignment);
    }

    @Override
    public void setRotation(short rotation)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getRotation()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setIndention(short indent)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getIndention()
    {
        if (_format != null)
            return (short)_format.getIndentation();
        return 0;
    }

    @Override
    public BorderStyle getBorderLeft()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public BorderStyle getBorderRight()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public BorderStyle getBorderTop()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public BorderStyle getBorderBottom()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setLeftBorderColor(short color)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getLeftBorderColor()
    {
        if (_format != null)
            return (short)_format.getBorderColour(jxl.format.Border.LEFT).getValue();
        return 0;
    }

    @Override
    public void setRightBorderColor(short color)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getRightBorderColor()
    {
        if (_format != null)
            return (short)_format.getBorderColour(jxl.format.Border.RIGHT).getValue();
        return 0;
    }

    @Override
    public void setTopBorderColor(short color)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getTopBorderColor()
    {
        if (_format != null)
            return (short)_format.getBorderColour(jxl.format.Border.TOP).getValue();
        return 0;
    }

    @Override
    public void setBottomBorderColor(short color)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getBottomBorderColor()
    {
        if (_format != null)
            return (short)_format.getBorderColour(jxl.format.Border.BOTTOM).getValue();
        return 0;
    }

    @Override
    public FillPatternType getFillPattern()
    {
        int ordinal = (_format != null ? _format.getPattern().getValue() : 0);
        return FillPatternType.forInt(ordinal);
    }

    @Override
    public void setFillBackgroundColor(short bg)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getFillBackgroundColor()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Color getFillBackgroundColorColor()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setFillForegroundColor(short bg)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getFillForegroundColor()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Color getFillForegroundColorColor()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void cloneStyleFrom(CellStyle source)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setAlignment(HorizontalAlignment horizontalAlignment)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setVerticalAlignment(VerticalAlignment verticalAlignment)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setBorderLeft(BorderStyle borderStyle)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setBorderRight(BorderStyle borderStyle)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setBorderTop(BorderStyle borderStyle)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setBorderBottom(BorderStyle borderStyle)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setFillPattern(FillPatternType fillPatternType)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setShrinkToFit(boolean b)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getShrinkToFit()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setQuotePrefixed(boolean quotePrefix)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getQuotePrefixed()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getFontIndexAsInt()
    {
        return getFontIndex();
    }

    @Override
    public void setFillBackgroundColor(Color color)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setFillForegroundColor(Color color)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    /**
     * A pared down version of {@link org.apache.poi.ss.util.CellUtil#getFormatProperties(org.apache.poi.ss.usermodel.CellStyle)}
     * that only includes properties supported by this implementation.
     */
    @Override
    public EnumMap<CellPropertyType, Object> getFormatProperties()
    {
        EnumMap<CellPropertyType, Object> properties = new EnumMap<>(CellPropertyType.class);
        properties.put(CellPropertyType.ALIGNMENT, getAlignment());
        properties.put(CellPropertyType.VERTICAL_ALIGNMENT, getVerticalAlignment());
        //properties.put(CellPropertyType.BORDER_BOTTOM, getBorderBottom());
        //properties.put(CellPropertyType.BORDER_LEFT, getBorderLeft());
        //properties.put(CellPropertyType.BORDER_RIGHT, getBorderRight());
        //properties.put(CellPropertyType.BORDER_TOP, getBorderTop());
        properties.put(CellPropertyType.BOTTOM_BORDER_COLOR, getBottomBorderColor());
        properties.put(CellPropertyType.DATA_FORMAT, getDataFormat());
        properties.put(CellPropertyType.FILL_PATTERN, getFillPattern());

        //properties.put(CellPropertyType.FILL_FOREGROUND_COLOR, getFillForegroundColor());
        //properties.put(CellPropertyType.FILL_BACKGROUND_COLOR, getFillBackgroundColor());
        //properties.put(CellPropertyType.FILL_FOREGROUND_COLOR_COLOR, getFillForegroundColorColor());
        //properties.put(CellPropertyType.FILL_BACKGROUND_COLOR_COLOR, getFillBackgroundColorColor());

        properties.put(CellPropertyType.FONT, getFontIndex());
        properties.put(CellPropertyType.HIDDEN, getHidden());
        properties.put(CellPropertyType.INDENTION, getIndention());
        properties.put(CellPropertyType.LEFT_BORDER_COLOR, getLeftBorderColor());
        properties.put(CellPropertyType.LOCKED, getLocked());
        properties.put(CellPropertyType.RIGHT_BORDER_COLOR, getRightBorderColor());
        //properties.put(CellPropertyType.ROTATION, getRotation());
        properties.put(CellPropertyType.TOP_BORDER_COLOR, getTopBorderColor());
        properties.put(CellPropertyType.WRAP_TEXT, getWrapText());
        //properties.put(CellPropertyType.SHRINK_TO_FIT, getShrinkToFit());
        //properties.put(CellPropertyType.QUOTE_PREFIXED, getQuotePrefixed());
        return properties;
    }

    @Override
    public void invalidateCachedProperties()
    {
        // Properties can't change in this implementation, nothing to invalidate
    }
}
