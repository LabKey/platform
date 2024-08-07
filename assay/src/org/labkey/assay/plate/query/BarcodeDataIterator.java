package org.labkey.assay.plate.query;

import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.WrapperDataIterator;

import java.util.Map;

public class BarcodeDataIterator extends WrapperDataIterator
{
    private final Integer _barcodeCol;
    private final Integer _generatedBarcodeCol;
    private final Integer _template;


    public BarcodeDataIterator(DataIterator di, String barcodeColumn, String generatedBarcodeColumn, String templateColumn)
    {
        super(DataIteratorUtil.wrapMap(di, false));

        Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
        _barcodeCol = map.get(barcodeColumn);
        _generatedBarcodeCol = map.get(generatedBarcodeColumn);
        _template = map.get(templateColumn);
    }

    @Override
    public Object get(int i)
    {
        // Return user-supplied barcode value if present, defaulting to generated value
        if (i == _barcodeCol)
        {
            if ((boolean) super.get(_template))
                return null;

            Object curName = super.get(_barcodeCol);
            if (curName != null)
                return curName;
            else
                return super.get(_generatedBarcodeCol);
        }

        return super.get(i);
    }
}
