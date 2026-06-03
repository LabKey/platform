/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
        // If plate is not a template, return user-supplied barcode value if present, defaulting to generated value
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
