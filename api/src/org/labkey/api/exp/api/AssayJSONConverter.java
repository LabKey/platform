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
package org.labkey.api.exp.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AbstractTsvAssayProvider;
import org.labkey.api.study.assay.AssayProvider;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Serializes and deserializes JSON for assay-related APIs
 * User: jeckels
 * Date: Jan 21, 2009
 */
public class AssayJSONConverter
{
    // Top level properties
    public static final String ASSAY_ID = "assayId";
    public static final String ASSAY_NAME = "assayName";
    public static final String PROVIDER_NAME = "providerName";
    public static final String BATCH_ID = "batchId";
    public static final String BATCH_IDS = "batchIds";
    public static final String BATCH = "batch";
    public static final String BATCHES = "batches";
    public static final String RUNS = "runs";

    // Run properties
    public static final String DATA_ROWS = "dataRows";

    public static JSONObject serializeBatch(ExpExperiment batch, AssayProvider provider, ExpProtocol protocol, User user) throws SQLException
    {
        JSONObject jsonObject = ExperimentJSONConverter.serializeRunGroup(batch, provider.getBatchDomain(protocol));

        JSONArray runsArray = new JSONArray();
        for (ExpRun run : batch.getRuns())
        {
            runsArray.put(serializeRun(run, provider, protocol, user));
        }
        jsonObject.put(RUNS, runsArray);

        return jsonObject;
    }

    public static JSONArray serializeDataRows(ExpData data, AssayProvider provider, ExpProtocol protocol, User user, Integer... objectIds) throws SQLException
    {
        Domain dataDomain = provider.getResultsDomain(protocol);
        List<FieldKey> fieldKeys = new ArrayList<>();
        for (DomainProperty property : dataDomain.getProperties())
        {
            fieldKeys.add(FieldKey.fromParts(property.getName()));
        }

        if (fieldKeys.isEmpty())
        {
            return new JSONArray();
        }

        TableInfo tableInfo = provider.createProtocolSchema(user, data.getContainer(), protocol, null).createDataTable();
        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(tableInfo, fieldKeys);
        assert columns.size() == fieldKeys.size() : "Missing a column for at least one of the properties";
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(AbstractTsvAssayProvider.DATA_ID_COLUMN_NAME), data.getRowId());
        if (objectIds != null && objectIds.length > 0)
        {
            filter.addClause(new SimpleFilter.InClause(FieldKey.fromParts(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME), Arrays.asList(objectIds)));
        }

        JSONArray dataRows = new JSONArray();

        new TableSelector(tableInfo, columns.values(), filter, new Sort(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME)).forEachResults(results -> {
            JSONObject dataRow = new JSONObject();
            for (ColumnInfo columnInfo : columns.values())
            {
                Object value = columnInfo.getValue(results);
                dataRow.put(columnInfo.getName(), value);
            }
            dataRows.put(dataRow);
        });

        return dataRows;
    }

    public static JSONObject serializeRun(ExpRun run, AssayProvider provider, ExpProtocol protocol, User user) throws SQLException
    {
        JSONObject jsonObject = ExperimentJSONConverter.serializeRun(run, provider.getRunDomain(protocol));

        JSONArray dataRows = new JSONArray();
        List<? extends ExpData> datas = run.getOutputDatas(provider.getDataType());

        if (datas.size() == 1)
        {
            dataRows = serializeDataRows(datas.get(0), provider, protocol, user);
        }
        else if (datas.size() > 1)
        {
            // more than one output datas, check for a transformed data object
            List<? extends ExpData> transformedDatas = run.getInputDatas(ExpDataRunInput.IMPORTED_DATA_ROLE,  ExpProtocol.ApplicationType.ExperimentRunOutput);
            if (transformedDatas.size() == 1)
            {
                dataRows = serializeDataRows(transformedDatas.get(0), provider, protocol, user);
            }
        }
        jsonObject.put(DATA_ROWS, dataRows);

        return jsonObject;
    }

    public static ApiResponse serializeResult(AssayProvider provider, ExpProtocol protocol, ExpExperiment batch, User user) throws SQLException
    {
        JSONObject result = new JSONObject();
        result.put(ASSAY_ID, protocol.getRowId());

        JSONObject batchObject;

        if (batch != null)
        {
            batchObject = serializeBatch(batch, provider, protocol, user);
        }
        else
        {
            batchObject = new JSONObject();
        }

        result.put(BATCH, batchObject);
        return new ApiSimpleResponse(result);
    }

    public static ApiResponse serializeResult(AssayProvider provider, ExpProtocol protocol, List<? extends ExpExperiment> batches, User user) throws SQLException
    {
        JSONObject result = new JSONObject();
        result.put(ASSAY_ID, protocol.getRowId());

        JSONArray batchesArray = new JSONArray();

        for (ExpExperiment batch : batches)
        {
            batchesArray.put(serializeBatch(batch, provider, protocol, user));
        }

        result.put(BATCHES, batchesArray);
        return new ApiSimpleResponse(result);
    }
}
