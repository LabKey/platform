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

import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URIUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Serializes and deserializes experiment objects to and from JSON.
 * User: jeckels
 * Date: Jan 21, 2009
 */
public class ExperimentJSONConverter
{
    // General experiment object properties
    public static final String ID = "id";
    public static final String ROW_ID = "rowId";
    public static final String CREATED = "created";
    public static final String CREATED_BY = "createdBy";
    public static final String MODIFIED = "modified";
    public static final String MODIFIED_BY = "modifiedBy";
    public static final String NAME = "name";
    public static final String LSID = "lsid";
    public static final String CPAS_TYPE = "cpasType";
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

    // Material properties
    public static final String SAMPLE_SET = "sampleSet";

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
    }

    @NotNull
    public static JSONObject serialize(@NotNull Identifiable node, @NotNull User user, @NotNull Settings settings)
    {
        if (node instanceof ExpExperiment)
            return serializeRunGroup((ExpExperiment)node, null, settings);
        else if (node instanceof ExpRun)
            return serializeRun((ExpRun)node, null, user, settings);
        else if (node instanceof ExpMaterial)
            return serializeMaterial((ExpMaterial)node, settings);
        else if (node instanceof ExpData)
            return serializeData((ExpData)node, user, settings);
        else if (node instanceof ExpObject)
            return serializeExpObject((ExpObject)node, null, settings);
        else
            return serializeIdentifiable(node, settings);
    }

    public static JSONObject serializeRunGroup(ExpExperiment runGroup, Domain domain, @NotNull Settings settings)
    {
        JSONObject jsonObject = serializeExpObject(runGroup, domain != null ? domain.getProperties() : Collections.emptyList(), settings);
        jsonObject.put(COMMENT, runGroup.getComments());
        return jsonObject;
    }

    public static JSONObject serializeRun(ExpRun run, Domain domain, User user, @NotNull Settings settings)
    {
        JSONObject jsonObject = serializeExpObject(run, domain == null ? null : domain.getProperties(), settings);
        if (settings.isIncludeProperties())
        {
            jsonObject.put(COMMENT, run.getComments());
            jsonObject.put(PROTOCOL, serializeProtocol(run.getProtocol(), user));
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

        ExpProtocol protocol = run.getProtocol();
        if (protocol != null)
        {
            jsonObject.put(CPAS_TYPE, protocol.getLSID());
        }

        if (settings.isIncludeRunSteps())
        {
            JSONArray steps = new JSONArray();
            for (ExpProtocolApplication protApp : run.getProtocolApplications())
            {
                // We can skip the initial input and final steps ince we've already included the run-level inputs and
                // outputs and there aren't usually any interesting properties on the initial and final steps.
                if (protApp.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun || protApp.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRunOutput)
                    continue;

                JSONObject step = serializeRunProtocolApplication(protApp, run, user, settings);
                steps.put(step);
            }
            jsonObject.put(STEPS, steps);
        }

        return jsonObject;
    }

    public static JSONObject serializeProtocol(ExpProtocol protocol, User user)
    {
        if (protocol == null || !protocol.getContainer().hasPermission(user, ReadPermission.class))
            return null;

        // Just include basic protocol properties for now.
        // See GetProtocolAction and GWTProtocol for serializing an assay protocol with domain fields.
        JSONObject jsonObject = serializeExpObject(protocol, null, DEFAULT_SETTINGS.withIncludeProperties(false));
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
            outputMaterialArray.put(ExperimentJSONConverter.serializeMaterial(material, settings));
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
            if (runInput instanceof ExpDataRunInput)
            {
                json = ExperimentJSONConverter.serializeData(((ExpDataRunInput)runInput).getData(), user, settings);
            }
            else if (runInput instanceof ExpMaterialRunInput)
            {
                json = ExperimentJSONConverter.serializeMaterial(((ExpMaterialRunInput)runInput).getMaterial(), settings);
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

    protected static JSONObject serializeRunProtocolApplication(@NotNull ExpProtocolApplication protApp, ExpRun run, User user, Settings settings)
    {
        JSONObject json = serializeExpObject(protApp, null, settings);

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

        // CONSIDER: parameters
//        List<ProtocolApplicationParameter> parameters = ExperimentService.get().getProtocolApplicationParameters(application.getRowId());
//        if (!parameters.isEmpty())
//        {
//            json.put(PARAMETERS, parameters.stream().map());
//        }

        return json;
    }

    public static void serializeRunLevelProvenanceProperties(@NotNull JSONObject obj, ExpRun run)
    {
        ProvenanceService svc = ProvenanceService.get();
        if (svc == null)
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
                        inputSet.stream()
                                .map(Pair::getKey)
                                .map(ExperimentJSONConverter::serializeProvenanceObject)
                                .collect(Collectors.toUnmodifiableList()));
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
        if (svc == null)
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
            map.put("from", serializeProvenanceObject(pair.first));
        if (pair.second != null)
            map.put("to", serializeProvenanceObject(pair.second));
        return map;
    }

    // For now, just return the lsid if it isn't null
    // CONSIDER: Use LsidManager to find the object and call serialize() ?
    private static Object serializeProvenanceObject(String objectUri)
    {
        if (objectUri == null)
            return null;

        return objectUri;
    }

    /**
     * Serialize {@link Identifiable} java bean properties (Name, LSID, URL, and schema/query/pkFilters)
     */
    private static JSONObject serializeIdentifiableBean(@NotNull Identifiable obj)
    {
        JSONObject json = new JSONObject();

        json.put(NAME, obj.getName());
        json.put(LSID, obj.getLSID());
        var url = obj.detailsURL();
        if (url != null)
            json.put(URL, url);

        QueryRowReference rowRef = obj.getQueryRowReference();
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
    public static JSONObject serializeIdentifiable(@NotNull Identifiable obj, Settings settings)
    {
        JSONObject json = serializeIdentifiableBean(obj);

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
    public static JSONObject serializeExpObject(@NotNull ExpObject object, @Nullable List<? extends DomainProperty> properties, @NotNull Settings settings)
    {
        // While serializeIdentifiable can include object properties, we call serializeIdentifiableBean
        // instead and use serializeOntologyProperties(ExpObject) so the object properties will be
        // fetched using ExpObject.getProperty().
        JSONObject jsonObject = serializeIdentifiableBean(object);
        int rowId = object.getRowId();
        if (rowId != 0)
        {
            jsonObject.put(ID, rowId);
        }
        if (object.getCreatedBy() != null)
        {
            jsonObject.put(CREATED_BY, object.getCreatedBy().getEmail());
        }
        jsonObject.put(CREATED, object.getCreated());
        if (object.getModifiedBy() != null)
        {
            jsonObject.put(MODIFIED_BY, object.getModifiedBy().getEmail());
        }
        jsonObject.put(MODIFIED, object.getModified());
        String comment = object.getComment();
        if (comment != null)
            jsonObject.put(COMMENT, object.getComment());

        if (settings.isIncludeProperties())
        {
            JSONObject propertiesObject = serializeOntologyProperties(object, properties, settings);
            if (!propertiesObject.isEmpty())
                jsonObject.put(PROPERTIES, propertiesObject);
        }

        return jsonObject;
    }

    /**
     * Serialize the custom ontology properties associated with the object.
     */
    @NotNull
    private static JSONObject serializeOntologyProperties(@NotNull ExpObject object, @Nullable List<? extends DomainProperty> properties, @NotNull ExperimentJSONConverter.Settings settings)
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

    private static void serializeOntologyProperties(JSONObject json, Container c,
                                                    Set<String> seenPropertyURIs, Map<String, ObjectProperty> objectProps,
                                                    Settings settings)
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
            json.put(propertyURI, op.value());
        }
    }

    private static Object serializePropertyValue(Container container, PropertyType type, Settings settings, Object value)
    {
        if (type == PropertyType.FILE_LINK && value instanceof File)
        {
            // We need to return files not as simple string properties with the path, but as an Exp.Data object
            // with multiple values
            File f = (File) value;
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


    @Deprecated(forRemoval = true)
    @NotNull
    public static JSONObject serializeData(@NotNull ExpData data, @Nullable User user)
    {
        return serializeData(data, user, DEFAULT_SETTINGS);
    }

    @NotNull
    public static JSONObject serializeData(@NotNull ExpData data, @Nullable User user, @NotNull Settings settings)
    {
        final ExpDataClass dc = data.getDataClass(user);

        JSONObject jsonObject = serializeExpObject(data, null, settings);

        if (settings.isIncludeProperties())
        {
            if (dc != null)
            {
                JSONObject dataClassJsonObject = serializeExpObject(dc, null, settings.withIncludeProperties(false));
                if (dc.getCategory() != null)
                    dataClassJsonObject.put(DATA_CLASS_CATEGORY, dc.getCategory());
                jsonObject.put(DATA_CLASS, dataClassJsonObject);
            }
        }

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

        jsonObject.put(CPAS_TYPE, data.getCpasType());

        return jsonObject;
    }

    @Deprecated(forRemoval = true)
    @NotNull
    public static JSONObject serializeMaterial(@NotNull ExpMaterial material)
    {
        return serializeMaterial(material, DEFAULT_SETTINGS);
    }

    // TODO: Include MaterialInput edge properties (role and properties)
    // TODO: Include protocol input
    @NotNull
    public static JSONObject serializeMaterial(@NotNull ExpMaterial material, @NotNull Settings settings)
    {
        ExpSampleSet sampleSet = material.getSampleSet();

        JSONObject jsonObject;
        if (sampleSet == null)
        {
            jsonObject = serializeExpObject(material, null, settings);
        }
        else
        {
            jsonObject = serializeExpObject(material, sampleSet.getDomain().getProperties(), settings);
            if (sampleSet.hasNameAsIdCol())
            {
                JSONObject properties = jsonObject.optJSONObject(ExperimentJSONConverter.PROPERTIES);
                if (properties == null)
                    properties = new JSONObject();
                properties.put(ExperimentJSONConverter.NAME, material.getName());
                jsonObject.put(ExperimentJSONConverter.PROPERTIES, properties);
            }

            if (settings.isIncludeProperties())
            {
                JSONObject sampleSetJson = serializeExpObject(sampleSet, null, settings.withIncludeProperties(false));
                jsonObject.put(SAMPLE_SET, sampleSetJson);
            }
        }

        jsonObject.put(CPAS_TYPE, material.getCpasType());

        return jsonObject;
    }

    @NotNull
    public static Map<PropertyDescriptor, Object> convertProperties(Map<String, ? extends Object> propertiesJsonObject, List<? extends DomainProperty> dps, Container container, boolean ignoreMissingProperties) throws ValidationException
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
//            else if (propName.equals(ProvenanceService.PROVENANCE_INPUT_PROPERTY))
//            {
//                // handle inputs
//            }
//            else if (propName.equals(ProvenanceService.PROVENANCE_OUTPUT_PROPERTY))
//            {
//                // handle outputs
//            }
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

    private static Object convertProperty(Object value, DomainProperty dp, Container container)
    {
        return convertProperty(value, dp.getPropertyDescriptor(), container);
    }

    private static Object convertProperty(Object value, PropertyDescriptor pd, Container container)
    {
        Class javaType = pd.getPropertyType().getJavaType();
        Object convertedValue = ConvertUtils.lookup(javaType).convert(javaType, value);

        // We need to special case Files to apply extra checks based on the context from which they're being
        // referenced
        if (javaType.equals(File.class) && convertedValue instanceof File)
        {
            File f = (File)convertedValue;
            PipeRoot root = PipelineService.get().getPipelineRootSetting(container);
            FileContentService fileService = FileContentService.get();
            File fileRoot = fileService == null ? null : fileService.getFileRoot(container);
            boolean acceptableFile = (root != null && root.isUnderRoot((File)convertedValue)) || (fileRoot != null && URIUtil.isDescendant(fileRoot.toURI(), f.toURI()));
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
}
