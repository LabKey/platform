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
package org.labkey.api.exp.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URIUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.exp.api.ExpData.DATA_INPUTS_PREFIX;
import static org.labkey.api.exp.api.ExpDataClass.NEW_DATA_CLASS_ALIAS_VALUE;
import static org.labkey.api.exp.api.SampleTypeService.MATERIAL_INPUTS_PREFIX;
import static org.labkey.api.exp.api.SampleTypeService.NEW_SAMPLE_TYPE_ALIAS_VALUE;

/**
 * Serializes and deserializes experiment objects to and from JSON.
 */
public class ExperimentJSONConverter
{
    // General experiment object properties
    public static final String ID = "id";
    public static final String ROW_ID = "rowId";
    public static final String CONTAINER = "container";
    public static final String CONTAINER_PATH = "containerPath";
    public static final String CREATED = "created";
    public static final String CREATED_BY = "createdBy";
    public static final String MODIFIED = "modified";
    public static final String MODIFIED_BY = "modifiedBy";
    public static final String NAME = "name";
    public static final String WORKFLOW_TASK = "workflowTask";
    public static final String LSID = "lsid";
    public static final String CPAS_TYPE = "cpasType";
    public static final String EXP_TYPE = "expType";
    public static final String URL = "url";
    public static final String PROPERTIES = "properties";
    public static final String COMMENT = "comment";
    public static final String DATA_FILE_URL = "dataFileURL";
    public static final String ABSOLUTE_PATH = "absolutePath";
    public static final String PIPELINE_PATH = "pipelinePath"; //path relative to pipeline root
    public static final String PROTOCOL_NAME = "protocolName"; // non-assay backed protocol name

    public static final String SCHEMA_NAME = QueryParam.schemaName.name();
    public static final String QUERY_NAME = QueryParam.queryName.name();
    public static final String PK_FILTERS = "pkFilters";

    // Run properties
    public static final String PROTOCOL = "protocol";
    public static final String DATA_INPUTS = "dataInputs";
    public static final String MATERIAL_INPUTS = "materialInputs";
    public static final String ROLE = "role";
    public static final String DATA_OUTPUTS = "dataOutputs";
    public static final String MATERIAL_OUTPUTS = "materialOutputs";
    public static final String STEPS = "steps";

    public static final String MATERIAL_INPUTS_ALIAS_PREFIX = MATERIAL_INPUTS + "/";
    public static final String DATA_INPUTS_ALIAS_PREFIX = DATA_INPUTS + "/";

    // Material properties
    public static final String SAMPLE_TYPE = "sampleSet";

    // Data properties
    public static final String DATA_CLASS = "dataClass";
    public static final String DATA_CLASS_CATEGORY = "category";
    public static final String EDGE = "edge";
    public static final String PROTOCOL_INPUT = "protocolInput";

    // Protocol Application properties
    public static final String APPLICATION_TYPE = "applicationType";
    public static final String ACTION_SEQUENCE = "activitySequence";
    public static final String ACTIVITY_DATE = "activityDate";
    public static final String START_TIME = "startTime";
    public static final String END_TIME = "endTime";
    public static final String RECORD_COUNT = "recordCount";
    public static final String PARAMETERS = "parameters";

    // Domain kinds
    public static final String VOCABULARY_DOMAIN = "Vocabulary";

    public static final Settings DEFAULT_SETTINGS = new Settings();

    public static class Settings
    {
        private final boolean includeProperties;
        private final boolean includeInputsAndOutputs;
        private final boolean includeRunSteps;

        public Settings()
        {
            this(true, true, false);
        }

        public Settings(boolean includeProperties, boolean includeInputsAndOutputs, boolean includeRunSteps)
        {
            this.includeProperties = includeProperties;
            this.includeInputsAndOutputs = includeInputsAndOutputs;
            this.includeRunSteps = includeRunSteps;
        }

        public boolean isIncludeProperties()
        {
            return includeProperties;
        }

        public boolean isIncludeInputsAndOutputs()
        {
            return includeInputsAndOutputs;
        }

        public boolean isIncludeRunSteps()
        {
            return includeRunSteps;
        }

        public Settings withIncludeProperties(boolean b)
        {
            return new Settings(b, includeInputsAndOutputs, includeRunSteps);
        }

        public Settings withIncludeInputsAndOutputs(boolean b)
        {
            return new Settings(includeProperties, b, includeRunSteps);
        }

        public Settings withIncludeRunSteps(boolean b)
        {
            return new Settings(includeProperties, includeInputsAndOutputs, b);
        }
    }

    @NotNull
    public static JSONObject serialize(@NotNull Identifiable node, @NotNull User user, @NotNull Settings settings)
    {
        if (node instanceof ExpExperiment expExperiment)
            return serializeRunGroup(expExperiment, null, settings, user);
        else if (node instanceof ExpRun expRun)
            return serializeRun(expRun, null, user, settings);
        else if (node instanceof ExpMaterial expMaterial)
            return serializeMaterial(expMaterial, user, settings);
        else if (node instanceof ExpData expData)
            return serializeData(expData, user, settings);
        else if (node instanceof ExpObject expObject)
            return serializeExpObject(expObject, null, settings, user);
        else
            return serializeIdentifiable(node, settings, user);
    }

    public static JSONObject serializeRunGroup(ExpExperiment runGroup, Domain domain, @NotNull Settings settings, @Nullable User user)
    {
        JSONObject jsonObject = serializeExpObject(runGroup, domain == null ? null : domain.getProperties(), settings, user);
        jsonObject.put(ExperimentJSONConverter.EXP_TYPE, ExpExperiment.DEFAULT_CPAS_TYPE);

        ExpProtocol protocol = runGroup.getBatchProtocol();
        if (protocol != null)
        {
            jsonObject.put(CPAS_TYPE, protocol.getLSID());
        }

        if (settings.isIncludeProperties())
        {
            jsonObject.put(COMMENT, runGroup.getComments());
            jsonObject.put(PROTOCOL, serializeProtocol(protocol, user));
        }

        return jsonObject;
    }

    public static JSONObject serializeRun(ExpRun run, Domain domain, User user, @NotNull Settings settings)
    {
        JSONObject jsonObject = serializeExpObject(run, domain == null ? null : domain.getProperties(), settings, user);
        jsonObject.put(ExperimentJSONConverter.EXP_TYPE, ExpRun.DEFAULT_CPAS_TYPE);

        ExpProtocol protocol = run.getProtocol();
        if (protocol != null)
        {
            jsonObject.put(CPAS_TYPE, protocol.getLSID());
        }

        if (settings.isIncludeProperties())
        {
            jsonObject.put(COMMENT, run.getComments());
            jsonObject.put(PROTOCOL, serializeProtocol(protocol, user));
        }

        if (settings.isIncludeInputsAndOutputs())
        {
            ExpProtocolApplication inputApp = run.getInputProtocolApplication();
            if (inputApp != null)
            {
                jsonObject.put(DATA_INPUTS, serializeRunInputs(inputApp.getDataInputs(), user, settings));
                jsonObject.put(MATERIAL_INPUTS, serializeRunInputs(inputApp.getMaterialInputs(), user, settings));
            }
            else
            {
                jsonObject.put(DATA_INPUTS, new JSONArray());
                jsonObject.put(MATERIAL_INPUTS, new JSONArray());
            }

            // Inputs into the final output step are outputs of the entire run
            ExpProtocolApplication outputApp = run.getOutputProtocolApplication();
            if (outputApp != null)
            {
                jsonObject.put(DATA_OUTPUTS, serializeRunDataOutputs(outputApp.getDataInputs(), user, settings));
                jsonObject.put(MATERIAL_OUTPUTS, serializeRunInputs(outputApp.getMaterialInputs(), user, settings));
            }
            else
            {
                jsonObject.put(DATA_OUTPUTS, new JSONArray());
                jsonObject.put(MATERIAL_OUTPUTS, new JSONArray());
            }

            serializeRunLevelProvenanceProperties(jsonObject, run);
        }

        if (settings.isIncludeRunSteps())
        {
            JSONArray steps = new JSONArray();
            for (ExpProtocolApplication protApp : run.getProtocolApplications())
            {
                // We can skip the initial input and final steps once we've already included the run-level inputs and
                // outputs and there aren't usually any interesting properties on the initial and final steps.
                if (protApp.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun || protApp.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRunOutput)
                    continue;

                JSONObject step = serializeRunProtocolApplication(protApp, user, settings);
                steps.put(step);
            }
            jsonObject.put(STEPS, steps);
        }

        return jsonObject;
    }

    public static @Nullable JSONObject serializeProtocol(ExpProtocol protocol, User user)
    {
        if (protocol == null || !protocol.getContainer().hasPermission(user, ReadPermission.class))
            return null;

        // Just include basic protocol properties for now.
        // See GetProtocolAction and GWTProtocol for serializing an assay protocol with domain fields.
        JSONObject jsonObject = serializeExpObject(protocol, null, DEFAULT_SETTINGS.withIncludeProperties(false), user);
        jsonObject.put(ExperimentJSONConverter.EXP_TYPE, ExpProtocol.DEFAULT_CPAS_TYPE);
        return jsonObject;
    }

    public static JSONObject serializeRunOutputs(Collection<ExpData> data, Collection<ExpMaterial> materials, User user, @NotNull Settings settings)
    {
        JSONObject obj = new JSONObject();
        serializeRunOutputs(obj, data, materials, user, settings);
        return obj;
    }

    protected static void serializeRunOutputs(@NotNull JSONObject obj, Collection<ExpData> data, Collection<ExpMaterial> materials, User user, @NotNull Settings settings)
    {
        JSONArray outputDataArray = new JSONArray();
        for (ExpData d : data)
        {
            if (null != d.getFile() || null != d.getDataClass(user))
                outputDataArray.put(ExperimentJSONConverter.serializeData(d, user, settings));
        }
        obj.put(DATA_OUTPUTS, outputDataArray);

        JSONArray outputMaterialArray = new JSONArray();
        for (ExpMaterial material : materials)
        {
            outputMaterialArray.put(ExperimentJSONConverter.serializeMaterial(material, user, settings));
        }
        obj.put(MATERIAL_OUTPUTS, outputMaterialArray);
    }

    protected static JSONArray serializeRunDataOutputs(Collection<? extends ExpDataRunInput> inputs, User user, Settings settings)
    {
        // filter out any output data that have a file URL or aren't a DataClass
        return serializeRunInputs(inputs.stream().filter(input -> {
            ExpData d = input.getData();
            return d != null && (d.getFile() != null || d.getDataClass(user) != null);
        }).collect(Collectors.toList()), user, settings);
    }

    protected static JSONArray serializeRunInputs(Collection<? extends ExpRunInput> inputs, User user, Settings settings)
    {
        JSONArray jsonArray = new JSONArray();

        for (ExpRunInput runInput : inputs)
        {
            JSONObject json;
            if (runInput instanceof ExpDataRunInput expDataRunInput)
            {
                json = ExperimentJSONConverter.serializeData(expDataRunInput.getData(), user, settings);
            }
            else if (runInput instanceof ExpMaterialRunInput expMaterialRunInput)
            {
                json = ExperimentJSONConverter.serializeMaterial(expMaterialRunInput.getMaterial(), user, settings);
            }
            else
            {
                throw new IllegalArgumentException("Unknown run input: " + runInput);
            }

            // Workaround for Issue 40119: Only include "role" for materials for now.  Including "role" for
            // ExpData will break ModuleAssayTransformTest due to the the old transform data file still being attached
            // with the "ImportedData" role.
            if (runInput instanceof ExpMaterialRunInput)
                json.put(ROLE, runInput.getRole());

            if (settings.isIncludeProperties())
            {
                JSONObject edgeProperties = serializeOntologyProperties(runInput, null, settings);
                if (!edgeProperties.isEmpty())
                {
                    JSONObject edgeJson = new JSONObject();
                    // NOTE: The standard properties aren't interesting for the MaterialInput/DataInput edge
                    edgeJson.put(LSID, runInput.getLSID());
                    edgeJson.put(PROPERTIES, edgeProperties);
                    json.put(EDGE, edgeJson);
                }
            }

            ExpProtocolInput protocolInput = runInput.getProtocolInput();
            if (protocolInput != null)
            {
                Lsid lsid = Lsid.parse(protocolInput.getLSID());
                json.put(PROTOCOL_INPUT, lsid.getObjectId());
            }

            jsonArray.put(json);
        }

        return jsonArray;
    }

    protected static JSONObject serializeRunProtocolApplication(@NotNull ExpProtocolApplication protApp, User user, Settings settings)
    {
        JSONObject json = serializeExpObject(protApp, null, settings, user);
        json.put(ExperimentJSONConverter.EXP_TYPE, ExpProtocolApplication.DEFAULT_CPAS_TYPE);

        json.put(ACTION_SEQUENCE, protApp.getActionSequence());
        json.put(APPLICATION_TYPE, protApp.getApplicationType().toString());
        if (protApp.getComments() != null)
            json.put(COMMENT, protApp.getComments());

        if (protApp.getActivityDate() != null)
            json.put(ACTIVITY_DATE, protApp.getActivityDate());

        if (protApp.getStartTime() != null)
            json.put(START_TIME, protApp.getStartTime());

        if (protApp.getEndTime() != null)
            json.put(END_TIME, protApp.getEndTime());

        if (protApp.getRecordCount() != null)
            json.put(RECORD_COUNT, protApp.getRecordCount());

        json.put(PROTOCOL, serializeProtocol(protApp.getProtocol(), user));

        if (settings.isIncludeInputsAndOutputs())
        {
            json.put(DATA_INPUTS, serializeRunInputs(protApp.getDataInputs(), user, settings));
            json.put(MATERIAL_INPUTS, serializeRunInputs(protApp.getMaterialInputs(), user, settings));

            json.put(DATA_OUTPUTS, serializeRunInputs(protApp.getDataOutputs(), user, settings));
            json.put(MATERIAL_OUTPUTS, serializeRunInputs(protApp.getMaterialOutputs(), user, settings));

            // provenance
            provenanceMap(json, protApp);
        }

        return json;
    }

    public static void serializeRunLevelProvenanceProperties(@NotNull JSONObject obj, ExpRun run)
    {
        ProvenanceService svc = ProvenanceService.get();
        if (!svc.isProvenanceSupported())
            return;

        // Include provenance inputs of the run in this format:
        // {
        //   objectInputs: [ "urn:lsid:lsid1", "urn:lsid:lsid" ]
        // }
        ExpProtocolApplication inputApp = run.getInputProtocolApplication();
        if (inputApp != null)
        {
            var inputSet = svc.getProvenanceObjectUris(inputApp.getRowId());
            if (!inputSet.isEmpty())
            {
                obj.put(ProvenanceService.PROVENANCE_OBJECT_INPUTS,
                        Collections.unmodifiableList(inputSet.stream()
                                .map(Pair::getKey)
                                .map((objectUri -> serializeProvenanceObject(objectUri, false)))
                                .collect(Collectors.toList())));
            }
        }

        ExpProtocolApplication outputApp = run.getOutputProtocolApplication();
        if (outputApp != null)
        {
            provenanceMap(obj, outputApp);
        }
    }

    // Include provenance object mapping for the run in this format:
    // {
    //   provenanceMap: [{
    //     from: 'urn:lsid:input1', to: 'urn:lsid:output1'
    //   },{
    //     from: 'urn:lsid:input2', to: 'urn:lsid:output1'
    //   }]
    // }
    public static void provenanceMap(@NotNull JSONObject obj, ExpProtocolApplication app)
    {
        ProvenanceService svc = ProvenanceService.get();
        if (!svc.isProvenanceSupported())
            return;

        var outputSet = svc.getProvenanceObjectUris(app.getRowId());
        if (!outputSet.isEmpty())
        {
            obj.put(ProvenanceService.PROVENANCE_OBJECT_MAP,
                    outputSet.stream()
                            .map(ExperimentJSONConverter::serializeProvenancePair)
                            .collect(Collectors.toUnmodifiableList()));
        }
    }

    private static Map<String, Object> serializeProvenancePair(Pair<String, String> pair)
    {
        var map = new HashMap<String, Object>();
        if (pair.first != null)
            map.put("from", serializeProvenanceObject(pair.first, true));
        if (pair.second != null)
            map.put("to", serializeProvenanceObject(pair.second, true));
        return map;
    }

    private static @Nullable Object serializeProvenanceObject(String objectUri, boolean asJSON)
    {
        if (objectUri == null)
            return null;

        if (asJSON)
        {
            Identifiable obj = LsidManager.get().getObject(objectUri);
            if (obj == null)
                return null;

            return serializeIdentifiableBean(obj, null);
        }

        return objectUri;
    }

    /**
     * Serialize {@link Identifiable} java bean properties (Name, LSID, URL, and schema/query/pkFilters)
     */
    private static JSONObject serializeIdentifiableBean(@NotNull Identifiable obj, @Nullable User user)
    {
        JSONObject json = new JSONObject();

        json.put(NAME, obj.getName());
        json.put(LSID, obj.getLSID());
        var url = obj.detailsURL();
        if (url != null)
            json.put(URL, url);

        json.put(CONTAINER, obj.getContainer().getId());
        json.put(CONTAINER_PATH, obj.getContainer().getPath());

        QueryRowReference rowRef = obj.getQueryRowReference(user);
        if (rowRef != null)
        {
            json.put(SCHEMA_NAME, rowRef.getSchemaKey().toString());
            json.put(QUERY_NAME, rowRef.getQueryName());
            json.put(PK_FILTERS, rowRef.getPkFilters().stream().map(f -> Map.of("fieldKey", f.first.toString(), "value", f.second)).collect(Collectors.toList()));
        }
        return json;
    }

    /**
     * Serialize {@link Identifiable} java bean properties (Name, LSID, URL, and schema/query/pkFilters)
     * as well as any object properties for the object.
     */
    @NotNull
    public static JSONObject serializeIdentifiable(@NotNull Identifiable obj, Settings settings, @Nullable User user)
    {
        JSONObject json = serializeIdentifiableBean(obj, user);
        json.put(ExperimentJSONConverter.EXP_TYPE, (Object) null);

        if (settings.isIncludeProperties())
        {
            Set<String> seenPropertyURIs = new HashSet<>();
            JSONObject propertiesObject = new JSONObject();
            Map<String, ObjectProperty> objectProps = OntologyManager.getPropertyObjects(obj.getContainer(), obj.getLSID());
            serializeOntologyProperties(propertiesObject, obj.getContainer(), seenPropertyURIs, objectProps, settings);
            if (!propertiesObject.isEmpty())
                json.put(PROPERTIES, propertiesObject);
        }

        return json;
    }

    /**
     * Serialize ExpObject java bean properties (ID, CreatedBy, Comment) and include object properties and the optional domain properties.
     */
    @NotNull
    public static JSONObject serializeExpObject(
        @NotNull ExpObject object,
        @Nullable List<? extends DomainProperty> properties,
        @NotNull Settings settings,
        @Nullable User user
    )
    {
        // While serializeIdentifiable can include object properties, we call serializeIdentifiableBean
        // instead and use serializeOntologyProperties(ExpObject) so the object properties will be
        // fetched using ExpObject.getProperty().
        JSONObject jsonObject = serializeIdentifiableBean(object, user);
        jsonObject.put(ExperimentJSONConverter.EXP_TYPE, ExpObject.DEFAULT_CPAS_TYPE);

        int rowId = object.getRowId();
        if (rowId != 0)
            jsonObject.put(ID, rowId);

        var createdBy = object.getCreatedBy();
        if (createdBy != null)
            jsonObject.put(CREATED_BY, createdBy.getEmail());
        jsonObject.put(CREATED, object.getCreated());

        var modifiedBy = object.getModifiedBy();
        if (modifiedBy != null)
            jsonObject.put(MODIFIED_BY, modifiedBy.getEmail());
        jsonObject.put(MODIFIED, object.getModified());

        if (settings.isIncludeProperties())
        {
            jsonObject.put(COMMENT, object.getComment());
            JSONObject propertiesObject = serializeOntologyProperties(object, properties, settings);
            if (!propertiesObject.isEmpty())
                jsonObject.put(PROPERTIES, propertiesObject);
        }

        return jsonObject;
    }

    /**
     * Serialize the custom ontology properties associated with the object.
     * NOTE: If properties of the object are explicitly passed into this method then the value of
     * those properties will only be sourced from the ontology store. Conversely, if values for those properties
     * are persisted somewhere else (e.g. a provisioned table) then those values will be ignored. You're most likely
     * better off passing in null for properties unless you are wanting to ignore other value sources for some reason.
     */
    @NotNull
    private static JSONObject serializeOntologyProperties(
        @NotNull ExpObject object,
        @Nullable List<? extends DomainProperty> properties,
        @NotNull ExperimentJSONConverter.Settings settings
    )
    {
        Set<String> seenPropertyURIs = new HashSet<>();
        JSONObject propertiesObject = new JSONObject();
        if (properties != null)
        {
            for (DomainProperty dp : properties)
            {
                seenPropertyURIs.add(dp.getPropertyURI());
                Object value = object.getProperty(dp);
                value = serializePropertyValue(object.getContainer(), dp.getPropertyDescriptor().getPropertyType(), settings, value);
                propertiesObject.put(dp.getName(), value);
            }
        }

        var objectProps = object.getObjectProperties();
        serializeOntologyProperties(propertiesObject, object.getContainer(), seenPropertyURIs, objectProps, settings);

        return propertiesObject;
    }

    private static void serializeOntologyProperties(
        JSONObject json,
        Container c,
        Set<String> seenPropertyURIs,
        Map<String, ObjectProperty> objectProps,
        Settings settings
    )
    {
        for (var propPair : objectProps.entrySet())
        {
            String propertyURI = propPair.getKey();
            if (seenPropertyURIs.contains(propertyURI))
                continue;
            seenPropertyURIs.add(propertyURI);

            ObjectProperty op = propPair.getValue();
            PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(op.getPropertyURI(), c);
            PropertyType type = pd != null ? pd.getPropertyType() : op.getPropertyType();

            Object value = serializePropertyValue(c, type, settings, op.value());
            json.put(propertyURI, value);
        }
    }

    private static Object serializePropertyValue(Container container, PropertyType type, Settings settings, Object value)
    {
        if (type == PropertyType.FILE_LINK && value instanceof File f)
        {
            // We need to return files not as simple string properties with the path, but as an Exp.Data object
            // with multiple values
            ExpData data = ExperimentService.get().getExpDataByURL(f, container);
            if (data != null)
            {
                // If we can find a row in the data table, return that
                value = serializeData(data, null, settings);
            }
            else
            {
                // Otherwise, return a subset of all the data fields that we know about
                JSONObject jsonFile = new JSONObject();
                jsonFile.put(ABSOLUTE_PATH, f.getAbsolutePath());
                PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(container);
                if (pipeRoot != null)
                {
                    jsonFile.put(PIPELINE_PATH, pipeRoot.relativePath(f));
                }
                value = jsonFile;
            }
        }

        return value;
    }

    @NotNull
    public static JSONObject serializeData(@NotNull ExpData data, @Nullable User user, @NotNull Settings settings)
    {
        JSONObject jsonObject = serializeExpObject(data, null, settings, user);

        if (settings.isIncludeProperties())
        {
            ExpDataClass dataClass = data.getDataClass(user);

            if (dataClass != null)
            {
                JSONObject dataClassJsonObject = serializeExpObject(dataClass, null, settings.withIncludeProperties(false), user);
                if (dataClass.getCategory() != null)
                    dataClassJsonObject.put(DATA_CLASS_CATEGORY, dataClass.getCategory());
                jsonObject.put(DATA_CLASS, dataClassJsonObject);
            }
        }

        jsonObject.put(CPAS_TYPE, data.getCpasType());
        jsonObject.put(EXP_TYPE, ExpData.DEFAULT_CPAS_TYPE);
        jsonObject.put(DATA_FILE_URL, data.getDataFileUrl());

        File f = data.getFile();
        if (f != null)
        {
            jsonObject.put(ABSOLUTE_PATH, f.getAbsolutePath());
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(data.getContainer());
            if (pipeRoot != null)
            {
                jsonObject.put(PIPELINE_PATH, pipeRoot.relativePath(f));
            }
        }

        return jsonObject;
    }

    // TODO: Include MaterialInput edge properties (role and properties)
    // TODO: Include protocol input
    @NotNull
    public static JSONObject serializeMaterial(@NotNull ExpMaterial material, @Nullable User user, @NotNull Settings settings)
    {
        JSONObject jsonObject = serializeExpObject(material, null, settings, user);

        ExpSampleType sampleType = material.getSampleType();
        if (sampleType != null)
        {
            if (sampleType.hasNameAsIdCol())
            {
                JSONObject properties = jsonObject.optJSONObject(ExperimentJSONConverter.PROPERTIES);
                if (properties == null)
                    properties = new JSONObject();
                properties.put(ExperimentJSONConverter.NAME, material.getName());
                jsonObject.put(ExperimentJSONConverter.PROPERTIES, properties);
            }

            if (settings.isIncludeProperties())
            {
                JSONObject sampleTypeJson = serializeExpObject(sampleType, null, settings.withIncludeProperties(false), user);
                jsonObject.put(SAMPLE_TYPE, sampleTypeJson);
            }
        }

        jsonObject.put(CPAS_TYPE, material.getCpasType());
        jsonObject.put(ExperimentJSONConverter.EXP_TYPE, ExpMaterial.DEFAULT_CPAS_TYPE);

        boolean isAliquot = !StringUtils.isEmpty(material.getAliquotedFromLSID());
        boolean isDerivative = false;
        if (!isAliquot)
            isDerivative = material.getRunId() != null && material.getRunId() > 0;

        jsonObject.put("materialLineageType", isAliquot ? "Aliquot" : (isDerivative ? "Derivative" : "RootMaterial"));

        return jsonObject;
    }

    @NotNull
    public static Map<PropertyDescriptor, Object> convertProperties(
        @NotNull JSONObject propertiesJsonObject,
        List<? extends DomainProperty> dps,
        Container container,
        boolean ignoreMissingProperties
    ) throws ValidationException
    {
        Map<PropertyDescriptor, Object> properties = new HashMap<>();

        // map of domain property name
        Map<String, DomainProperty> propertiesMap = new HashMap<>();
        if (null != dps)
        {
            for (DomainProperty dp : dps)
            {
                propertiesMap.put(dp.getPropertyURI(), dp);
                propertiesMap.put(dp.getName(), dp);

                // initialize all properties in the domain with null values when not ignoring missing props
                if (!ignoreMissingProperties)
                    properties.put(dp.getPropertyDescriptor(), null);
            }
        }

        for (String propName : propertiesJsonObject.keySet())
        {
            Object value = propertiesJsonObject.get(propName);
            DomainProperty dp = propertiesMap.get(propName);
            if (dp != null)
            {
                value = convertProperty(value, dp, container);
                properties.put(dp.getPropertyDescriptor(), value);
            }
            else if (URIUtil.hasURICharacters(propName))
            {
                // resolve propName by PropertyURI if propName looks like a URI
                PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(propName, container);

                //ask OM to get the list of domains and check the domains (vocabulary domain)
                if (pd != null)
                {
                    List<Domain> domainsForPropertyDescriptor = OntologyManager.getDomainsForPropertyDescriptor(container, pd);

                    boolean propertyInVocabulary = domainsForPropertyDescriptor.stream().anyMatch(domain -> domain.getDomainKind().getKindName().equals(VOCABULARY_DOMAIN));

                    //only properties that exist in any vocabulary in this container are saved in the batch
                    if (propertyInVocabulary)
                    {
                        value = convertProperty(value, pd, container);
                        properties.put(pd, value);
                    }
                }
                else
                {
                    throw new ValidationException("Property does not exist in any Vocabulary Domain in this container: " + propName);
                }
            }

        }

        return properties;
    }

    private static Object convertProperty(Object value, @NotNull DomainProperty dp, Container container)
    {
        return convertProperty(value, dp.getPropertyDescriptor(), container);
    }

    private static Object convertProperty(Object value, @NotNull PropertyDescriptor pd, Container container)
    {
        Class javaType = pd.getPropertyType().getJavaType();
        // Issue 48040: Process JSONObject.NULL sentinel value as null
        Object convertedValue = ConvertUtils.lookup(javaType).convert(javaType, JSONObject.NULL.equals(value) ? null : value);

        // We need to special case Files to apply extra checks based on the context from which they're being
        // referenced
        if (javaType.equals(File.class) && convertedValue instanceof File f)
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(container);
            FileContentService fileService = FileContentService.get();
            File fileRoot = fileService == null ? null : fileService.getFileRoot(container);
            boolean acceptableFile = (root != null && root.isUnderRoot(f)) || (fileRoot != null && URIUtil.isDescendant(fileRoot.toURI(), f.toURI()));
            if (!acceptableFile)
            {
                // Don't let an assay reference a file that doesn't fall under this container's file root
                // and/or pipeline root.
                // This would be a security problem.
                convertedValue = null;
            }
        }

        return convertedValue;
    }

    // teach Jackson serialization to use ExperimentJSONConverter for Identifiable object instances
    public static class IdentifiableSerializer extends StdSerializer<Identifiable>
    {
        private final User _user;
        private final Settings _settings;

        protected IdentifiableSerializer(Class<Identifiable> t)
        {
            this(t, User.guest, DEFAULT_SETTINGS);
        }

        public IdentifiableSerializer(Class<Identifiable> t, User user, Settings settings)
        {
            super(t);
            _user = user;
            _settings = settings;
        }

        @Override
        public void serialize(Identifiable value, JsonGenerator gen, SerializerProvider provider) throws IOException
        {
            JSONObject obj = ExperimentJSONConverter.serialize(value, _user, _settings);
            gen.writeObject(obj);
        }
    }

    @Nullable
    public static String getAliasJson(Map<String, Map<String, Object>> importAliases, String currentAliasName)
    {
        if (importAliases == null || importAliases.isEmpty())
            return null;

        Map<String, Map<String, Object>> aliases = sanitizeAliases(importAliases, currentAliasName);

        try
        {
            return JsonUtil.DEFAULT_MAPPER.writeValueAsString(aliases);
        }
        catch (JsonProcessingException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private static Map<String, Map<String, Object>> sanitizeAliases(Map<String, Map<String, Object>> importAliases, String currentAliasName)
    {
        Map<String, Map<String, Object>> cleanAliases = new HashMap<>();
        importAliases.forEach((key, value) -> {
            String trimmedKey = StringUtils.trimToNull(key);
            String trimmedVal = StringUtils.trimToNull((String) value.get("inputType"));
            String requiredStr = Objects.toString(value.get("required"), null);

            // Sanity check this should be caught earlier
            if (trimmedKey == null || trimmedVal == null)
                throw new IllegalArgumentException("Parent aliases contain blanks");

            // Substitute the currentAliasName for the placeholder value
            if (trimmedVal.equalsIgnoreCase(NEW_SAMPLE_TYPE_ALIAS_VALUE) ||
                    trimmedVal.equalsIgnoreCase(MATERIAL_INPUTS_PREFIX + NEW_SAMPLE_TYPE_ALIAS_VALUE))
            {
                trimmedVal = MATERIAL_INPUTS_ALIAS_PREFIX + currentAliasName;
            }
            else if (trimmedVal.equalsIgnoreCase(NEW_DATA_CLASS_ALIAS_VALUE) ||
                    trimmedVal.equalsIgnoreCase(DATA_INPUTS_PREFIX + NEW_DATA_CLASS_ALIAS_VALUE))
            {
                trimmedVal = DATA_INPUTS_ALIAS_PREFIX + currentAliasName;
            }

            cleanAliases.put(trimmedKey, Map.of("inputType", trimmedVal, "required", Boolean.valueOf(requiredStr)));
        });

        return cleanAliases;
    }

    public static Map<String, Map<String, Object>> parseImportAliases(String parentImportAliasMapStr) throws IOException
    {
        if (StringUtils.isBlank(parentImportAliasMapStr))
            return Collections.emptyMap();

        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {};

        Map<String, Map<String, Object>> aliases = new HashMap<>();
        Map<String, Object> aliasMap = JsonUtil.DEFAULT_MAPPER.readValue(parentImportAliasMapStr, typeRef);
        for (Map.Entry<String, Object> entry : aliasMap.entrySet())
        {
            String alias = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String)
            {
                // legacy importAlias without 'required' field
                // {"LabParent":"dataInputs/Lab","BloodParent":"materialInputs/Blood"}
                aliases.put(alias, Map.of("inputType", value, "required", false));
            }
            else if (value instanceof Map map)
            {
                String inputType = (String) map.get("inputType");
                String requiredStr = Objects.toString(map.get("required"), null);
                aliases.put(alias, Map.of("inputType", inputType, "required", Boolean.valueOf(requiredStr)));
            }
        }

        return aliases;
    }

    public static final class TestCase extends Assert
    {
        @Test
        public void testConvertProperty()
        {
            final Container c = ContainerManager.createMockContainer();

            PropertyDescriptor pd = new PropertyDescriptor(null, PropertyType.INTEGER, "intField", c);
            assertNull(convertProperty(null, pd, c));
            assertNull(convertProperty(JSONObject.NULL, pd, c));
            assertNull(convertProperty("", pd, c));
            assertEquals(1, convertProperty("1", pd, c));
        }
    }
}
