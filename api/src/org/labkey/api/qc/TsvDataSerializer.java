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
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.iterator.ValidatingDataRowIterator;
import org.labkey.api.query.ValidationException;
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
import java.util.function.Supplier;

/**
 * User: klum
 * Date: Nov 17, 2009
 */
public class TsvDataSerializer implements DataExchangeHandler.DataSerializer
{
    @Override
    public void exportRunData(ExpProtocol protocol, Supplier<ValidatingDataRowIterator> data, File runDataFile) throws IOException, ValidationException
    {
        try (ValidatingDataRowIterator iter = data.get())
        {
            // Only write a file if there are data rows
            if (iter.hasNext())
            {
                try (PrintWriter pw = PrintWriters.getPrintWriter(runDataFile))
                {
                    Map<String, Object> row = iter.next();
                    // write the column header
                    List<String> columns = new ArrayList<>(row.keySet());
                    String sep = "";
                    for (String name : columns)
                    {
                        pw.append(sep);
                        pw.append(name);
                        sep = "\t";
                    }
                    pw.println();
                    writeRow(row, columns, pw);

                    // write the remaining rows
                    while (iter.hasNext())
                    {
                        row = iter.next();
                        writeRow(row, columns, pw);
                    }
                }
            }
        }
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
    public Supplier<ValidatingDataRowIterator> importRunData(ExpProtocol protocol, File runData) throws Exception
    {
        return _importRunData(protocol, runData, true);
    }

    protected Supplier<ValidatingDataRowIterator> _importRunData(ExpProtocol protocol, File runData, boolean shouldInferTypes) throws Exception
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        Domain dataDomain = provider.getResultsDomain(protocol);
        DataLoaderSettings loaderSettings = new DataLoaderSettings();
        loaderSettings.setAllowUnexpectedColumns(true);

        try (DataLoader loader = AbstractAssayTsvDataHandler.createLoaderForImport(runData, dataDomain, loaderSettings, shouldInferTypes))
        {
            // TODO - streaming iterator
            List<Map<String, Object>> rows = loader.load();
            return () -> ValidatingDataRowIterator.of(rows);
        }
    }
}
