/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.study.assay;

import org.apache.log4j.Logger;
import org.labkey.api.collections.Sets;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.DataLoaderSettings;
import org.labkey.api.qc.TransformDataHandler;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.study.assay.AbstractAssayTsvDataHandler;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 11:17:56 AM
 */
public class TsvDataHandler extends AbstractAssayTsvDataHandler implements TransformDataHandler
{
    public static final AssayDataType DATA_TYPE;
    static
    {
        FileType fileType = new FileType(Arrays.asList(".tsv", ".xls", ".xlsx", ".txt", ".fna", ".fasta"), ".tsv");
        fileType.setExtensionsMutuallyExclusive(false);
        DATA_TYPE = new AssayDataType("AssayRunTSVData", fileType);
    }
    
    private boolean _allowEmptyData = false;

    public Priority getPriority(ExpData data)
    {
        Lsid lsid = new Lsid(data.getLSID());
        if (DATA_TYPE.matches(lsid))
        {
            return Priority.HIGH;
        }
        return null;
    }

    protected boolean allowEmptyData()
    {
        return _allowEmptyData;
    }

    public void setAllowEmptyData(boolean allowEmpty)
    {
        _allowEmptyData = allowEmpty;
    }

    @Override
    protected boolean shouldAddInputMaterials()
    {
        return true;
    }

    public Map<DataType, List<Map<String, Object>>> getValidationDataMap(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context, DataLoaderSettings settings) throws ExperimentException
    {
        ExpProtocol protocol = data.getRun().getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);

        Domain dataDomain = provider.getResultsDomain(protocol);

        DomainProperty[] columns = dataDomain.getProperties();
        Map<String, DomainProperty> aliases = dataDomain.createImportMap(false);
        Set<String> mvEnabledColumns = Sets.newCaseInsensitiveHashSet();
        Set<String> mvIndicatorColumns = Sets.newCaseInsensitiveHashSet();

        for (DomainProperty col : columns)
        {
            if (col.isMvEnabled())
            {
                mvEnabledColumns.add(col.getName());
                mvIndicatorColumns.add(col.getName() + MvColumn.MV_INDICATOR_SUFFIX);
            }
        }
        DataLoader loader = null;
        try
        {
            loader = DataLoader.get().createLoader(dataFile, null, true, null);

            loader.setThrowOnErrors(settings.isThrowOnErrors());
            for (ColumnDescriptor column : loader.getColumns())
            {
                if (mvEnabledColumns.contains(column.name))
                {
                    column.setMvEnabled(dataDomain.getContainer());
                }
                else if (mvIndicatorColumns.contains(column.name))
                {
                    column.setMvIndicator(dataDomain.getContainer());
                    column.clazz = String.class;
                }
                DomainProperty prop = aliases.get(column.name);
                if (prop != null)
                    column.clazz = prop.getPropertyDescriptor().getPropertyType().getJavaType();
                else
                {
                    // It's not an expected column. Is it an MV indicator column?
                    if (!mvIndicatorColumns.contains(column.name))
                    {
                        column.load = false;
                    }
                }
                if (settings.isBestEffortConversion())
                    column.errorValues = DataLoader.ERROR_VALUE_USE_ORIGINAL;
                else
                    column.errorValues = ERROR_VALUE;
            }
            Map<DataType, List<Map<String, Object>>> datas = new HashMap<DataType, List<Map<String, Object>>>();
            List<Map<String, Object>> dataRows = loader.load();

            // loader did not parse any rows
            if (dataRows.isEmpty() && !settings.isAllowEmptyData() && columns.length > 0)
                throw new ExperimentException("Unable to load any rows from the input data. Please check the format of the input data to make sure it matches the assay data columns.");
            if (!dataRows.isEmpty())
                adjustFirstRowOrder(dataRows, loader);

            datas.put(DATA_TYPE, dataRows);
            return datas;
        }
        catch (IOException ioe)
        {
            throw new ExperimentException(ioe);
        }
        finally
        {
            if (loader != null)
                loader.close();
        }
    }

    /**
     * Reorders the first row of the list of rows to be in original column order. This is usually enough
     * to cause serializers for tsv formats to respect the original file column order. A bit of a hack but
     * the way row maps are generated make it difficult to preserve order at row map generation time.
     */
    private void adjustFirstRowOrder(List<Map<String, Object>> dataRows, DataLoader loader) throws IOException
    {
        Map<String, Object> firstRow = dataRows.remove(0);
        Map<String, Object> newRow = new LinkedHashMap<String, Object>();

        for (ColumnDescriptor column : loader.getColumns())
        {
            if (firstRow.containsKey(column.name))
                newRow.put(column.name, firstRow.get(column.name));
        }
        dataRows.add(0, newRow);
    }

    public void importTransformDataMap(ExpData data, AssayRunUploadContext context, ExpRun run, List<Map<String, Object>> dataMap) throws ExperimentException
    {
        try
        {
            importRows(data, context.getUser(), run, context.getProtocol(), context.getProvider(), dataMap);
        }
        catch (ValidationException e)
        {
            throw new ExperimentException(e.toString(), e);
        }
    }
}
