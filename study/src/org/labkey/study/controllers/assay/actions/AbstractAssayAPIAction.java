/*
 * Copyright (c) 2009 LabKey Corporation
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
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.data.Table;
import org.labkey.api.data.SQLFragment;
import org.json.JSONObject;
import org.json.JSONArray;
import org.springframework.validation.BindException;

import java.util.List;
import java.util.Map;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Jan 15, 2009
 */
public abstract class AbstractAssayAPIAction<FORM> extends ApiAction<FORM>
{
    // Top level properties
    protected static final String ASSAY_ID = "assayId";
    protected static final String BATCH_ID = "batchId";
    protected static final String BATCH = "batch";
    protected static final String RUNS = "runs";

    // Run properties
    protected static final String DATA_ROWS = "dataRows";

    public final ApiResponse execute(SimpleApiJsonForm form, BindException errors) throws Exception
    {
        if (form.getJsonObject() == null)
        {
            form.setJsonObject(new JSONObject());
        }
        int assayId = form.getJsonObject().getInt(ASSAY_ID);

        ExpProtocol protocol = ExperimentService.get().getExpProtocol(assayId);
        if (protocol == null)
        {
            throw new NotFoundException("Could not find assay id " + assayId);
        }

        List<ExpProtocol> availableAssays = AssayService.get().getAssayProtocols(getViewContext().getContainer());
        if (!availableAssays.contains(protocol))
        {
            throw new NotFoundException("Assay id " + assayId + " is not visible for folder " + getViewContext().getContainer());
        }

        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
        {
            throw new NotFoundException("Could not find assay provider for assay id " + assayId);
        }

        return executeAction(protocol, provider, form, errors);
    }

    protected abstract ApiResponse executeAction(ExpProtocol assay, AssayProvider provider, SimpleApiJsonForm form, BindException errors) throws Exception;

    protected JSONObject serializeRun(ExpRun run, AssayProvider provider, ExpProtocol protocol) throws SQLException
    {
        JSONObject jsonObject = ExperimentJSONConverter.serializeRun(run, provider.getRunDomain(protocol));

        JSONArray dataRows = new JSONArray();

        ExpData[] datas = run.getOutputDatas(provider.getDataType());
        if (datas.length == 1)
        {
            SQLFragment sql = new SQLFragment("SELECT child.ObjectURI FROM " + OntologyManager.getTinfoObject() + " child, ");
            sql.append(OntologyManager.getTinfoObject() + " parent WHERE parent.ObjectId = child.OwnerObjectId AND ");
            sql.append("parent.ObjectURI = ? ORDER BY child.ObjectId");
            sql.add(datas[0].getLSID());
            String[] objectURIs = Table.executeArray(OntologyManager.getExpSchema(), sql, String.class);

            Domain dataDomain = provider.getRunDataDomain(protocol);

            for (String objectURI : objectURIs)
            {
                JSONObject dataRow = new JSONObject();
                Map<String, Object> values = OntologyManager.getProperties(run.getContainer(), objectURI);
                for (DomainProperty prop : dataDomain.getProperties())
                {
                    dataRow.put(prop.getName(), values.get(prop.getPropertyURI()));
                }
                dataRows.put(dataRow);
            }
        }
        jsonObject.put(DATA_ROWS, dataRows);

        return jsonObject;
    }

    protected JSONObject serializeBatch(ExpExperiment batch, AssayProvider provider, ExpProtocol protocol) throws SQLException
    {
        JSONObject jsonObject = ExperimentJSONConverter.serializeRunGroup(batch, provider.getBatchDomain(protocol));

        JSONArray runsArray = new JSONArray();
        for (ExpRun run : batch.getRuns())
        {
            runsArray.put(serializeRun(run, provider, protocol));
        }
        jsonObject.put(RUNS, runsArray);

        return jsonObject;
    }

    protected ApiResponse serializeResult(AssayProvider provider, ExpProtocol protocol, ExpExperiment batch) throws SQLException
    {
        JSONObject result = new JSONObject();
        result.put(ASSAY_ID, protocol.getRowId());

        JSONObject batchObject;

        if (batch != null)
        {
            batchObject = serializeBatch(batch, provider, protocol);
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