/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A collection of {@link ExpMaterial}, with a custom {@link Domain} for additional properties.
 * Material version of {@link ExpDataClass}
 */
public interface ExpSampleType extends ExpObject
{
    String SEQUENCE_PREFIX = "org.labkey.experiment.api.MaterialSource";
    String ALIQUOTED_FROM_EXPRESSION = "${AliquotedFrom}";

    String getMaterialLSIDPrefix();


    /** Returns all samples in the given container */
    List<? extends ExpMaterial> getSamples(Container c);

    /** Returns all samples in the given container using the given container filter. */
    List<? extends ExpMaterial> getSamples(Container c, @Nullable ContainerFilter cf);

    /** number of samples in the given container **/
    public long getSamplesCount(Container c, @Nullable ContainerFilter cf);

    /** Returns the sample in the given container with the given name */
    ExpMaterial getSample(Container c, String name);

    /** get the sample with name at a specific time */
    ExpMaterial getEffectiveSample(Container c, String name, Date effectiveDate, @Nullable ContainerFilter cf);

    @NotNull
    Domain getDomain();

    String getDescription();

    /**
     * Some sample types shouldn't be updated through the standard import or derived samples
     * UI, as they don't have any properties. Study specimens are an example.
     */
    boolean canImportMoreSamples();

    /** @return true if either using 'Name' as the Id column or uses at least one property for the unique id column. */
    boolean hasIdColumns();

    /** @return true if using 'Name' as the Id column.  getIdCol1(), getIdCol2() and getIdCol3() will all be null. */
    boolean hasNameAsIdCol();

    /**
     *  Get the property that determines the first part of the sample type's sample's keys.
     *  When 'Name' is being used as the id column, null will be returned.
     *  <b>WARNING:</b> If no idCol1 has been explicitly set, the first domain property is picked as the idCol.
     *  Callers should check {@link #hasIdColumns()} and {@link #hasNameAsIdCol()} before calling this method.
     */
    @Nullable
    @Deprecated
    DomainProperty getIdCol1();

    /** @return property that determines the second part of the sample type's sample's keys */
    @Nullable
    @Deprecated
    DomainProperty getIdCol2();

    /** @return property that determines the third part of the sample type's sample's keys */
    @Nullable
    @Deprecated
    DomainProperty getIdCol3();

    /** @return column that contains parent sample names */
    @Nullable
    @Deprecated //Please use lineage syntax or parent aliases materialSource/parentSampleType/columnName
    DomainProperty getParentCol();

    /** @return name expression if set. */
    @Nullable
    String getNameExpression();

    void setNameExpression(String expression);

    /** @return true if this SampleSet has a name expression. */
    boolean hasNameExpression();

    /** @return aliquot name expression if set. */
    @Nullable
    String getAliquotNameExpression();

    /** @return true if this SampleSet has an override aliquot name expression. */
    boolean hasAliquotNameExpression();

    void setAliquotNameExpression(String expression);

    /** @return label color hex value if set. */
    @Nullable
    String getLabelColor();

    /** @return Metric Unit if set. */
    @Nullable
    String getMetricUnit();

    /** @return Auto link target container if set. */
    @Nullable
    Container getAutoLinkTargetContainer();

    /** @return Auto link dataset category if set. */
    @Nullable
    String getAutoLinkCategory();

    /** @return Category if set. */
    @Nullable
    String getCategory();

    /**
     * Generate sample names for each row map in <code>maps</code> sample group.
     * If a row map already has a non-null value for the "name" key, no sample name will be generated.
     *
     * The name expression will be evaluated in the context of the row map,
     * with the following additional context values:
     * <dl>
     *     <dt>Inputs</dt>
     *     <dd>Contains the names of all ExpData and ExpMaterial parents</dd>
     *
     *     <dt>DataInputs</dt>
     *     <dd>Contains the names of all ExpData parents</dd>
     *
     *     <dt>MaterialInputs</dt>
     *     <dd>Contains the names of all ExpMaterial parents</dd>
     *
     *     <dt>Now</dt>
     *     <dd>The current timestamp</dd>
     *
     *     <dt>BatchRandomId</dt>
     *     <dd>A four digit random number for the entire set of samples</dd>
     *
     *     <dt>RandomId</dt>
     *     <dd>A four digit random number for each sample row</dd>
     * </dl>
     *
     * Examples:
     * <pre>${DataInputs:first:defaultValue('S')}-${now:date('yyyy-MM')}-${batchRandomId}</pre>
     * <pre>${ingredientId/name:defaultValue('S')}-${now:date('yyyy-MM')}-${dailySampleCount}</pre>
     *
     * @param maps The collection of row maps to generate sample names for.  The generated name added to the row map with the "name" key.
     * @param expr The name expression to use when generating sample names, otherwise use the SampleSet's name expression or id columns.
     * @param parentDatas A set of parent data added to the context for each row.  If the row map contains keys starting with "DataInputs", the names will be added to the context under the "Inputs" and "DataInputs" keys.
     * @param parentSamples A set of parent samples added to the context for each row.  If the row map contains keys starting with "MaterialInputs", the names will be added to the context under the "Inputs" and "MaterialInputs" keys.
     * @param skipDuplicates If duplicate names are generated and <code>addUniqueSuffixForDuplicates</code> is false, the row will be removed from the <code>maps</code> collection.
     * @throws ExperimentException Thrown when a name can't be generated or when a duplicate name is found and both <code>addUniqueSuffixForDuplicates</code> and <code>skipDuplicates</code> are false.
     *
     * @see org.labkey.api.util.SubstitutionFormat
     */
    void createSampleNames(@NotNull List<Map<String, Object>> maps,
                           @Nullable StringExpressionFactory.FieldKeyStringExpression expr,
                           @Nullable Set<ExpData> parentDatas,
                           @Nullable Set<ExpMaterial> parentSamples,
                           boolean skipDuplicates)
            throws ExperimentException;

    String createSampleName(@NotNull Map<String, Object> rowMap) throws ExperimentException;

    String createSampleName(@NotNull Map<String, Object> rowMap,
                            @Nullable Set<ExpData> parentDatas,
                            @Nullable Set<ExpMaterial> parentSamples,
                            @Nullable Container container) throws ExperimentException;

    String createSampleName(@NotNull Map<String, Object> rowMap,
                            @Nullable Set<ExpData> parentDatas,
                            @Nullable Set<ExpMaterial> parentSamples,
                            @Nullable Container container,
                            @Nullable User user
    ) throws ExperimentException;

    void setIdCol1(String s);

    void setDescription(String s);

    void setMaterialLSIDPrefix(String s);

    /**
     * Return all of the ID columns.
     * When using 'Name' as the ID column, the returned list will be empty.
     * <b>WARNING:</b> When no ID columns have been explicitly set, the first domain property will be returned.
     * Callers should check {@link #hasIdColumns()} and {@link #hasNameAsIdCol()} before calling this method.
     * @return a list of 0-3 elements.
     */
    @NotNull
    List<DomainProperty> getIdCols();

    /**
     * @return LSID that is prepared for use as a material in this Sample Type.
     */
    Lsid.LsidBuilder generateSampleLSID();

    /**
     * @return LSID using the next DBSeq of the Sample Type.
     */
    Lsid.LsidBuilder generateNextDBSeqLSID();

    /** Override to signal that we never throw BatchValidationExceptions */
    @Override
    void save(User user);

    @NotNull Map<String, String> getImportAliases() throws IOException;

    @NotNull Map<String, String> getRequiredImportAliases() throws IOException;

    @NotNull Map<String, Map<String, Object>> getImportAliasMap() throws IOException;

    void setImportAliasMap(Map<String, Map<String, Object>> aliasMap);

    ActionURL urlEditDefinition(ContainerUser cu);

    Function<String, Long> getMaxSampleCounterFunction();

    void setCategory(String category);

    boolean isMedia();

    long getCurrentGenId();

    void ensureMinGenId(long newSeqValue) throws ExperimentException;

    boolean hasData();
}
