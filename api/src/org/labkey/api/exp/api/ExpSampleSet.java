/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.util.StringExpressionFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A collection of {@link ExpMaterial}, with a custom {@link Domain} for additional properties.
 * Material version of {@link ExpDataClass}
 */
public interface ExpSampleSet extends ExpObject
{
    String getMaterialLSIDPrefix();


    /** pass in a container to request a sample */
    @Deprecated
    List<? extends ExpMaterial> getSamples();
    List<? extends ExpMaterial> getSamples(Container c);

    /** pass in a container to request a sample */
    @Deprecated
    ExpMaterial getSample(String name);
    ExpMaterial getSample(Container c, String name);

    @NotNull
    Domain getType();

    String getDescription();

    /**
     * Some sample sets shouldn't be updated through the standard import or derived samples
     * UI, as they don't have any properties. Study specimens are an example.
     */
    boolean canImportMoreSamples();

    /** @return true if either using 'Name' as the Id column or uses at least one property for the unique id column. */
    boolean hasIdColumns();

    /** @return true if using 'Name' as the Id column.  getIdCol1(), getIdCol2() and getIdCol3() will all be null. */
    boolean hasNameAsIdCol();

    /** @return property that determines the first part of the sample set's sample's keys.  Will be null if using 'Name' as the Id column. */
    @Nullable
    DomainProperty getIdCol1();

    /** @return property that determines the second part of the sample set's sample's keys */
    @Nullable
    DomainProperty getIdCol2();

    /** @return property that determines the third part of the sample set's sample's keys */
    @Nullable
    DomainProperty getIdCol3();

    /** @return column that contains parent sample names */
    @Nullable
    DomainProperty getParentCol();

    /** @return name expression if set. */
    @Nullable
    String getNameExpression();

    /** @return true if this SampleSet has a name expression. */
    boolean hasNameExpression();

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
     * @param addUniqueSuffixForDuplicates When a duplicate name is generated, append an incrementing unique suffix starting with ".1"
     * @throws ExperimentException Thrown when a name can't be generated or when a duplicate name is found and both <code>addUniqueSuffixForDuplicates</code> and <code>skipDuplicates</code> are false.
     *
     * @see org.labkey.api.util.SubstitutionFormat
     */
    void createSampleNames(@NotNull List<Map<String, Object>> maps,
                           @Nullable StringExpressionFactory.FieldKeyStringExpression expr,
                           @Nullable Set<ExpData> parentDatas,
                           @Nullable Set<ExpMaterial> parentSamples,
                           boolean skipDuplicates,
                           boolean addUniqueSuffixForDuplicates)
            throws ExperimentException;

    String createSampleName(@NotNull Map<String, Object> rowMap) throws ExperimentException;

    String createSampleName(@NotNull Map<String, Object> rowMap,
                            @Nullable Set<ExpData> parentDatas,
                            @Nullable Set<ExpMaterial> parentSamples) throws ExperimentException;

    void setDescription(String s);

    void setMaterialLSIDPrefix(String s);

    /** @return all of the ID columns. Should be a list of 0-3 elements */
    @NotNull
    List<DomainProperty> getIdCols();

    /**
     * @return LSID that is prepared for use as a material in this Sample Set.
     */
    Lsid.LsidBuilder generateSampleLSID();
}
