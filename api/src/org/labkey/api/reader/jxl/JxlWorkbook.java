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

import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.formula.udf.UDFFinder;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.PictureData;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

/**
 * User: klum
 * Date: May 2, 2011
 * Time: 6:49:35 PM
 */
public class JxlWorkbook implements Workbook
{
    private final jxl.Workbook _workbook;

    public JxlWorkbook(File dataFile) throws IOException, InvalidFormatException
    {
        WorkbookSettings settings = new WorkbookSettings();
        settings.setGCDisabled(true);

        try {
            _workbook = jxl.Workbook.getWorkbook(dataFile, settings);
        }
        catch (BiffException e)
        {
            throw new InvalidFormatException(e.getMessage());
        }
    }

    public JxlWorkbook(InputStream data) throws IOException, InvalidFormatException
    {
        WorkbookSettings settings = new WorkbookSettings();
        settings.setGCDisabled(true);

        try
        {
            _workbook = jxl.Workbook.getWorkbook(data, settings);
        }
        catch (BiffException e)
        {
            throw new InvalidFormatException(e.getMessage());
        }
    }

    @Override
    public int getActiveSheetIndex()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setActiveSheet(int sheetIndex)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getFirstVisibleTab()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setFirstVisibleTab(int sheetIndex)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setSheetOrder(String sheetname, int pos)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setSelectedTab(int index)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setSheetName(int sheet, String name)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public String getSheetName(int sheet)
    {
        jxl.Sheet s = _workbook.getSheet(sheet);
        if (s != null)
            return s.getName();

        return null;
    }

    @Override
    public int getSheetIndex(String name)
    {
        int idx = 0;
        for (jxl.Sheet sheet :_workbook.getSheets())
        {
            if (sheet.getName().equals(name))
                return idx;

            idx++;
        }
        return 0;
    }

    @Override
    public int getSheetIndex(Sheet sheet)
    {
        int idx = 0;
        for (jxl.Sheet s :_workbook.getSheets())
        {
            if (s.getName().equals(sheet.getSheetName()))
                return idx;

            idx++;
        }
        return 0;
    }

    @Override
    public Sheet createSheet()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Sheet createSheet(String sheetname)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Sheet cloneSheet(int sheetNum)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getNumberOfSheets()
    {
        return _workbook.getNumberOfSheets();
    }

    @Override
    public Sheet getSheetAt(int index)
    {
        return new JxlSheet(_workbook.getSheet(index), this);
    }

    @Override
    public Sheet getSheet(String name)
    {
        return new JxlSheet(_workbook.getSheet(name), this);
    }

    @Override
    public void removeSheetAt(int index)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Font createFont()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getNumberOfFonts()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Font getFontAt(short idx)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellStyle createCellStyle()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void write(OutputStream stream) throws IOException
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getNumberOfNames()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Name getName(String name)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Name getNameAt(int nameIndex)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Name createName()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getNameIndex(String name)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void removeName(int index)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void removeName(String name)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setPrintArea(int sheetIndex, String reference)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setPrintArea(int sheetIndex, int startColumn, int endColumn, int startRow, int endRow)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public String getPrintArea(int sheetIndex)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void removePrintArea(int sheetIndex)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Row.MissingCellPolicy getMissingCellPolicy()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setMissingCellPolicy(Row.MissingCellPolicy missingCellPolicy)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public DataFormat createDataFormat()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int addPicture(byte[] pictureData, int format)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public List<? extends PictureData> getAllPictures()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CreationHelper getCreationHelper()
    {
        return new JxlCreationHelper();
    }

    @Override
    public boolean isHidden()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setHidden(boolean hiddenFlag)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean isSheetHidden(int idx)
    {
        jxl.Sheet sheet = _workbook.getSheet(idx);

        if (sheet != null)
            return sheet.isHidden();
        return false;
    }

    @Override
    public boolean isSheetVeryHidden(int sheetIx)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setSheetHidden(int sheetIx, boolean hidden)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setSheetHidden(int sheetIx, int hidden)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void addToolPack(UDFFinder udfFinder)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setForceFormulaRecalculation(boolean b)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getForceFormulaRecalculation()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Iterator<Sheet> sheetIterator()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Font findFont(boolean b, short i, short i1, String s, boolean b1, boolean b2, short i2, byte b3)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getNumCellStyles()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellStyle getCellStyleAt(int i)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void close() throws IOException
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public List<? extends Name> getNames(String s)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public List<? extends Name> getAllNames()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void removeName(Name name)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int linkExternalWorkbook(String s, Workbook workbook)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public SpreadsheetVersion getSpreadsheetVersion()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Iterator<Sheet> iterator()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public SheetVisibility getSheetVisibility(int sheetIx)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setSheetVisibility(int sheetIx, SheetVisibility visibility)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int addOlePackage(byte[] oleData, String label, String fileName, String command) throws IOException
    {
        throw new UnsupportedOperationException("method not yet supported");
    }
}
