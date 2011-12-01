package org.labkey.api.reader.jxl;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Nov 30, 2011
 * Time: 1:03:47 PM
 */
public class JxlFormulaEvaluator implements FormulaEvaluator
{
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
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Cell evaluateInCell(Cell cell)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }
}
