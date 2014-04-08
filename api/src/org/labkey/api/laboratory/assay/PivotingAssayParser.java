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
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 12/9/12
 * Time: 7:51 AM
 */
public class PivotingAssayParser extends DefaultAssayParser
{
    PivotingImportMethod _pivotMethod;

    public PivotingAssayParser(PivotingImportMethod method, Container c, User u, int assayId)
    {
        super(method, c, u, assayId);
        _pivotMethod = method;
    }

    @Override
    protected String readRawFile(ImportContext context) throws BatchValidationException
    {
        try (StringWriter sw = new StringWriter(); CSVWriter out = new CSVWriter(sw, '\t'))
        {
            DomainProperty valueCol = _pivotMethod.getValueColumn(_protocol);
            DomainProperty pivotCol = _pivotMethod.getPivotColumn(_protocol);
            Map<Integer, String> resultCols = null;
            Integer rowIdx = 0;

            for (List<String> line : getFileLines(context.getFile()))
            {
                if (rowIdx == 0)
                {
                    resultCols = inspectHeader(line, context);
                }

                List<String> rowBase = new ArrayList<>();
                List<Pair<String, String>> otherFields = new ArrayList<>();
                int cellIdx = 0;
                for (String cell : line)
                {
                    if (resultCols.keySet().contains(cellIdx))
                    {
                        if (!StringUtils.isEmpty(cell))
                            otherFields.add(Pair.of(resultCols.get(cellIdx), cell));
                    }
                    else
                    {
                        rowBase.add(cell);
                    }
                    cellIdx++;
                }

                if (rowIdx > 0)
                {
                    for (Pair<String, String> pair : otherFields)
                    {
                        List<String> row = new ArrayList<>();
                        row.addAll(rowBase);
                        row.add(pair.first);
                        row.add(pair.second);
                        row.add(rowIdx.toString());
                        out.writeNext(row.toArray(new String[row.size()]));
                    }
                }
                else
                {
                    List<String> row = new ArrayList<>();
                    row.addAll(rowBase);
                    row.add(pivotCol.getLabel());
                    row.add(valueCol.getLabel());
                    row.add("_rowIdx");
                    out.writeNext(row.toArray(new String[row.size()]));
                }

                rowIdx++;
            }

            return sw.toString();
        }
        catch (IOException e)
        {
            context.getErrors().addError(e.getMessage());
            throw context.getErrors().getErrors();
        }
    }

    /**
     * Inspects the header line and returns a list of all columns inferred to contain results
     * and other columns are assumed to
     */
    private Map<Integer, String> inspectHeader(List<String> header, ImportContext context) throws BatchValidationException
    {
        Map<Integer, String> resultMap = new HashMap<>();
        Map<String, String> allowable = new CaseInsensitiveHashMap<String>();
        BatchValidationException errors = new BatchValidationException();

        for (String val : _pivotMethod.getAllowableValues())
        {
            allowable.put(val, val);
        }

        Set<String> knownColumns = new HashSet<>();
        for (DomainProperty dp : _provider.getResultsDomain(_protocol).getProperties())
        {
            knownColumns.add(dp.getLabel());
            knownColumns.add(dp.getName());
        }

        Integer idx = 0;
        for (String col : header)
        {
            String normalized = null;
            if (allowable.containsKey(col))
            {
                normalized = allowable.get(col);
            }
            else
            {
                if (!knownColumns.contains(col))
                {
                    normalized = handleUnknownColumn(col, allowable, context);
                }
            }

            if (normalized != null)
                resultMap.put(idx, normalized);

            idx++;
        }

        if (errors.hasErrors())
            throw errors;

        return resultMap;
    }

    protected String handleUnknownColumn(String col, Map<String, String> allowable, ImportContext context)
    {
        //TODO: allow a flag that lets us assume known columns hold results
        context.getErrors().addError("Unknown column: " + col);
        return null;
    }
}
