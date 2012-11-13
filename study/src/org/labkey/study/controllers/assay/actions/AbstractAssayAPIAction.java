/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.study.controllers.assay.actions;

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AbstractTsvAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.data.Table;
import org.json.JSONObject;
import org.json.JSONArray;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Jan 15, 2009
 */
public abstract class AbstractAssayAPIAction<FORM extends SimpleApiJsonForm> extends ApiAction<FORM>
{
    // Top level properties
    public static final String ASSAY_ID = "assayId";
    public static final String BATCH_ID = "batchId";
    protected static final String BATCH = "batch";
    protected static final String RUNS = "runs";

    // Run properties
    protected static final String DATA_ROWS = "dataRows";

    public final ApiResponse execute(FORM form, BindException errors) throws Exception
    {
        if (form.getJsonObject() == null)
        {
            form.bindProperties(new JSONObject());
        }

        Pair<ExpProtocol, AssayProvider> pair = getProtocolProvider(form.getJsonObject(), getViewContext().getContainer());
        ExpProtocol protocol = pair.first;
        AssayProvider provider = pair.second;

        return executeAction(protocol, provider, form, errors);
    }

    public static Pair<ExpProtocol, AssayProvider> getProtocolProvider(JSONObject json, Container c)
    {
        int assayId = json.getInt(ASSAY_ID);
        return getProtocolProvider(assayId, c);
    }

    public static Pair<ExpProtocol, AssayProvider> getProtocolProvider(Integer assayId, Container c)
    {
        if (assayId == null)
        {
            throw new IllegalArgumentException("assayId parameter required");
        }

        ExpProtocol protocol = ExperimentService.get().getExpProtocol(assayId);
        if (protocol == null)
        {
            throw new NotFoundException("Could not find assay id " + assayId);
        }

        List<ExpProtocol> availableAssays = AssayService.get().getAssayProtocols(c);
        if (!availableAssays.contains(protocol))
        {
            throw new NotFoundException("Assay id " + assayId + " is not visible for folder " + c);
        }

        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
        {
            throw new NotFoundException("Could not find assay provider for assay id " + assayId);
        }

        return Pair.of(protocol, provider);
    }

    protected abstract ApiResponse executeAction(ExpProtocol assay, AssayProvider provider, FORM form, BindException errors) throws Exception;

    public static JSONArray serializeDataRows(ExpData data, AssayProvider provider, ExpProtocol protocol, User user, Integer... objectIds) throws SQLException
    {
        Domain dataDomain = provider.getResultsDomain(protocol);
        List<FieldKey> fieldKeys = new ArrayList<FieldKey>();
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
        SimpleFilter filter = new SimpleFilter(AbstractTsvAssayProvider.DATA_ID_COLUMN_NAME, data.getRowId());
        if (objectIds != null && objectIds.length > 0)
        {
            filter.addClause(new SimpleFilter.InClause(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME, Arrays.asList(objectIds)));
        }
        Results results = null;

        try
        {
            results = Table.select(tableInfo, columns.values(), filter, new Sort(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME));

            JSONArray dataRows = new JSONArray();
            while (results.next())
            {
                JSONObject dataRow = new JSONObject();
                for (ColumnInfo columnInfo : columns.values())
                {
                    Object value = columnInfo.getValue(results);
                    dataRow.put(columnInfo.getName(), value);
                }
                dataRows.put(dataRow);
            }
            return dataRows;
        }
        finally
        {
            if (results != null) { try { results.close(); } catch (SQLException e) {} }
        }

    }

    public static JSONObject serializeRun(ExpRun run, AssayProvider provider, ExpProtocol protocol, User user) throws SQLException
    {
        JSONObject jsonObject = ExperimentJSONConverter.serializeRun(run, provider.getRunDomain(protocol));

        JSONArray dataRows;
        ExpData[] datas = run.getOutputDatas(provider.getDataType());
        if (datas.length == 1)
        {
            dataRows = serializeDataRows(datas[0], provider, protocol, user);
        }
        else
        {
            dataRows = new JSONArray();
        }
        jsonObject.put(DATA_ROWS, dataRows);

        return jsonObject;
    }

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

    protected ApiResponse serializeResult(AssayProvider provider, ExpProtocol protocol, ExpExperiment batch, User user) throws SQLException
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

    protected ExpExperiment lookupBatch(int batchId)
    {
        ExpExperiment batch = ExperimentService.get().getExpExperiment(batchId);
        if (batch == null)
        {
            throw new NotFoundException("Could not find assay batch " + batchId);
        }
        if (!batch.getContainer().equals(getViewContext().getContainer()))
        {
            throw new NotFoundException("Could not find assay batch " + batchId + " in folder " + getViewContext().getContainer());
        }
        return batch;
    }
}
