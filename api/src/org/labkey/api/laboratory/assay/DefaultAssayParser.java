/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.laboratory.assay;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.ExcelFactory;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * User: bimber
 * Date: 9/15/12
 * Time: 10:37 AM
 */
public class DefaultAssayParser implements AssayParser
{
    protected boolean _hasHeaders = true;
    protected ExpProtocol _protocol;
    protected AssayProvider _provider;
    protected Container _container;
    protected User _user;
    protected AssayImportMethod _method;
    private String HAS_RESULT = "__hasResult__";

    protected String RESULT_FIELD = "result";
    protected String RESULT_OOR_FIELD = "resultOORIndicator";

    private static final Logger _log = Logger.getLogger(AssayParser.class);

    public DefaultAssayParser(AssayImportMethod method, Container c, User u, int assayId)
    {
        _method = method;
        _container = c;
        _user = u;
        populateProtocolProvider(assayId);
    }

    protected TabLoader getTabLoader(String contents) throws IOException
    {
        return new TabLoader(contents, _hasHeaders);
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

    public List<Map<String, Object>> parseResultFile(ImportContext context, ExpProtocol protocol) throws BatchValidationException
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        Domain dataDomain = provider.getResultsDomain(protocol);
        Map<String, DomainProperty> importMap = dataDomain.createImportMap(false);
        Map<String, PropertyDescriptor> propertyNameToDescriptor = getPropertyMap(importMap);

        try
        {
            String sb = readRawFile(context);
            context.getErrors().confirmNoErrors();
            TabLoader loader = getTabLoader(sb);
            configureColumns(propertyNameToDescriptor, loader);
            List<Map<String, Object>> rows = loader.load();
            rows = processOORIndicators(rows, propertyNameToDescriptor, context);
            context.getErrors().confirmNoErrors();

            rows = processRowsFromFile(rows, context);
            performDefaultChecks(rows, context);
            return rows;
        }
        catch (IOException e)
        {
            context.getErrors().addError(e.getMessage());
            throw context.getErrors().getErrors();
        }
    }

    private List<Map<String, Object>> processOORIndicators(List<Map<String, Object>> rows, Map<String, PropertyDescriptor> propertyNameToDescriptor, ImportContext context)
    {
        if (!propertyNameToDescriptor.containsKey(RESULT_OOR_FIELD))
            return rows;

        Pattern p = Pattern.compile("^(<|>).*");

        for (Map<String, Object> row : rows)
        {
            Object resultObj = row.get(RESULT_FIELD);
            if (resultObj != null && resultObj instanceof String)
            {
                String resultString = (String)resultObj;
                if (p.matcher(resultString).matches())
                {
                    String oor = resultString.substring(0, 1);
                    resultString = resultString.substring(1);
                    row.put(RESULT_OOR_FIELD, oor);
                }

                try
                {
                    Double result = Double.parseDouble(resultString);
                    row.put(RESULT_FIELD, result);
                }
                catch (NumberFormatException e)
                {
                    context.getErrors().addError("Improper number format: " + resultString);
                }
            }
        }

        return rows;
    }

    protected List<Map<String, Object>> processRowsFromFile(List<Map<String, Object>> rows, ImportContext context) throws BatchValidationException
    {
        ListIterator<Map<String, Object>> rowsIter = rows.listIterator();
        while (rowsIter.hasNext())
        {
            Map<String, Object> row = rowsIter.next();
            appendPromotedResultFields(row, context);
        }

        return rows;
    }

    protected void performDefaultChecks(List<Map<String, Object>> rows, ImportContext context) throws BatchValidationException
    {
        ParserErrors errors = context.getErrors();

        Domain dataDomain = getProvider().getResultsDomain(getProtocol());
        Map<String, DomainProperty> importMap = dataDomain.createImportMap(false);
        Map<String, PropertyDescriptor> propertyNameToDescriptor = getPropertyMap(importMap);

        int idx = 0;
        for (Map<String, Object> row : rows)
        {
            idx++;
            for (String name : propertyNameToDescriptor.keySet())
            {
                if (row.containsKey(name))
                {
                    PropertyDescriptor pd = propertyNameToDescriptor.get(name);
                    if (pd.isRequired() && row.get(name) == null)
                    {
                        errors.addError("Row " + getRowIdx(row, idx) + ": missing required field '" + pd.getLabel() + "'");
                        continue;
                    }

                    try
                    {
                        ConvertHelper.convert(row.get(name), pd.getJavaClass());
                    }
                    catch (ConversionException e)
                    {
                        errors.addError("Row " + getRowIdx(row, idx) + ": unable to convert value: '" + row.get(name).toString() + "' to type " + pd.getJdbcType());
                        continue;
                    }
                }
            }

            String category = "category";
            if (row.containsKey(category) && StringUtils.trimToNull((String)row.get(category)) != null)
            {
                String errorMsg = "Row " + idx + ": unknown sample category: " + row.get(category);
                try
                {
                    DefaultAssayImportMethod.SAMPLE_CATEGORY cat = DefaultAssayImportMethod.SAMPLE_CATEGORY.getEnum((String)row.get(category));
                    if (cat == null)
                    {
                        errors.addError(errorMsg);
                    }
                }
                catch (IllegalArgumentException e)
                {
                    errors.addError(errorMsg);
                }
            }
        }

        errors.confirmNoErrors();
    }

    protected void appendPromotedResultFields(Map<String, Object> row, ImportContext context)
    {
        JSONObject resultData = context.getPromotedResultsFromJson();
        if (resultData != null)
        {
            for (String prop : resultData.keySet())
            {
                if (row.get(prop) == null)
                    row.put(prop, resultData.get(prop));
            }
        }
    }

    /**
     * Reads the raw input file and converts it to a regular TSV file before passing to TabLoader
     * This allows subclasses to transform the input data
     * @return
     */
    protected String readRawFile(ImportContext context) throws BatchValidationException
    {
        try (StringWriter sw = new StringWriter(); CSVWriter out = new CSVWriter(sw, '\t'))
        {
            // replace "n/a" with "0"
            for (List<String> line : getFileLines(context.getFile()))
            {
                List<String> cells = new ArrayList<>();
                for (Object cell : line)
                {
                    if (cell != null && "n\\a".equalsIgnoreCase(cell.toString()))
                    {
                        cell = "0";
                    }

                    cells.add(cell == null ? null : cell.toString());
                }

                if (!StringUtils.isEmpty(StringUtils.join(line)))
                    out.writeNext(line.toArray(new String[line.size()]));
            }

            return sw.toString();
        }
        catch (IOException e)
        {
            context.getErrors().addError(e.getMessage());
            throw context.getErrors().getErrors();
        }
    }

    protected void configureColumns(Map<String, PropertyDescriptor> propertyNameToDescriptor, TabLoader loader) throws IOException
    {
        for (ColumnDescriptor column : loader.getColumns())
        {
            String columnName = column.name.toLowerCase();
            PropertyDescriptor pd = propertyNameToDescriptor.get(columnName);
            if (pd != null)
            {
                column.clazz = pd.getPropertyType().getJavaType();
                if (RESULT_FIELD.equalsIgnoreCase(pd.getName()) && propertyNameToDescriptor.containsKey(RESULT_OOR_FIELD))
                    column.clazz = String.class;

                if (!columnName.equals(pd.getName()))
                    column.name = pd.getName();
            }
        }
    }

    public JSONObject getPreview(JSONObject json, File file, String fileName, ViewContext ctx) throws BatchValidationException
    {
        ImportContext context = new ImportContext(json, file, fileName, ctx);

        JSONObject ret = new JSONObject();

        JSONArray batches = new JSONArray();
        JSONObject batch = new JSONObject();
        batch.put("properties", json.get("Batch"));

        JSONArray runs = new JSONArray();
        JSONObject run = new JSONObject();
        run.put("properties", json.get("Run"));

        List<Map<String, Object>> rows = parseResults(context);
        run.put("results", rows);
        runs.put(run);

        batch.put("runs", runs);

        batches.put(batch);
        ret.put("batches", batches);
        ret.put("importMethod", _method.getName());

        context.getErrors().confirmNoErrors();

        return ret;
    }

    /**
     * This is the primary method where the JSON and raw results file are parsed to produce a list of row maps
     */
    protected List<Map<String, Object>> parseResults(ImportContext context) throws BatchValidationException
    {
        List<Map<String, Object>> rows;
        JSONArray resultRows = context.getResultRowsFromJson();
        if (resultRows != null)
        {
            rows = resultRows.toMapList();
            rows = processRowsFromJson(rows, context);
        }
        else
        {
            rows = parseResultFile(context, _protocol);
        }

        return processRows(rows, context);
    }

    /**
     * Override this method to provide custom processing of each result row provided as JSON
     * @return
     */
    protected List<Map<String, Object>> processRowsFromJson(List<Map<String, Object>> rows, ImportContext context)
    {
        ListIterator<Map<String, Object>> rowsIter = rows.listIterator();
        while (rowsIter.hasNext())
        {
            Map<String, Object> row = rowsIter.next();
            appendPromotedResultFields(row, context);
        }

        return rows;
    }

    /**
     * Override this method to transform rows after the file/json has been processed
     */
    protected List<Map<String, Object>> processRows(List<Map<String, Object>> rows, ImportContext context) throws BatchValidationException
    {
        validateRows(rows, context);
        return rows;
    }

    /**
     * Provides simple validation of rows based on field properties
     */
    protected void validateRows(List<Map<String, Object>> rows, ImportContext context) throws BatchValidationException
    {
        ParserErrors errors = context.getErrors();

        Domain resultDomain = _provider.getResultsDomain(_protocol);
        int idx = 0;
        for (Map<String, Object> row : rows)
        {
            idx++; //1-based counter
            CaseInsensitiveHashMap<Object> map = new CaseInsensitiveHashMap<>(row);
            for (DomainProperty dp : resultDomain.getProperties())
            {
                if (dp.isRequired() && (!map.containsKey(dp.getName()) || map.get(dp.getName()) == null))
                {
                    errors.addError("Row " + getRowIdx(row, idx) + ": Missing required field " + dp.getLabel());
                }
            }
        }

        errors.confirmNoErrors();
    }

    protected void saveTemplate(ViewContext ctx, int templateId, int runId) throws BatchValidationException
    {
        try
        {
            //validate the template exists
            TableInfo ti = DbSchema.get("laboratory").getTable("assay_run_templates");
            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowid"), templateId), null);
            if (ts.getRowCount() == 0)
            {
                throw new BatchValidationException(Collections.singletonList(new ValidationException("Unknown template: " + templateId)), null);
            }

            Map<String, Object> row = ts.getMap();
            row.put("runid", runId);
            row.put("status", "Complete");

            Table.update(ctx.getUser(), ti, row, templateId);
        }
        catch (RuntimeSQLException e)
        {
            throw new BatchValidationException(Collections.singletonList(new ValidationException(e.getSQLException().getMessage())), null);
        }
    }

    public Pair<ExpExperiment, ExpRun> saveBatch(JSONObject json, File file, String fileName, ViewContext ctx) throws BatchValidationException
    {
        ImportContext context = new ImportContext(json, file, fileName, ctx);

        try
        {
            return LaboratoryService.get().saveAssayBatch(parseResults(context), json, file, fileName, ctx, _provider, _protocol);
        }
        catch (ValidationException e)
        {
            context.getErrors().addError(e.getMessage());
            throw context.getErrors().getErrors();
        }
    }

    protected boolean mergeTemplateRow(String keyProperty, Map<String, Map<String, Object>> templateRows, Map<String, Object> map, ImportContext context)
    {
        return mergeTemplateRow(keyProperty, templateRows, map, context, false);
    }

    protected boolean mergeTemplateRow(String keyProperty, Map<String, Map<String, Object>> templateRows, Map<String, Object> map, ImportContext context, boolean ignoreMissing)
    {
        String key = (String)map.get(keyProperty);
        Map<String, Object> templateRow = templateRows.get(key);
        if (templateRow != null)
        {
            templateRow.put(HAS_RESULT, true);

            for (String prop : templateRow.keySet())
            {
                if (!"rowid".equalsIgnoreCase(prop))
                {
                    Object value = templateRow.get(prop);
                    if (value != null && !StringUtils.isEmpty(value.toString()))
                    {
                        if (map.get(prop) != null && !map.get(prop).equals(templateRow.get(prop)))
                            _log.info("Property exists for " + prop + ", " + map.get(prop) + ", " + templateRow.get(prop));
                        else
                            map.put(prop, templateRow.get(prop));
                    }
                }
            }
            return true;
        }
        else
        {
            if (!ignoreMissing)
                context.getErrors().addError("Unable to find sample information to match well: " + key);

            return false;
        }
    }

    protected void ensureTemplateRowsHaveResults(Map<String, Map<String, Object>> templateRows, ImportContext context) throws BatchValidationException
    {
        for (String key : templateRows.keySet())
        {
            Map<String, Object> row = templateRows.get(key);
            if (!row.containsKey(HAS_RESULT))
                context.getErrors().addError("Template row with key " + key + " does not have a result", Level.WARN);
        }

        context.getErrors().confirmNoErrors();
    }


    protected void populateProtocolProvider(int assayId)
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(assayId);
        if (protocol == null)
        {
            throw new NotFoundException("Could not find assay id " + assayId);
        }

        List<ExpProtocol> availableAssays = AssayService.get().getAssayProtocols(_container);
        if (!availableAssays.contains(protocol))
        {
            throw new NotFoundException("Assay id " + assayId + " is not visible for folder " + _container);
        }

        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
        {
            throw new NotFoundException("Could not find assay provider for assay id " + assayId);
        }

        _provider = provider;
        _protocol = protocol;
    }

    protected Map<String, Map<String, Object>> getTemplateRowMap(ImportContext context, String keyProperty){
        Integer templateId = context.getTemplateIdFromJson();
        Map<String, Map<String, Object>> ret = new HashMap<>();

        if (templateId == null)
            return ret;

        TableInfo ti = DbSchema.get("laboratory").getTable("assay_run_templates");

        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowid"), templateId), null);
        Map<String, Object>[] maps = ts.getMapArray();
        if (maps.length == 0)
        {
            return ret;
        }

        Map<String, Object> map = maps[0];
        JSONObject templateJson = new JSONObject((String)map.get("json"));
        JSONArray rows = templateJson.getJSONArray("ResultRows");
        for (JSONObject row : rows.toJSONObjectArray())
        {
            String key = row.getString(keyProperty);
            ret.put(key, row);
        }

        return ret;
    }

    public ExpProtocol getProtocol()
    {
        return _protocol;
    }

    public AssayProvider getProvider()
    {
        return _provider;
    }

    protected Integer getRowIdx(Map<String, Object> map, int rowIdx)
    {
        if (map.containsKey("originalRowIdx"))
            return (Integer)map.get("originalRowIdx");
        else
            return rowIdx;
    }

    /**
     * Parses either an excel or text file
     */
    public List<List<String>> getFileLines(File file) throws IOException
    {
        try
        {
            JSONArray arr = ExcelFactory.convertExcelToJSON(file, true);
            List<List<String>> ret = new ArrayList<>();
            if (arr.length() == 0)
                return ret;

            JSONObject sheet = arr.getJSONObject(0);
            for (Object cells : sheet.getJSONArray("data").toArray())
            {
                List<String> line = new ArrayList<>();
                for (JSONObject o : ((JSONArray) cells).toJSONObjectArray())
                {
                    Object val = o.containsKey("formattedValue") ? o.getString("formattedValue") : o.get("value");
                    line.add(ConvertHelper.convert(val, String.class));
                }
                ret.add(line);
            }

            return ret;
        }
        catch (InvalidFormatException e)
        {
            //non-excel file, ignore
        }

        return parseTextFile(file);
    }

    protected List<List<String>> parseTextFile(File file) throws IOException
    {
        List<List<String>> ret = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(file), inferDelimiter(file)))
        {
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                ret.add(Arrays.<String>asList(line));
            }
        }

        return ret;
    }

    protected char inferDelimiter(File f) throws IOException
    {
        int tabCount = 0;
        int commaCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(f)))
        {
            String line;
            int lineCount = 0;
            while (null != (line = reader.readLine()))
            {
                if (StringUtils.isEmpty(line))
                    continue;

                commaCount += line.split(",").length - 1;
                tabCount += line.split("\t").length - 1;

                lineCount++;
                if (lineCount > 20)
                    break;
            }
        }

        return commaCount > tabCount ? ',' : '\t';
    }
}
