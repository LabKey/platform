/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.reader.jxl;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;

import java.util.Map;

/**
 * User: klum
 * Date: Nov 30, 2011
 * Time: 1:03:47 PM
 */
public class JxlFormulaEvaluator implements FormulaEvaluator
{
    @Override
    public void setDebugEvaluationOutputForNextEval(boolean b)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void clearAllCachedResultValues()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void notifySetFormula(Cell cell)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void notifyDeleteCell(Cell cell)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void notifyUpdateCell(Cell cell)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void evaluateAll()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellValue evaluate(Cell cell)
    {
        if (cell instanceof JxlCell)
            return new CellValue(((JxlCell) cell).getRawCell().getContents());
        else
            throw new IllegalArgumentException("The specified cell is not a JXL cell and cannot be evaluated");
    }

    @Override
    public int evaluateFormulaCell(Cell cell)
    {
        // Assume that the formula result is unchanged, works fine for unmodified data
        return cell.getCachedFormulaResultType();
    }

    @Override
    public Cell evaluateInCell(Cell cell)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellType evaluateFormulaCellEnum(Cell cell)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setupReferencedWorkbooks(Map<String, FormulaEvaluator> map)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setIgnoreMissingWorkbooks(boolean b)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }
}
