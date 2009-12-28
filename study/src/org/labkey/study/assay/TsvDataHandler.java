/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.TransformDataHandler;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.study.assay.AbstractAssayTsvDataHandler;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.security.User;
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
    public static final AssayDataType DATA_TYPE = new AssayDataType("AssayRunTSVData", new FileType(Arrays.asList(".tsv", ".xls", ".txt"), ".tsv"));
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

    public Map<DataType, List<Map<String, Object>>> getValidationDataMap(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
        ExpProtocol protocol = data.getRun().getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);

        Domain dataDomain = provider.getResultsDomain(protocol);

        DomainProperty[] columns = dataDomain.getProperties();
        Map<String, DomainProperty> aliases = dataDomain.createImportMap(false);
        Set<String> mvEnabledColumns = new CaseInsensitiveHashSet();
        Set<String> mvIndicatorColumns = new CaseInsensitiveHashSet();

        for (DomainProperty col : columns)
        {
            if (col.isMvEnabled())
            {
                mvEnabledColumns.add(col.getName());
                mvIndicatorColumns.add(col.getName() + MvColumn.MV_INDICATOR_SUFFIX);
            }
        }
        DataLoader<Map<String, Object>> loader = null;
        try
        {

            if (dataFile.getName().toLowerCase().endsWith(".xls"))
            {
                loader = new ExcelLoader(dataFile, true);
            }
            else
            {
                loader = new TabLoader(dataFile, true);
            }
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
                column.errorValues = ERROR_VALUE;
            }
            Map<DataType, List<Map<String, Object>>> datas = new HashMap<DataType, List<Map<String, Object>>>();

            datas.put(DATA_TYPE, loader.load());
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

    public void importTransformDataMap(ExpData data, User user, ExpRun run, ExpProtocol protocol, AssayProvider provider, List<Map<String, Object>> dataMap) throws ExperimentException
    {
        importRows(data, user, run, protocol, provider, dataMap);
    }
}
