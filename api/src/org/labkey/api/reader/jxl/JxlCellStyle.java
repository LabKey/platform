/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Color;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;

/**
 * User: klum
 * Date: May 20, 2011
 * Time: 1:45:20 PM
 */
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
    public short getFontIndex()
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

    @Override
    public short getAlignment()
    {
        if (_format != null)
            return (short)_format.getAlignment().getValue();
        return 0;
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

    @Override
    public short getVerticalAlignment()
    {
        if (_format != null)
            return (short)_format.getVerticalAlignment().getValue();
        return 0;
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
    public short getBorderLeft()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getBorderRight()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getBorderTop()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getBorderBottom()
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
    public short getFillPattern()
    {
        if (_format != null)
            return (short)_format.getPattern().getValue();
        return 0;
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
    public HorizontalAlignment getAlignmentEnum()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setVerticalAlignment(VerticalAlignment verticalAlignment)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public VerticalAlignment getVerticalAlignmentEnum()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setBorderLeft(BorderStyle borderStyle)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public BorderStyle getBorderLeftEnum()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setBorderRight(BorderStyle borderStyle)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public BorderStyle getBorderRightEnum()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setBorderTop(BorderStyle borderStyle)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public BorderStyle getBorderTopEnum()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setBorderBottom(BorderStyle borderStyle)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public BorderStyle getBorderBottomEnum()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setFillPattern(FillPatternType fillPatternType)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public FillPatternType getFillPatternEnum()
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
}
