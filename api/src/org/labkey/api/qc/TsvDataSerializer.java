/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

package org.labkey.api.qc;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.assay.AbstractAssayTsvDataHandler;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.util.DateUtil;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Nov 17, 2009
 */
public class TsvDataSerializer implements DataExchangeHandler.DataSerializer
{
    @Override
    public void exportRunData(ExpProtocol protocol, List<DataIteratorBuilder> data, File runDataFile) throws IOException, BatchValidationException
    {
        List<String> columns = null;
        try (PrintWriter pw = PrintWriters.getPrintWriter(runDataFile))
        {
            for (DataIteratorBuilder dib : data)
            {
                columns = exportData(dib, columns, pw);
            }
        }

        if (columns == null)
        {
            // No data was written so delete the empty file
            runDataFile.delete();
        }
    }

    private List<String> exportData(DataIteratorBuilder data, List<String> columns, PrintWriter pw) throws IOException, BatchValidationException
    {
        try (MapDataIterator iter = DataIteratorUtil.wrapMap(data.getDataIterator(new DataIteratorContext()), true))
        {
            if (iter.next())
            {
                Map<String, Object> row = iter.getMap();
                if (columns == null)
                {
                    // write the column header
                    columns = new ArrayList<>(row.keySet());
                    String sep = "";
                    for (String name : columns)
                    {
                        pw.append(sep);
                        pw.append(name);
                        sep = "\t";
                    }
                    pw.println();
                    writeRow(row, columns, pw);
                }

                // write the remaining rows
                while (iter.next())
                {
                    row = iter.getMap();
                    writeRow(row, columns, pw);
                }
            }
        }
        return columns;
    }

    private static void writeRow(Map<String, Object> row, List<String> columns, PrintWriter pw)
    {
        String sep;
        sep = "";
        for (String name : columns)
        {
            Object o = row.get(name);
            pw.append(sep);
            if (o != null)
            {
                if (Date.class.isAssignableFrom(o.getClass()))
                    pw.append(DateUtil.formatIsoDateShortTime((Date) o));  // Always ISO? Or should we apply display format?
                else if (MvFieldWrapper.class.isAssignableFrom(o.getClass()))
                    pw.append(String.valueOf(((MvFieldWrapper) o).getOriginalValue()));
                else if (Collection.class.isAssignableFrom(o.getClass()))
                    pw.append(StringUtils.join((Collection) o, ","));
                else if (Object[].class.isAssignableFrom(o.getClass()))
                    pw.append(StringUtils.join((Object[]) o, ","));
                else
                    pw.append(String.valueOf(o));
            }
            sep = "\t";
        }
        pw.println();
    }

    @Override
    public DataIteratorBuilder importRunData(ExpProtocol protocol, File runData) throws Exception
    {
        return _importRunData(protocol, runData, true);
    }

    protected DataIteratorBuilder _importRunData(ExpProtocol protocol, File runData, boolean shouldInferTypes)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        Domain dataDomain = provider.getResultsDomain(protocol);
        DataLoaderSettings loaderSettings = new DataLoaderSettings();
        loaderSettings.setAllowUnexpectedColumns(true);

        return context -> {
            try (DataLoader loader = AbstractAssayTsvDataHandler.createLoaderForImport(runData, null, dataDomain, loaderSettings, shouldInferTypes))
            {
                return loader.getDataIterator(new DataIteratorContext());
            }
        };

    }
}
