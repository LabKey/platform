/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.cbcassay;

import org.labkey.api.exp.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.qc.DataLoaderSettings;
import org.labkey.api.reader.Readers;
import org.labkey.api.study.assay.AbstractAssayTsvDataHandler;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.util.FileType;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * User: kevink
 * Date: Nov 12, 2008 4:13:12 PM
 */
public class CBCDataHandler extends AbstractAssayTsvDataHandler
{
    public static final String NAMESPACE = "CBCAssayData";
    private static final AssayDataType DATA_TYPE = new AssayDataType(NAMESPACE, new FileType(".dat"));

    @Override
    public DataType getDataType()
    {
        return DATA_TYPE;
    }

    @Override
    protected boolean allowEmptyData()
    {
        return false;
    }

    @Override
    protected boolean shouldAddInputMaterials()
    {
        return false;
    }

    protected Map<String, PropertyDescriptor> getPropertyMap(Map<String, DomainProperty> importMap)
    {
        Map<String, PropertyDescriptor> map = new CaseInsensitiveHashMap<>(importMap.size());
        Set<PropertyDescriptor> seen = new HashSet<>(importMap.size());
        for (Map.Entry<String, DomainProperty> entry : importMap.entrySet())
        {
            PropertyDescriptor pd = entry.getValue().getPropertyDescriptor();
            if (!seen.contains(pd))
            {
                String description = pd.getDescription();
                if (description != null && description.length() > 0)
                    map.put(description.toLowerCase(), pd);
                seen.add(pd);
            }
            map.put(entry.getKey(), pd);
        }
        return map;
    }

    private static Date removeTime(Date date)
    {
        Calendar cal = Calendar.getInstance();
        cal.setLenient(false);
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public Map<DataType, List<Map<String, Object>>> getValidationDataMap(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context, DataLoaderSettings settings) throws ExperimentException
    {
        ExpProtocol protocol = data.getRun().getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);

        Domain dataDomain = provider.getResultsDomain(protocol);

        Map<String, DomainProperty> importMap = dataDomain.createImportMap(false);
        Map<String, PropertyDescriptor> propertyNameToDescriptor = getPropertyMap(importMap);
        List<Map<String, Object>> rows = loadTsv(propertyNameToDescriptor, dataFile);

        // compute percent and absolute TotalLymph
        ListIterator<Map<String, Object>> rowsIter = rows.listIterator();
        while (rowsIter.hasNext())
        {
            Map<String, Object> row = rowsIter.next();
            Map<String, Object> map = new CaseInsensitiveHashMap<>(row);
            Double percentLymph = (Double) map.get("PercentLYMPH");
            Double percentLuc = (Double) map.get("PercentLUC");
            if (percentLymph != null && percentLuc != null)
            {
                double value = percentLymph.doubleValue() + percentLuc.doubleValue();
                Double percentTotalLymph = Double.valueOf(Math.rint(100.0d * value) / 100.0d);
                map.put("PercentTotalLYMPH", percentTotalLymph);
            }

            Double absLymph = (Double) map.get("AbsLYMPH");
            Double absLuc = (Double) map.get("AbsLUC");
            if (absLymph != null && absLuc != null)
            {
                double value = absLymph.doubleValue() + absLuc.doubleValue();
                Double absTotalLymph = Double.valueOf(Math.rint(100.0d * value) / 100.0d);
                map.put("AbsTotalLYMPH", absTotalLymph);
            }

            // Study prefers timepoints in days, so remove time portion
            Date date = (Date) map.get("Date");
            if (date != null)
            {
                map.put("Date", removeTime(date));
            }
            
            rowsIter.set(map);
        }

        Map<DataType, List<Map<String, Object>>> datas = new HashMap<>();
        datas.put(getDataType(), rows);
        return datas;
    }

    protected List<Map<String, Object>> loadTsv(Map<String, PropertyDescriptor> propertyNameToDescriptor, File inputFile) throws ExperimentException
    {
        try (BufferedReader reader = Readers.getReader(inputFile))
        {
            StringBuilder sb = new StringBuilder((int)(inputFile.length()));

            // replace "n/a" with "0"
            String line;
            Pattern p = Pattern.compile("\\bn/a\\b");
            while (null != (line = reader.readLine()))
                sb.append(p.matcher(line).replaceAll("0")).append("\n");

            TabLoader loader = new TabLoader(sb, true);
            for (ColumnDescriptor column : loader.getColumns())
            {
                String columnName = column.name.toLowerCase();
                PropertyDescriptor pd = propertyNameToDescriptor.get(columnName);
                if (pd == null)
                {
                    // most column names are formatted as "name(units)"
                    int paren = columnName.indexOf('(');
                    if (paren > 0)
                        pd = propertyNameToDescriptor.get(columnName.substring(0, paren));
                }
                if (pd != null)
                {
                    column.clazz = pd.getPropertyType().getJavaType();
                    if (!columnName.equals(pd.getName()))
                        column.name = pd.getName();
                }
                else
                {
                    column.load = false;
                }
                column.errorValues = ERROR_VALUE;
            }
            return loader.load();
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }
}
