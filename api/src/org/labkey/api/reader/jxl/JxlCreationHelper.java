package org.labkey.api.reader.jxl;

import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.RichTextString;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Nov 30, 2011
 * Time: 1:01:56 PM
 */
public class JxlCreationHelper implements CreationHelper
{
    @Override
    public RichTextString createRichTextString(String text)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public DataFormat createDataFormat()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Hyperlink createHyperlink(int type)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public FormulaEvaluator createFormulaEvaluator()
    {
        return new JxlFormulaEvaluator();
    }

    @Override
    public ClientAnchor createClientAnchor()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }
}
