/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataClassDataIteratorTransformer;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentProtocolHandler;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.ExperimentRunTypeSource;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidType;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.query.ExpDataClassTable;
import org.labkey.api.exp.query.ExpDataInputTable;
import org.labkey.api.exp.query.ExpDataProtocolInputTable;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.exp.query.ExpMaterialInputTable;
import org.labkey.api.exp.query.ExpMaterialProtocolInputTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpProtocolApplicationTable;
import org.labkey.api.exp.query.ExpProtocolTable;
import org.labkey.api.exp.query.ExpQCFlagTable;
import org.labkey.api.exp.query.ExpRunGroupMapTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSampleTypeTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.ExpUnreferencedSampleFilesTable;
import org.labkey.api.exp.query.SampleStatusTable;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.QueryViewProvider;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.IntegerUtils;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.vfs.FileLike;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.labkey.api.exp.api.ExpDataClass.NEW_DATA_CLASS_ALIAS_VALUE;
import static org.labkey.api.exp.api.SampleTypeService.NEW_SAMPLE_TYPE_ALIAS_VALUE;

public interface ExperimentService extends ExperimentRunTypeSource
{
    String MODULE_NAME = "Experiment";
    String SCHEMA_LOCATION = "http://cpas.fhcrc.org/exp/xml http://www.labkey.org/download/XarSchema/V2.3/expTypes.xsd";

    String SAMPLE_DERIVATION_PROTOCOL_LSID = "urn:lsid:labkey.org:Protocol:SampleDerivationProtocol";
    String SAMPLE_DERIVATION_PROTOCOL_NAME = "Sample Derivation Protocol";

    String SAMPLE_ALIQUOT_PROTOCOL_LSID = "urn:lsid:labkey.org:Protocol:SampleAliquotProtocol";
    String SAMPLE_ALIQUOT_PROTOCOL_NAME = "Sample Aliquot Protocol";

    String SAMPLE_MANAGEMENT_JOB_PROTOCOL_PREFIX = "SampleManagement.JobProtocol";
    String SAMPLE_MANAGEMENT_TASK_PROTOCOL_PREFIX = "SampleManagement.TaskProtocol";

    // Constant used by ExpDataIterators.AliasDataIterator
    String ALIASCOLUMNALIAS = "org.labkey.experiment.ExpDataIterators$AliasDataIterator#ALIAS";

    String LSID_COUNTER_DB_SEQUENCE_PREFIX = "LsidCounter-";

    String LINEAGE_DEFAULT_MAXIMUM_DEPTH_PROPERTY_NAME = "lineageDefaultMaximumDepth";

    String ILLEGAL_PARENT_ALIAS_CHARSET = "/:<>$[]{};,`\"~!@#$%^*=|?\\";

    String EXPERIMENTAL_FEATURE_FROM_EXPANCESTORS = "org.labkey.api.exp.api.ExperimentService#FROM_EXPANCESTORS";

    String EXPERIMENTAL_FEATURE_ALLOW_ROW_ID_MERGE = "org.labkey.experiment.api.SampleTypeUpdateServiceDI#ALLOW_ROW_ID_SAMPLE_MERGE";

    int SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE = 1;
    int SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE = 10;
    int SIMPLE_PROTOCOL_EXTRA_STEP_SEQUENCE = 15;
    int SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE = 20;

    static ExperimentService get()
    {
        return ServiceRegistry.get().getService(ExperimentService.class);
    }

    static void setInstance(ExperimentService impl)
    {
        ServiceRegistry.get().registerService(ExperimentService.class, impl);
    }

    boolean canMoveFileReference(User user, Container sourceContainer, File sourceFile, int moveCount);

    enum QueryOptions
    {
        UseProvidedLsidForXarImport,
        GetSampleRecomputeCol,
        SkipBulkRemapCache,
        DeferRequiredLineageValidation,
        AddLsidColForDataClassUpdate,
        ShouldCommitRowsBeforeContinuing
    }

    enum DataTypeForExclusion
    {
        SampleType,
        DataClass,
        AssayDesign,
        StorageLocation,
        DashboardSampleType
    }

    @Nullable
    ExpObject findObjectFromLSID(String lsid);

    @Nullable
    ExpRun getExpRun(long rowId);

    @Nullable
    ExpRun getExpRun(long rowId, @Nullable Container container);

    List<? extends ExpRun> getExpRuns(Collection<Long> rowIds);

    @Nullable
    ExpRun getExpRun(String lsid);

    boolean hasExpRuns(Container container, @NotNull Predicate<ExpRun> filterFn);
    /** @return a list of ExpRuns ordered by the RowId */
    List<? extends ExpRun> getExpRuns(Container container, @Nullable ExpProtocol parentProtocol, @Nullable ExpProtocol childProtocol);

    List<? extends ExpRun> getExpRuns(Container container, @Nullable ExpProtocol parentProtocol, @Nullable ExpProtocol childProtocol, @NotNull Predicate<ExpRun> filterFn);

    /**
     * @param filterSQL optional additional WHERE predicates; callers doing keyset pagination should include
     *                  {@code ER.RowId > minRowId} here
     * @param limit     max rows to return; pass {@code Table.ALL_ROWS} (-1) for no limit
     * @return up to {@code limit} ExpRuns in {@code container} matching {@code filterSQL}, ordered by RowId
     */
    List<? extends ExpRun> getExpRuns(@Nullable SQLFragment filterSQL, @NotNull Predicate<ExpRun> filterFn, @NotNull Container container, int limit);

    /**
     * @param modifiedSince optional upper-exclusive Modified cutoff; pass {@code null} to return all batches
     * @param minRowId      keyset cursor — only batches with RowId &gt; minRowId are returned; pass 0 for the first page
     * @param limit         max rows to return
     * @return up to {@code limit} assay batches for {@code batchProtocol} in {@code container} with
     *         RowId &gt; minRowId (and Modified &gt; modifiedSince when non-null), ordered by RowId
     */
    List<? extends ExpExperiment> getExpBatches(@NotNull Container container, @NotNull ExpProtocol batchProtocol, @Nullable Date modifiedSince, long minRowId, int limit);

    List<? extends ExpRun> getExpRunsForJobId(long jobId);

    List<? extends ExpRun> getExpRunsForFilePathRoot(File filePathRoot);

    ExpRun createExperimentRun(Container container, String name);

    @Nullable
    ExpRun createRunForProvenanceRecording(Container container, User user,
                                           RecordedActionSet actionSet,
                                           String runName,
                                           @Nullable Long runJobId) throws ExperimentException, ValidationException;

    void queueSyncRunEdges(long runId);

    void queueSyncRunEdges(ExpRun run);

    void syncRunEdges(long runId);

    void syncRunEdges(ExpRun run);

    void syncRunEdges(Collection<ExpRun> runs);

    @Nullable
    ExpData getExpData(long rowId);

    @Nullable
    ExpData getExpData(String lsid);

    @NotNull
    List<? extends ExpData> getExpDatas(long... rowIds);

    @NotNull
    List<? extends ExpData> getExpDatasByLSID(Collection<String> lsids);

    @NotNull
    List<? extends ExpData> getExpDatas(Collection<Long> rowid);

    @NotNull
    List<? extends ExpData> getExpDatas(@NotNull ExpDataClass dataClass, Collection<Long> rowIds);

    List<? extends ExpData> getExpDatas(Container container, @Nullable DataType type, @Nullable String name);

    /**
     * There are subtle differences between File.toURI() and Path.toUri() so ensure you pick the correct getExpDatasUnderPath to
     * match your use case.
     */
    @NotNull
    List<? extends ExpData> getExpDatasUnderPath(@NotNull File path, @Nullable Container c);

    @NotNull
    List<? extends ExpData> getExpDatasUnderPath(@NotNull Path path, @Nullable Container c, boolean includeExactPath);
    
    /**
     * Get all ExpData that are members of the ExpDataClass.
     */
    List<? extends ExpData> getExpDatas(ExpDataClass dataClass);

    @Nullable
    ExpData getExpData(ExpDataClass dataClass, String name);

    @Nullable
    ExpData getExpData(ExpDataClass dataClass, long rowId);

    /**
     * Build an {@link org.labkey.api.attachments.AttachmentParent} that points at an ExpData's
     * attachment storage. Useful for callers outside the experiment module (for example, module
     * triggers) that need to move, read, or delete ExpData attachments without referencing
     * experiment-internal classes.
     */
    @NotNull
    AttachmentParent getDataClassAttachmentParent(@NotNull Container container, @NotNull String dataLsid);

    /**
     * Get a Data with name at a specific time.
     */
    @Nullable
    ExpData getEffectiveData(@NotNull ExpDataClass dataClass, String name, @NotNull Date effectiveDate, @NotNull Container container, @Nullable ContainerFilter cf);

    /**
     * Create a data object. The object will be unsaved, and will have a name which is a GUID.
     */
    ExpData createData(Container container, @NotNull DataType type);

    ExpData createData(Container container, @NotNull DataType type, @NotNull String name);

    ExpData createData(Container container, @NotNull DataType type, @NotNull String name, boolean generated);

    ExpData createData(Container container, String name, String lsid);

    ExpData createData(URI uri, XarSource source) throws XarFormatException;

    /**
     * Create a new DataClass with the provided domain properties and top level options.
     */
    ExpDataClass createDataClass(
        @NotNull Container c,
        @NotNull User u,
        @NotNull String name,
        @Nullable DataClassDomainKindProperties options,
        List<GWTPropertyDescriptor> properties,
        List<GWTIndex> indices,
        @Nullable TemplateInfo templateInfo,
        @Nullable List<String> disabledSystemField
    ) throws ExperimentException;

    /**
     * Update a DataClass with the provided domain properties and top level options.
     */
    ValidationException updateDataClass(
        @NotNull Container c,
        @NotNull User u,
        @NotNull ExpDataClass dataClass,
        @Nullable DataClassDomainKindProperties options,
        GWTDomain<? extends GWTPropertyDescriptor> original,
        GWTDomain<? extends GWTPropertyDescriptor> update,
        @Nullable String auditUserComment
    );

    void validateDataClassName(@NotNull Container c, @NotNull User u, String name, boolean skipExisting);

    /**
     * Get all DataClass definitions in the container
     */
    List<? extends ExpDataClass> getDataClasses(@NotNull Container container, boolean includeOtherContainers);

    /**
     * Get a DataClass by name within the definition container
     */
    ExpDataClass getDataClass(@NotNull Container definitionContainer, @NotNull String dataClassName);

    /**
     * Get a DataClass by name within scope -- current plus, if <code>includeOtherContainers</code> is true, project and shared
     */
    ExpDataClass getDataClass(@NotNull Container scope, @NotNull String dataClassName, boolean includeProjectAndShared);

    /**
     * Get a DataClass by rowId within the definition container
     */
    ExpDataClass getDataClass(@NotNull Container definitionContainer, long rowId);

    /**
     * Get a DataClass by rowId within scope -- current plus, if <code>includeOtherContainers</code> is true, project and shared
     */
    ExpDataClass getDataClass(@NotNull Container scope, long rowId, boolean includeProjectAndShared);

    /**
     * Get a DataClass by LSID.
     * NOTE: Prefer using one of the getDataClass methods that accept a Container
     */
    @Nullable ExpDataClass getDataClass(@NotNull String lsid);

    /**
     * Get a DataClass by RowId
     * NOTE: Prefer using one of the getDataClass methods that accept a Container
     */
    ExpDataClass getDataClass(long rowId);

    /**
     * Get a DataClass with name at a specific time.
     */
    @Nullable ExpDataClass getEffectiveDataClass(
        @NotNull Container definitionContainer,
        @NotNull String dataClassName,
        @NotNull Date effectiveDate,
        @Nullable ContainerFilter cf
    );

    /**
     * Get materials by rowId in this, project, or shared container and within the provided sample type.
     *
     * @param container       Samples will be found within this container, project, or shared container.
     * @param user            Samples will only be resolved within containers that the user has ReadPermission.
     * @param rowIds          The set of samples rowIds.
     * @param sampleType      Optional sample type that the samples must live in.
     */
    List<? extends ExpMaterial> getExpMaterials(Container container, User user, Collection<Long> rowIds, @Nullable ExpSampleType sampleType);

    /* This version of createExpMaterial() takes name from lsid.getObjectId() */
    @NotNull ExpMaterial createExpMaterial(Container container, Lsid lsid);

    @NotNull ExpMaterial createExpMaterial(Container container, String lsid, String name);

    @Nullable ExpMaterial getExpMaterial(long rowId);

    @Nullable ExpMaterial getExpMaterial(long rowId, ContainerFilter containerFilter);

    /**
     * Get material by rowId in this, project, or shared container and within the provided sample type.
     *
     * @param c       Sample will be found within this container, project, or shared container.
     * @param u            Sample will only be resolved within containers that the user has ReadPermission.
     * @param rowId           The sample rowId.
     * @param sampleType      Optional sample type that the sample must live in.
     */
    @Nullable ExpMaterial getExpMaterial(Container c, User u, long rowId, @Nullable ExpSampleType sampleType);

    @NotNull List<? extends ExpMaterial> getExpMaterials(Collection<Long> rowIds);

    @NotNull List<? extends ExpMaterial> getExpMaterialsByLsid(Collection<String> lsids);

    @Nullable ExpMaterial getExpMaterial(String lsid);

    /**
     * Looks in all the sample types visible from the given container for a single match with the specified name
     */
    @NotNull List<? extends ExpMaterial> getExpMaterialsByName(String name, @Nullable Container container, User user);

    @NotNull List<? extends ExpMaterial> getExpMaterialsByName(@NotNull Collection<String> names, @NotNull String sampleTypeName, @NotNull Container container, User user);

    @Nullable ExpData findExpData(
        Container c,
        User user,
        @NotNull ExpDataClass dataClass,
        @NotNull String dataClassName,
        String dataName,
        RemapCache cache,
        Map<Long, ExpData> dataCache
    ) throws ValidationException;

    /**
     * Attempts to resolve an ExpMaterial from an Object.
     *
     * @param container        The container in which to scope the resolution.
     * @param user             The user that is attempting to resolve the material.
     * @param sampleIdentifier An identifier object (ExpMaterial, Long, String, etc.) that identifies an individual material.
     * @param sampleType       An optional ExpSampleType in which to scope the resolution.
     * @param cache            Caller-supplied remapping cache that remaps underlying queries for samples
     * @param materialCache    Caller-supplied Map used to cache materials by rowId.
     * @return The resolved ExpMaterial or null if the Object does not resolve to a material.
     */
    @Nullable ExpMaterial findExpMaterial(
        Container container,
        User user,
        Object sampleIdentifier,
        @Nullable ExpSampleType sampleType,
        @NotNull RemapCache cache,
        @NotNull Map<Long, ExpMaterial> materialCache
    ) throws ValidationException;

    ExpExperiment createHiddenRunGroup(Container container, User user, ExpRun... runs);

    @NotNull ExpExperiment createExpExperiment(Container container, String name);

    @Nullable ExpExperiment getExpExperiment(long rowId);

    @Nullable ExpExperiment getExpExperiment(String lsid);

    List<? extends ExpExperiment> getExpExperiments(Collection<Long> rowIds);

    List<? extends ExpExperiment> getExperiments(Container container, User user, boolean includeOtherContainers, boolean includeBatches);

    @Nullable ExpProtocol getExpProtocol(long rowId);

    @Nullable ExpProtocol getExpProtocol(String lsid);

    @Nullable ExpProtocol getExpProtocol(Container container, String name);

    ExpProtocol createExpProtocol(Container container, ExpProtocol.ApplicationType type, String name);

    ExpProtocol createExpProtocol(Container container, ExpProtocol.ApplicationType type, String name, String lsid);

    ExpDataProtocolInput createDataProtocolInput(
            @NotNull String name, @NotNull ExpProtocol protocol, boolean input,
            @Nullable ExpDataClass dataClass, @Nullable ExpProtocolInputCriteria criteria,
            int minOccurs, @Nullable Integer maxOccurs);

    ExpMaterialProtocolInput createMaterialProtocolInput(
            @NotNull String name, @NotNull ExpProtocol protocol, boolean input,
            @Nullable ExpSampleType sampleType, @Nullable ExpProtocolInputCriteria criteria,
            int minOccurs, @Nullable Integer maxOccurs);

    @Nullable ExpProtocolInput getProtocolInput(Lsid lsid);

    @Nullable ExpDataProtocolInput getDataProtocolInput(long rowId);
    @Nullable ExpDataProtocolInput getDataProtocolInput(Lsid lsid);
    List<? extends ExpDataProtocolInput> getDataProtocolInputs(long protocolId, boolean input, @Nullable String name, @Nullable Long materialSourceId);

    @Nullable ExpMaterialProtocolInput getMaterialProtocolInput(long rowId);
    @Nullable ExpMaterialProtocolInput getMaterialProtocolInput(Lsid lsid);
    List<? extends ExpMaterialProtocolInput> getMaterialProtocolInputs(long protocolId, boolean input, @Nullable String name, @Nullable Long materialSourceId);

    /**
     * @param type may be null. If non-null, only return roles that are used for that type of application (input, output, or intermediate)
     */
    Set<String> getDataInputRoles(Container container, ContainerFilter containerFilter, ExpProtocol.ApplicationType... type);

    /**
     * @param type may be null. If non-null, only return roles that are used for that type of application (input, output, or intermediate)
     */
    Set<String> getMaterialInputRoles(Container container, User user, ExpProtocol.ApplicationType... type);

    /**
     * Get the DataInput edge between the dataId and the protocolApplication.
     */
    @Nullable ExpDataRunInput getDataInput(long dataId, long targetProtocolApplicationId);

    @Nullable ExpDataRunInput getDataInput(Lsid lsid);

    /**
     * Get the MaterialInput edge between the materialId and the protocolApplication.
     */
    @Nullable ExpMaterialRunInput getMaterialInput(long materialId, long targetProtocolApplicationId);

    @Nullable ExpMaterialRunInput getMaterialInput(Lsid lsid);

    Pair<Set<ExpData>, Set<ExpMaterial>> getParents(Container c, User user, ExpRunItem start);

    Pair<Set<ExpData>, Set<ExpMaterial>> getChildren(Container c, User user, ExpRunItem start);

    static boolean isInputOutputColumn(String columnName)
    {
        if (StringUtils.isBlank(columnName))
            return false;

        String[] parts = columnName.split("[./]");
        if (parts.length == 0) // Issue 52305: if columnName consist of only '.' or '/'
            return false;
        String prefix = parts[0];

        return ExpData.DATA_INPUT_PARENT.equalsIgnoreCase(prefix) ||
               ExpMaterial.MATERIAL_INPUT_PARENT.equalsIgnoreCase(prefix) ||
               ExpData.DATA_OUTPUT_CHILD.equalsIgnoreCase(prefix) ||
               ExpMaterial.MATERIAL_OUTPUT_CHILD.equalsIgnoreCase(prefix);
    }

    static boolean isAliquotedFromColumn(String columnName)
    {
        return ExpMaterial.ALIQUOTED_FROM_INPUT.equalsIgnoreCase(columnName) ||
               ExpMaterial.ALIQUOTED_FROM_INPUT_LABEL.equalsIgnoreCase(columnName);
    }

    // convert MaterialInputs/Blood/Type to MaterialInputs/Blood$SType
    static @Nullable String getEncodedLineageKey(String inputColumn /*not encoded*/)
    {
        if (inputColumn.toLowerCase().startsWith(ExpData.DATA_INPUTS_PREFIX_LC) || inputColumn.toLowerCase().startsWith(ExpMaterial.MATERIAL_INPUTS_PREFIX_LC))
        {
            String[] parts = inputColumn.split("/", 2);
            if (parts.length != 2)
                return null;
            String prefix = parts[0];
            String dataType = parts[1];
            return prefix + "/" + QueryKey.encodePart(dataType);
        }

        return null;
    }

    static @NotNull String[] getParentValues(String valueStr) throws IOException
    {
        if (StringUtils.isEmpty(valueStr.trim()))
            return new String[0];
        try (TabLoader tabLoader = new TabLoader(valueStr))
        {
            tabLoader.setDelimiterCharacter(',');
            // Issue 50924: LKSM: Importing samples using naming expression referencing parent inputs with # result in error
            tabLoader.setIncludeComments(true);
            // Issue 51056 Samples with single double quotes in the name will not resolve if added as parent samples.
            tabLoader.setParseEnclosedQuotes(true);
            String[][] parsedValues = tabLoader.getFirstNLines(1);
            return parsedValues[0];
        }
    }

    static Pair<String, String> parseInputOutputAlias(String columnName)
    {
        if (!isInputOutputColumn(columnName))
            return null;

        String[] parts = columnName.split("[./]");
        if (parts.length < 2)
            return null;

        // Issue 50245: use substring to get second part of the alias instead of relying on parts[]
        String prefix = parts[0];
        String suffix = columnName.substring(prefix.length() + 1); // +1 for the separator
        return Pair.of(prefix, suffix);
    }
    
    static boolean parentAliasHasCorrectFormat(String parentAlias)
    {
        //check if it is of the expected format or targeting the to be created sample type or dataclass
        if (!(ExperimentService.isInputOutputColumn(parentAlias) || NEW_SAMPLE_TYPE_ALIAS_VALUE.equals(parentAlias) || NEW_DATA_CLASS_ALIAS_VALUE.equals(parentAlias)))
            throw new IllegalArgumentException(String.format("Invalid parent alias header: %1$s", parentAlias));

        return true;
    }
    
    static void validateParentAlias(Map<String, String> aliasMap, Set<String> reservedNames, Set<String> existingAliases, GWTDomain updatedDomainDesign, String dataTypeNoun)
    {
        Set<String> dupes = new CaseInsensitiveHashSet();
        aliasMap.forEach((key, value) -> {
            String trimmedKey = StringUtils.trimToNull(key);
            String trimmedValue = StringUtils.trimToNull(value);
            if (trimmedKey == null)
                throw new IllegalArgumentException("Import alias heading cannot be blank");

            if (trimmedValue == null)
            {
                throw new IllegalArgumentException("You must specify a valid parent type for the import alias.");
            }

            if (reservedNames.contains(trimmedKey))
            {
                throw new IllegalArgumentException(String.format("Parent alias header is reserved: %1$s", trimmedKey));
            }

            if (updatedDomainDesign != null)
            {
                var field = updatedDomainDesign.getFieldByName(trimmedKey);
                if (field != null)
                {
                    throw new IllegalArgumentException(String.format("An existing %1$s property conflicts with parent alias header: %2$s", dataTypeNoun, trimmedKey));
                }
                // GH Issue 1257: If there are conflicts with import aliases, this should be an error since it produces ambiguity during import
                field = updatedDomainDesign.getFieldByImportAlias(trimmedKey);
                if (field != null)
                {
                    throw new IllegalArgumentException(String.format("Field '%1$s' has an import alias '%2$s' that conflicts with a parent alias header.", field.getName(), trimmedKey));
                }

            }

            if (!dupes.add(trimmedKey))
            {
                throw new IllegalArgumentException(String.format("Duplicate parent alias header found: %1$s", trimmedKey));
            }

            String legalCharacterCheck = StringUtilsLabKey.validateLegalNames(trimmedKey, ILLEGAL_PARENT_ALIAS_CHARSET, "Parent alias");
            if (legalCharacterCheck != null)
                throw new IllegalArgumentException(legalCharacterCheck);

            String nameSyntaxError = NameGenerator.validateFieldKeyConflict(trimmedKey);
            if (nameSyntaxError != null)
                throw new IllegalArgumentException("Invalid Parent alias '" + trimmedKey + "'. " + nameSyntaxError);

            //Check if parent alias has correct format MaterialInput/<name> or NEW_SAMPLE_TYPE_ALIAS_VALUE, or DataInput/<name> or NEW_DATA_CLASS_ALIAS_VALUE
            if (!ExperimentService.parentAliasHasCorrectFormat(trimmedValue))
                throw new IllegalArgumentException(String.format("Invalid parent alias header: %1$s", trimmedValue));
        });
    }

    /**
     * Find all child and grandchild samples that are direct descendants of <code>start</code> ExpData,
     * ignoring any sample children derived from ExpData children.
     */
    Set<ExpMaterial> getRelatedChildSamples(Container c, User user, ExpData start);

    /**
     * Get the lineage for the seed Identifiable object. Typically, the seed object is an ExpMaterial,
     * an ExpData (in a DataClass), or an ExpRun.
     */
    @NotNull
    ExpLineage getLineage(Container c, User user, @NotNull Identifiable start, @NotNull ExpLineageOptions options);

    @NotNull
    SQLFragment generateExperimentTreeSQL(SQLFragment lsidsFrag, ExpLineageOptions options);

    /**
     * The following methods return TableInfo's suitable for using in queries.
     * These TableInfo's initially have no columns, but have methods to
     * add particular columns as needed by the client.
     */
    ExpRunTable createRunTable(String name, UserSchema schema, ContainerFilter cf);

    /**
     * Create a RunGroupMap junction table joining Runs and RunGroups.
     */
    ExpRunGroupMapTable createRunGroupMapTable(String name, UserSchema schema, ContainerFilter cf);

    ExpDataTable createDataTable(String name, UserSchema schema, ContainerFilter cf);

    ExpDataInputTable createDataInputTable(String name, ExpSchema expSchema, ContainerFilter cf);

    ExpDataProtocolInputTable createDataProtocolInputTable(String name, ExpSchema schema, ContainerFilter cf);

    ExpSampleTypeTable createSampleTypeTable(String name, UserSchema schema, ContainerFilter cf);

    ExpDataClassTable createDataClassTable(String name, UserSchema schema, ContainerFilter cf);

    ExpDataClassDataTable createDataClassDataTable(String name, UserSchema schema, ContainerFilter cf, @NotNull ExpDataClass dataClass);

    /**
     * Registers a factory that creates a {@link DataClassDataIteratorTransformer}
     * for the specified DataClass name. The transformer is applied in the pre-trigger DataIterator pipeline,
     * allowing modules to add computed columns (e.g., transforming flat columns into JSON) that work
     * uniformly for file imports, API imports, folder imports, and background pipeline jobs.
     * A fresh instance is created per import via the factory since transformers may be stateful.
     */
    void registerDataClassDataIteratorTransformer(String dataClassName, @NotNull Supplier<DataClassDataIteratorTransformer> factory);

    /**
     * Returns a fresh {@link DataClassDataIteratorTransformer} for the given
     * DataClass name, or {@code null} if none is registered.
     */
    @Nullable DataClassDataIteratorTransformer getDataClassDataIteratorTransformer(String dataClassName);

    ExpProtocolTable createProtocolTable(String name, UserSchema schema, ContainerFilter cf);

    ExpExperimentTable createExperimentTable(String name, UserSchema schema, ContainerFilter cf);

    ExpMaterialTable createMaterialTable(UserSchema schema, ContainerFilter cf, @Nullable ExpSampleType sampleType);

    ExpMaterialInputTable createMaterialInputTable(String name, ExpSchema expSchema, ContainerFilter cf);

    ExpMaterialProtocolInputTable createMaterialProtocolInputTable(String name, ExpSchema schema, ContainerFilter cf);

    ExpProtocolApplicationTable createProtocolApplicationTable(String name, UserSchema schema, ContainerFilter cf);

    ExpQCFlagTable createQCFlagsTable(String name, UserSchema schema, ContainerFilter cf);

    ExpDataTable createFilesTable(String name, UserSchema schema);

    SampleStatusTable createSampleStatusTable(ExpSchema expSchema, ContainerFilter cf);

    TableInfo createDataColorTable(ExpSchema expSchema, ContainerFilter cf);

    ExpUnreferencedSampleFilesTable createUnreferencedSampleFilesTable(ExpSchema expSchema, ContainerFilter cf);

    FilteredTable<ExpSchema> createFieldsTable(ExpSchema expSchema, ContainerFilter cf);

    FilteredTable<ExpSchema> createPhiFieldsTable(ExpSchema expSchema, ContainerFilter cf);

    String generateLSID(Container container, Class<? extends ExpObject> clazz, String name);

    String generateGuidLSID(Container container, Class<? extends ExpObject> clazz);

    /**
     *
     * @return pair of LSID and DBSeq string
     */
    Pair<String, String> generateLSIDWithDBSeq(@NotNull Container container, Class<? extends ExpObject> clazz);

    String generateLSID(@NotNull Container container, @NotNull DataType type, @NotNull String name);

    String generateGuidLSID(Container container, DataType type);

    Pair<String, String> generateLSIDWithDBSeq(@NotNull Container container, @NotNull DataType type);

    DataType getDataType(String namespacePrefix);

    DbScope.Transaction ensureTransaction(Lock... locks);

    ExperimentRunListView createExperimentRunWebPart(ViewContext context, ExperimentRunType type);

    ExperimentRunListView createExperimentRunWebPart(ViewContext context, ExperimentRunType type, @Nullable String dataRegionName);

    DbSchema getSchema();

    ExpProtocolApplication getExpProtocolApplication(String lsid);

    List<? extends ExpProtocolApplication> getExpProtocolApplicationsForProtocolLSID(String protocolLSID);

    List<? extends ExpData> getExpData(Container c);

    /**
     * Get the <b>most recently</b> created ExpData for the URL, if it exists.
     * @see #getAllExpDataByURL(String, Container)
     */
    ExpData getExpDataByURL(String canonicalURL, @Nullable Container container);

    /**
     * Get the <b>most recently</b> created ExpData for the file, if it exists.
     * @see #getAllExpDataByURL(Path, Container)
     */
    ExpData getExpDataByURL(File f, @Nullable Container c);

    /**
     * Get the <b>most recently</b> created ExpData for the file, if it exists.
     * @see #getAllExpDataByURL(Path, Container)
     */
    ExpData getExpDataByURL(FileLike f, @Nullable Container c);

    /**
     * Get the <b>most recently</b> created ExpData for the path, if it exists.
     * @see #getAllExpDataByURL(Path, Container)
     */
    ExpData getExpDataByURL(Path p, @Nullable Container c);

    /**
     * Get all ExpData for the dataFileUrl. Having more than one ExpData for the same file path doesn't happen often but
     * is allowed. Some examples:
     * - The file or pipeline root may be shared by more than one container and an exp.data may be created in each container when importing assay data.
     * - In the MS2 analysis pipeline, there are tools that rewrite an input file to add more data. We model them as separate exp.data.
     */
    List<? extends ExpData> getAllExpDataByURL(String canonicalURL, @Nullable Container c);
    List<? extends ExpData> getAllExpDataByURL(File f, @Nullable Container c);
    List<? extends ExpData> getAllExpDataByURL(Path p, @Nullable Container c);

    TableInfo getTinfoMaterial();

    TableInfo getTinfoMaterialAncestors();

    TableInfo getTinfoSampleType();

    TableInfo getTinfoProtocol();

    TableInfo getTinfoProtocolApplication();

    TableInfo getTinfoExperiment();

    TableInfo getTinfoExperimentRun();

    TableInfo getTinfoRunList();

    TableInfo getTinfoData();

    TableInfo getTinfoDataClass();

    TableInfo getTinfoMaterialInput();

    TableInfo getTinfoDataInput();

    TableInfo getTinfoDataAncestors();

    TableInfo getTinfoProtocolInput();

    TableInfo getTinfoPropertyDescriptor();

    TableInfo getTinfoAssayQCFlag();

    TableInfo getTinfoAlias();

    TableInfo getTinfoDataAliasMap();

    TableInfo getTinfoMaterialAliasMap();

    TableInfo getTinfoEdge();

    TableInfo getTinfoObjectLegacyNames();

    TableInfo getTinfoDataTypeExclusion();

    /**
     * Get all runs associated with these materials, including the source runs and any derived runs
     * @param materials to get runs for
     * @return List of ExpRun's associated to these materials
     */
    List<? extends ExpRun> getRunsUsingMaterials(List<ExpMaterial> materials);

    /**
     * Get all runs associated with these materials, including the source runs and any derived runs
     * @param materialIds to get runs for
     * @return List of ExpRun's associated to these materials
     */
    List<? extends ExpRun> getRunsUsingMaterials(long... materialIds);

    List<? extends ExpRun> getRunsUsingDatas(List<ExpData> datas);

    List<? extends ExpRun> getRunsUsingDataIds(List<Long> ids);

    ExpRun getCreatingRun(File file, Container c);

    List<? extends ExpRun> getExpRunsForProtocolIds(boolean includeRelated, long... rowIds);

    List<? extends ExpRun> getExpRunsForProtocolIds(boolean includeRelated, @NotNull Collection<Long> rowIds);

    List<? extends ExpRun> getRunsUsingSampleTypes(ExpSampleType... sampleTypes);

    List<? extends ExpRun> getRunsUsingDataClasses(Collection<ExpDataClass> dataClasses);

    /** Get derivation run IDs for a data class — runs with SAMPLE_DERIVATION_PROTOCOL that have no material inputs/outputs */
    List<Long> getDerivationRunIdsForDataClassExport(long dataClassRowId);

    /** Get derivation/aliquot run IDs for sample types — filtered by protocol and optionally excluding runs with data inputs/outputs */
    List<Long> getDerivationRunIdsForSampleTypesExport(Collection<String> sampleTypeLsids, Container c, boolean includeRunsWithDataIO);

    /**
     * @return the subset of these runs which are supposed to be deleted when one of their inputs is deleted.
     */
    List<? extends ExpRun> runsDeletedWithInput(List<? extends ExpRun> runs);

    void deleteAllExpObjInContainer(Container container, User user) throws ExperimentException;

    void deleteExperimentRunsByRowIds(Container container, final User user, long... runRowIds);

    void deleteExperimentRunsByRowIds(Container container, final User user, @Nullable String userComment, @NotNull Collection<Long> runRowIds, @Nullable Map<TransactionAuditProvider.TransactionDetail, Object> transactionDetails);

    void deleteExpExperimentByRowId(Container container, User user, long experimentId);

    void addExperimentListener(ExperimentListener listener);

    void clearCaches();

    void clearExperimentRunCache();

    void invalidateExperimentRun(String lsid);

    List<ProtocolApplicationParameter> getProtocolApplicationParameters(long rowId);

    void moveContainer(Container c, Container cOldParent, Container cNewParent);

    LsidType findType(Lsid lsid);

    Identifiable getObject(Lsid lsid);

    List<? extends ExpData> deleteExperimentRunForMove(long runId, User user);

    /**
     * Kicks off an asynchronous move - a PipelineJob is submitted to the queue to perform the move
     */
    void moveRuns(ViewBackgroundInfo targetInfo, Container sourceContainer, List<ExpRun> runs) throws IOException;

    /**
     * Insert a protocol with optional steps and predecessor configurations.
     *
     * @param baseProtocol the base/top-level protocol to create. Expected to have an ApplicationType and a
     *                     ProtocolParameter value for XarConstants.APPLICATION_LSID_TEMPLATE_URI.
     * @param steps        the protocol steps. Expected to have an ApplicationType and a ProtocolParameter value
     *                     for XarConstants.APPLICATION_LSID_TEMPLATE_URI.
     * @param predecessors Map of Protocol LSIDs to a List of Protocol LSIDs where each entry represents a
     *                     node in the Experiment Protocol graph. If this is not provided the baseProtocol and
     *                     subsequent steps will be organized sequentially.
     * @param user         user with insert permissions
     * @return the saved ExpProtocol
     */
    ExpProtocol insertProtocol(@NotNull ExpProtocol baseProtocol, @Nullable List<ExpProtocol> steps, @Nullable Map<String, List<String>> predecessors, User user) throws ExperimentException;

    ExpProtocol updateProtocol(@NotNull ExpProtocol baseProtocol, @Nullable List<ExpProtocol> steps, @Nullable Map<String, List<String>> predecessors, User user) throws ExperimentException;

    ExpProtocol insertSimpleProtocol(ExpProtocol baseProtocol, User user) throws ExperimentException;

    /**
     * The run must be an instance of a protocol created with insertSimpleProtocol().
     * The run must have at least one input and one output.
     *
     * @param run             ExperimentRun, populated with protocol, name, etc
     * @param inputMaterials  map from input role name to input material
     * @param inputDatas      map from input role name to input data
     * @param outputMaterials map from output role name to output material
     * @param outputDatas     map from output role name to output data
     * @param transformedDatas map of output role name to transformed output data
     * @param info            context information, including the user
     * @param log             output log target
     * @param loadDataFiles   When true, the files associated with <code>inputDatas</code> and <code>transformedDatas</code> will be loaded by their associated data handler.
     */
    ExpRun saveSimpleExperimentRun(
        ExpRun run,
        Map<? extends ExpMaterial, String> inputMaterials,
        Map<? extends ExpData, String> inputDatas,
        Map<ExpMaterial, String> outputMaterials,
        Map<ExpData, String> outputDatas,
        Map<ExpData, String> transformedDatas,
        ViewBackgroundInfo info,
        Logger log,
        boolean loadDataFiles
    ) throws ExperimentException;

    ExpRun saveSimpleExperimentRun(
        ExpRun run,
        Map<? extends ExpMaterial, String> inputMaterials,
        Map<? extends ExpData, String> inputDatas,
        Map<ExpMaterial, String> outputMaterials,
        Map<ExpData, String> outputDatas,
        Map<ExpData, String> transformedDatas,
        ViewBackgroundInfo info,
        Logger log,
        boolean loadDataFiles,
        @Nullable Set<String> runInputLsids,
        @Nullable Set<Pair<String, String>> finalOutputLsids
    ) throws ExperimentException;

    /**
     * Adds an extra protocol application to a run created by saveSimpleExperimentRun() to track more complex
     * workflows.
     *
     * @param expRun run to which the extra should be added
     * @param name   name of the protocol application
     * @return a fully populated but not yet saved ExpProtocolApplication. It will have no inputs and outputs.
     */
    ExpProtocolApplication createSimpleRunExtraProtocolApplication(ExpRun expRun, String name);

    ExpRun deriveSamples(Map<ExpMaterial, String> inputMaterials, Map<ExpMaterial, String> outputMaterials, ViewBackgroundInfo info, Logger log) throws ExperimentException, ValidationException;

    ExpRun derive(
        Map<? extends ExpMaterial, String> inputMaterials,
        Map<? extends ExpData, String> inputDatas,
        Map<ExpMaterial, String> outputMaterials,
        Map<ExpData, String> outputDatas,
        ViewBackgroundInfo info,
        Logger log
    ) throws ExperimentException, ValidationException;

    void deriveSamplesBulk(List<? extends SimpleRunRecord> runRecords, ViewBackgroundInfo info, Logger log) throws ExperimentException, ValidationException;

    void registerExperimentDataHandler(ExperimentDataHandler handler);

    void registerExperimentRunTypeSource(ExperimentRunTypeSource source);

    void registerDataType(DataType type);

    void registerProtocolImplementation(ProtocolImplementation impl);

    void registerProtocolHandler(ExperimentProtocolHandler handler);

    void registerProtocolInputCriteria(ExpProtocolInputCriteria.Factory factory);

    void registerObjectReferencer(ObjectReferencer referencer);

    void registerColumnExporter(ColumnExporter exporter);

    List<ColumnExporter> getColumnExporters();

    @NotNull
    List<ObjectReferencer> getObjectReferencers();

    @NotNull
    String getObjectReferenceDescription(Class<?> referencedClass);

    @Nullable ProtocolImplementation getProtocolImplementation(String name);

    @Nullable ExpProtocolApplication getExpProtocolApplication(long rowId);

    @Nullable ExpProtocolApplication getExpProtocolApplicationFromEntityId(String entityId);

    @NotNull
    List<? extends ExpProtocolApplication> getExpProtocolApplicationsByObjectId(Container container, String objectId);

    List<? extends ExpProtocolApplication> getExpProtocolApplicationsForRun(long runId);

    List<? extends ExpProtocol> getExpProtocols(Container... containers);

    List<? extends ExpProtocol> getAllExpProtocols();

    List<? extends ExpProtocol> getExpProtocolsWithParameterValue(
        @NotNull String parameterURI,
        @NotNull String parameterValue,
        @Nullable Container c,
        @Nullable User user,
        @Nullable ContainerFilter cf
    );

    void registerRunEditor(ExpRunEditor editor);

    @NotNull
    List<ExpRunEditor> getRunEditors();

    /**
     * Kicks off a pipeline job to asynchronously load the XAR from disk
     *
     * @return the job responsible for doing the work
     */
    PipelineJob importXarAsync(ViewBackgroundInfo info, FileLike file, String description, PipeRoot root) throws IOException;

    /**
     * Loads the xar synchronously, in the context of the pipelineJob
     *
     * @return the runs loaded from the XAR
     */
    List<ExpRun> importXar(XarSource source, PipelineJob pipelineJob, boolean reloadExistingRuns) throws ExperimentException;

    List<ExpRun> importXar(XarSource source, PipelineJob pipelineJob, XarImportOptions options) throws ExperimentException;

    /**
     * Create an experiment run to represent the work that the task's job has done so far.
     * The job's recorded actions will be marked as completed after creating the ExpRun so subsequent
     * runs created by the job won't duplicate the previous actions.
     *
     * @param job Pipeline job.
     * @return the run created from the job's actions.
     */
    ExpRun importRun(PipelineJob job, XarSource source) throws PipelineJobException, ValidationException;

    /**
     * Provides access to an object that should be locked before inserting protocols. Locking when doing
     * experiment run insertion has turned out to be problematic and deadlock prone. It's more pragmatic to have
     * the occasional import fail with a SQLException due to duplicate insertions compared with deadlocking the
     * whole server.
     *
     * @return lock object on which to synchronize
     */
    Lock getProtocolImportLock();

    HttpView<?> createRunExportView(Container container, String defaultFilenamePrefix);

    HttpView<?> createFileExportView(Container container, User user, String defaultFilenamePrefix);

    void auditRunEvent(User user, ExpProtocol protocol, ExpRun run, @Nullable ExpExperiment runGroup, String comment);
    void auditRunEvent(User user, ExpProtocol protocol, ExpRun run, @Nullable ExpExperiment runGroup, String comment, String userComment);
    void auditRunEvent(User user, ExpProtocol protocol, ExpRun run, @Nullable ExpExperiment runGroup, String comment, String userComment, @Nullable String message);

    List<? extends ExpExperiment> getMatchingBatches(String name, Container container, ExpProtocol protocol);

    List<? extends ExpProtocol> getExpProtocolsUsedByRuns(Container c, ContainerFilter containerFilter);

    @Nullable
    ExperimentProtocolHandler getExperimentProtocolHandler(@NotNull ExpProtocol protocol);

    @Nullable
    ExperimentRunType getExperimentRunType(@NotNull ExpProtocol protocol);

    @Nullable
    ExperimentRunType getExperimentRunType(@NotNull String description, @Nullable Container container);

    void onBeforeRunSaved(ExpProtocol protocol, ExpRun run, Container container, User user) throws BatchValidationException;

    void onRunDataCreated(ExpProtocol protocol, ExpRun run, Container container, User user) throws BatchValidationException;

    void onMaterialsCreated(List<? extends ExpMaterial> materials, Container container, User user);

    // creates a non-assay backed sample aliquot protocol
    ExpProtocol ensureSampleAliquotProtocol(User user) throws ExperimentException;

    // creates a non-assay backed sample derivation protocol
    ExpProtocol ensureSampleDerivationProtocol(User user) throws ExperimentException;

    // see org.labkey.experiment.LSIDRelativizer
    String LSID_OPTION_ABSOLUTE = "ABSOLUTE";
    String LSID_OPTION_FOLDER_RELATIVE = "FOLDER_RELATIVE";
    String LSID_OPTION_PARTIAL_FOLDER_RELATIVE = "PARTIAL_FOLDER_RELATIVE";

    /**
     * Get the set of runs that can be deleted based on the materials supplied.
     * INCLUDES: Derivative runs, and if only remaining output/derivative the immediate precursor run
     * @param materials Set of materials to get runs for
     * @return runs that can be deleted based on the materials
     */
    List<ExpRun> getDeletableRunsFromMaterials(Collection<? extends ExpMaterial> materials);

    List<String> collectRunsToInvestigate(ExpRunItem start, ExpLineageOptions options);

    SQLFragment generateExperimentTreeSQLLsidSeeds(List<String> lsids, ExpLineageOptions options);

    List<QueryViewProvider<ExpRun>> getRunInputsViewProviders();

    List<QueryViewProvider<ExpRun>> getRunOutputsViewProviders();

    void removeDataTypeExclusion(Collection<Long> rowIds, DataTypeForExclusion dataType);

    void removeContainerDataTypeExclusions(String containerId);

    @NotNull Map<DataTypeForExclusion, Set<Long>> getContainerDataTypeExclusions(@NotNull String excludedContainerId);

    Set<String> getDataTypeContainerExclusions(@NotNull DataTypeForExclusion dataType, @NotNull Long dataTypeRowId);

    void ensureContainerDataTypeExclusions(@NotNull DataTypeForExclusion dataType, @Nullable DataTypeForExclusion relatedDataType, @Nullable Collection<Long> excludedDataTypeRowIds, @NotNull String excludedContainerId, User user);

    /**
     * @return Details about what's changed, to be used in audit log (old -> new)
     */
    @NotNull Pair<Collection<String>, Collection<String>> ensureDataTypeContainerExclusions(@NotNull DataTypeForExclusion dataType, @Nullable Collection<String> excludedContainerIds, @NotNull Long dataTypeId, User user);

    void ensureDataTypeContainerExclusionsNonAdmin(@NotNull DataTypeForExclusion dataType, @NotNull Long dataTypeId, Container container, User user);

    String getDisabledDataTypeAuditMsg(DataTypeForExclusion type, List<Long> ids, boolean isUpdate);

    @NotNull Set<Long> getDataTypeExcludedColors(DataTypeForExclusion dataType, long dataTypeId);

    /** The data type rowIds (e.g. sample type rowIds) that currently exclude the given color. Inverse of {@link #getDataTypeExcludedColors}. */
    @NotNull Set<Long> getDataTypesExcludingColor(DataTypeForExclusion dataType, long colorRowId);

    @NotNull Set<Long> getActiveDataTypeColors(@NotNull Container container, DataTypeForExclusion dataType, long dataTypeId);

    @NotNull List<DataColor> getActiveProjectColors(@NotNull Container container);

    @Nullable DataColor getDataColor(@NotNull Container container, long colorRowId);

    boolean ensureDataColorExclusions(long dataTypeId, DataTypeForExclusion dataType, @Nullable Collection<Long> disabledColorRowIds, @NotNull Container container, User user);

    @NotNull Set<Long> updateColorDataTypeExclusions(long colorRowId, DataTypeForExclusion dataType, @Nullable Collection<Long> newlyDisabledDataTypeIds, @Nullable Collection<Long> newlyEnabledDataTypeIds, @NotNull Container container, User user);

    void removeDataColorExclusionsForColor(long colorRowId);

    void removeDataColorExclusionsForDataType(long dataTypeId, DataTypeForExclusion dataType);

    void removeContainerDataColors(String containerId);

    void registerRunInputsViewProvider(QueryViewProvider<ExpRun> provider);

    void registerRunOutputsViewProvider(QueryViewProvider<ExpRun> providers);

    void addObjectLegacyName(long objectId, String objectType, String legacyName, User user);

    /**
     * @param name          The legacy name of the object
     * @param dataType:     One of "SampleSet", "SampleType", "Material", "Sample", "Data", "DataClass"
     * @param effectiveDate The effective date that the legacy name was active
     * @return The exp.object.rowId with legacy name at the effectiveDate of specified dataType
     */
    Long getObjectIdWithLegacyName(String name, String dataType, Date effectiveDate, Container c, @Nullable ContainerFilter cf);

    /**
     * Persists a collection of lineage relationships (a.k.a. "edges") between experiment objects.
     * Adding edges with a runId is not supported and this method will throw an exception if any run-based edges
     * are supplied. Use experiment protocol inputs/outputs if run support is necessary.
     * @param edges Collection of edges to persist.
     */
    void addEdges(Collection<ExpLineageEdge> edges);

    /**
     * Fetch a collection of lineage relationships (a.k.a. "edges") between experiment objects. The constraints
     * for which edges to fetch is provided via the ExpLineageEdge.FilterOptions parameter. Example:
     *
     * new ExpLineageEdge.FilterOptions().sourceId(42).sourceKey("happy")
     *
     * fetches edges where:
     *
     * sourceId = 42 AND sourceKey = "happy"
     *
     * @param options Filtering options used to constrain the edge's fetched.
     * @return The collection of currently persisted lineage relationships matching the supplied filter options.
     */
    @NotNull
    List<ExpLineageEdge> getEdges(ExpLineageEdge.FilterOptions options);

    /**
     * Removes lineage relationships (a.k.a. "edges") between experiment objects. The constraints for which edges
     * are removed is provided via the ExpLineageEdge.FilterOptions parameter. Example:
     *
     * new ExpLineageEdge.FilterOptions().sourceId(24).sourceKey("cheerful")
     *
     * removes edges where:
     *
     * sourceId = 24 AND sourceKey = "cheerful"
     *
     * @param options Filtering options used to constrain the edge's removed.
     * @return The number of edges removed.
     */
    int removeEdges(ExpLineageEdge.FilterOptions options);

    int updateExpObjectContainers(TableInfo tableInfo, List<Long> rowIds, Container targetContainer);

    int moveExperimentRuns(List<ExpRun> runs, Container targetContainer, User user);

    Map<String, Integer> moveAssayRuns(List<? extends ExpRun> assayRuns, Container container, Container targetContainer, User user, String userComment, AuditBehaviorType auditBehavior) throws ExperimentException;

    int aliasMapRowContainerUpdate(TableInfo aliasMapTable, List<Long> dataIds, Container targetContainer);

    /**
     * From a list of barcodes, find material lsids
     * @param uniqueIds A list of barcodes
     * @return map of barcode and lsid
     */
    @NotNull Map<String, List<String>> getUniqueIdLsids(List<String> uniqueIds, User user, Container container);

    void handleAssayNameChange(String newAssayName, String oldAssayName, AssayProvider provider, ExpProtocol protocol, User user, Container container);

    boolean useStrictCounter();

    /**
     * Returns the lookup ExpSampleType if the property has a lookup to samples.<SampleTypeName> or
     * exp.materials.<SampleTypeName> and is an int or string.
     */
    @Nullable ExpSampleType getLookupSampleType(@NotNull DomainProperty dp, @NotNull Container container, @NotNull User user);

    /** Returns true if the property has a lookup to exp.Materials and is an int or string. */
    boolean isLookupToMaterials(DomainProperty dp);

    void registerNameExpressionType(String dataType, String schemaName, String queryname, String nameExpressionCol);

    Map<String, Map<String, Object>> getDomainMetrics();

    void clearAncestors(ExpRunItem runItem);
    void clearDataAncestors(Collection<Long> dataRowIds);
    void clearMaterialAncestors(Collection<Long> materialRowIds);
    void repopulateAncestors();

    class XarExportOptions
    {
        String _lsidRelativizer = LSID_OPTION_FOLDER_RELATIVE;
        String _xarXmlFileName = "experiment.xar";
        java.io.File _exportFile = null;
        // TODO consider tracking separate sets for input data and output data
        boolean _filterDataRoles = false;
        Set<String> dataRoles = new HashSet<>();
        Logger _log = null;

        public String getLsidRelativizer()
        {
            return _lsidRelativizer;
        }

        public XarExportOptions setLsidRelativizer(String lsidRelativizer)
        {
            _lsidRelativizer = lsidRelativizer;
            return this;
        }

        public String getXarXmlFileName()
        {
            return _xarXmlFileName;
        }

        public XarExportOptions setXarXmlFileName(String xarXmlFileName)
        {
            _xarXmlFileName = xarXmlFileName;
            return this;
        }

        public File getExportFile()
        {
            return _exportFile;
        }

        public XarExportOptions setExportFile(File exportFile)
        {
            _exportFile = exportFile;
            return this;
        }

        public Logger getLog()
        {
            return _log;
        }

        public XarExportOptions setLog(Logger log)
        {
            _log = log;
            return this;
        }

        public boolean isFilterDataRoles()
        {
            return _filterDataRoles;
        }

        public XarExportOptions setFilterDataRoles(boolean filterInputDataRoles)
        {
            _filterDataRoles = filterInputDataRoles;
            return this;
        }

        public Set<String> getDataRoles()
        {
            return dataRoles;
        }

        public XarExportOptions setDataRoles(Set<String> dataRoles)
        {
            this.dataRoles = dataRoles;
            return this;
        }
    }

    class XarImportOptions
    {
        boolean _replaceExistingRuns = false;
        boolean _useOriginalDataFileUrl = false;
        boolean _strictValidateExistingSampleType = true;


        // e.g. delete then re-import

        public boolean isReplaceExistingRuns()
        {
            return _replaceExistingRuns;
        }

        public XarImportOptions setReplaceExistingRuns(boolean b)
        {
            _replaceExistingRuns = b;
            return this;
        }

        // use OriginalFileUrl to find file on disk (probably only useful for internal xar copy/move)

        public boolean isUseOriginalDataFileUrl()
        {
            return _useOriginalDataFileUrl;
        }

        public XarImportOptions setUseOriginalDataFileUrl(boolean useOriginalDataFileUrl)
        {
            _useOriginalDataFileUrl = useOriginalDataFileUrl;
            return this;
        }

        public boolean isStrictValidateExistingSampleType()
        {
            return _strictValidateExistingSampleType;
        }

        public XarImportOptions setStrictValidateExistingSampleType(boolean strictValidateExistingSampleType)
        {
            _strictValidateExistingSampleType = strictValidateExistingSampleType;
            return this;
        }
    }

    @Deprecated // Use IntegerUtils.asLong() instead
    static Long asLong(Object o)
    {
        return IntegerUtils.asLong(o);
    }

    @Deprecated // Use IntegerUtils.asInteger() instead
    static Integer asInteger(Object o)
    {
        return IntegerUtils.asInteger(o);
    }
}
