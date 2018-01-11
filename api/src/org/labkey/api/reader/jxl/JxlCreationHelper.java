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

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.ExtendedColor;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;

/**
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
    public FormulaEvaluator createFormulaEvaluator()
    {
        return new JxlFormulaEvaluator();
    }

    @Override
    public ClientAnchor createClientAnchor()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Hyperlink createHyperlink(HyperlinkType hyperlinkType)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public ExtendedColor createExtendedColor()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public AreaReference createAreaReference(String reference)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public AreaReference createAreaReference(CellReference topLeft, CellReference bottomRight)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }
}
