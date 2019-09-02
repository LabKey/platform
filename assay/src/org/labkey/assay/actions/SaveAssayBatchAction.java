/*
 * Copyright (c) 2009-2018 LabKey Corporation
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
package org.labkey.assay.actions;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.ParseException;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.DefaultExperimentSaveHandler;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentSaveHandler;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.assay.AssayProvider;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.PostCommand;
import org.labkey.remoteapi.sas.SASConnection;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Jan 14, 2009
 */
@RequiresPermission(InsertPermission.class)
@ApiVersion(9.1)
public class SaveAssayBatchAction extends BaseProtocolAPIAction<SimpleApiJsonForm>
{
    public ApiResponse executeAction(ExpProtocol protocol, SimpleApiJsonForm form, BindException errors) throws Exception
    {
        JSONObject rootJsonObject = form.getJsonObject();

        // A user can send in either an array of batches or just a batch but not both.  If a user sends in an array of batches
        // then it must have at least one batch
        JSONObject batchJsonObject = null;
        JSONArray batchesJsonArray = null;

        if (rootJsonObject.has(AssayJSONConverter.BATCH))
            batchJsonObject = rootJsonObject.getJSONObject(AssayJSONConverter.BATCH);

        if (rootJsonObject.has(AssayJSONConverter.BATCHES))
            batchesJsonArray = rootJsonObject.getJSONArray(AssayJSONConverter.BATCHES);

        verifyFormJsonObject(batchJsonObject, batchesJsonArray);

        AssayProvider provider = getAssayProvider();
        ExperimentSaveHandler saveHandler;

        if (provider != null)
        {
            saveHandler = provider.getSaveHandler();
            if (null == saveHandler)
                throw new IllegalArgumentException("SaveAssayBatch is not supported for assay provider: " + provider);
        }
        else
            saveHandler = new DefaultExperimentSaveHandler();

        if (null != batchJsonObject)
            return executeAction(saveHandler, protocol, provider, rootJsonObject, batchJsonObject);

        return executeAction(saveHandler, protocol, provider, rootJsonObject, batchesJsonArray);
    }

    private ApiResponse executeAction(ExperimentSaveHandler saveHandler, ExpProtocol protocol, AssayProvider provider,
                                      JSONObject rootJsonObject, JSONArray batchesJsonArray) throws Exception
    {
        saveHandler.beforeSave(getViewContext(), rootJsonObject, protocol);
        List<ExpExperiment> batches = new ArrayList<>(batchesJsonArray.length());
        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            for (int i = 0; i < batchesJsonArray.length(); i++)
            {
                JSONObject batchJsonObject = batchesJsonArray.getJSONObject(i);
                batches.add(saveHandler.handleBatch(getViewContext(), batchJsonObject, protocol));
            }

            transaction.commit();
        }
        saveHandler.afterSave(getViewContext(), batches, protocol);
        return AssayJSONConverter.serializeResult(provider, protocol, batches, getUser());

    }

    private ApiResponse executeAction(ExperimentSaveHandler saveHandler, ExpProtocol protocol, AssayProvider provider,
                                      JSONObject rootJsonObject, JSONObject batchJsonObject) throws Exception
    {
        List<ExpExperiment> batches = new ArrayList<>(1);

        saveHandler.beforeSave(getViewContext(), rootJsonObject, protocol);
        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            batches.add(saveHandler.handleBatch(getViewContext(), batchJsonObject, protocol));
            transaction.commit();
        }
        saveHandler.afterSave(getViewContext(), batches, protocol);
        return AssayJSONConverter.serializeResult(provider, protocol, batches.get(0), getUser());
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testSaveBatch() throws IOException, URISyntaxException, CommandException, ParseException
        {

            SASConnection cn = new SASConnection("http://localhost:8080/labkey");
            //Create VocabularyDomain with adhoc properties
            JSONObject domainDesign = new JSONObject();

            JSONArray fields = new JSONArray();

            JSONObject field1 = new JSONObject();
            field1.put("name", "testIntField");
            field1.put("rangeURI", "int");

            JSONObject field2 = new JSONObject();
            field2.put("name", "testStringField");
            field2.put("rangeURI", "string");

            fields.put(field1);
            fields.put(field2);

            domainDesign.put("name","TestVocabulary");
            domainDesign.put("description","Test Ad Hoc Properties");
            domainDesign.put("fields", fields);

            CommandResponse domainResponse = createDomain(cn,"Vocabulary", domainDesign);

            //Save Batch - Use Vocabulary Domain properties while saving batch
            org.json.simple.JSONArray vocabularyFields = domainResponse.getProperty("fields");
            List<String> propertyURIs = new ArrayList<>();

            String fieldsJSONString = vocabularyFields.toJSONString();

            JSONArray fieldsJSON  = new JSONArray(fieldsJSONString);

            for(int i=0; i<fieldsJSON.length(); i++)
            {
                propertyURIs.add(fieldsJSON.getJSONObject(i).getString("propertyURI"));
            }

            JSONObject batch = new JSONObject();

            JSONObject batchProperty = new JSONObject();
            batchProperty.put(propertyURIs.get(0), 123);

            batch.put("properties", batchProperty);

            JSONArray runs = new JSONArray();
            JSONObject run = new JSONObject();
            run.put("name", "two");

            batchProperty = new JSONObject();
            batchProperty.put(propertyURIs.get(1), "hello");

            run.put("properties", batchProperty);
            runs.put(run);
            batch.put("runs", runs);

            CommandResponse batchResponse = saveBatch(cn, batch);

            //Verify properties got added
            JSONObject addedBatch = new JSONObject(batchResponse.getText()).getJSONObject("batch");
            JSONObject addedProperties = addedBatch.getJSONObject("properties");

            List<String> addedURIs = new ArrayList<>(addedProperties.keySet());

            assertEquals(propertyURIs.get(0), addedURIs.get(0), "Property not found");
        }

        private CommandResponse createDomain(SASConnection cn, String domainKind, JSONObject domainDesign) throws IOException, CommandException, URISyntaxException
        {
            PostCommand createDomainCommand = new PostCommand("property","createDomain");
            org.json.simple.JSONObject domain = new org.json.simple.JSONObject();
            domain.put("kind",domainKind);
            domain.put("domainDesign", domainDesign);
            createDomainCommand.setJsonObject(domain);
            return createDomainCommand.execute(cn, "/home");
        }

        private CommandResponse saveBatch(SASConnection cn, JSONObject batchDesign) throws IOException, CommandException
        {
            PostCommand createDomainCommand = new PostCommand("assay","saveAssayBatch");
            org.json.simple.JSONObject batch = new org.json.simple.JSONObject();
            batch.put("protocolName", "Sample Derivation Protocol");
            batch.put("batch", batchDesign);
            createDomainCommand.setJsonObject(batch);
            return createDomainCommand.execute(cn, "/home");
        }

    }
}

