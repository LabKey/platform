/*
 * Copyright (c) 2019 LabKey Corporation
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
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.qc.DataState;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.ExperimentalFeatureService;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface SampleTypeService
{
    String NEW_SAMPLE_TYPE_ALIAS_VALUE = "{{this_sample_set}}";
    String MATERIAL_INPUTS_PREFIX = "MaterialInputs/";
    String MODULE_NAME = "Experiment";
    String EXPERIMENTAL_SAMPLE_STATUS = "experimental-sample-status";

    enum SampleOperations {
        EditMetadata("editing metadata"),
        EditLineage("editing lineage"),
        AddToStorage("adding to storage"),
        UpdateStorageMetadata("updating storage metadata"),
        RemoveFromStorage("removing from storage"),
        AddToPicklist("adding to a picklist"),
        RemoveFromPicklist("removing from a picklist"),
        Delete("deleting"),
        AddToWorkflow("adding to a workflow"),
        RemoveFromWorkflow("removing from a workflow"),
        AddAssayData("addition of associated assay data"),
        LinkToStudy("linking to study"),
        RecallFromStudy("recalling from a study");

        private final String _description; // used as a suffix in messaging users about what is not allowed

        SampleOperations(String description)
        {
            _description = description;
        }

        public String getDescription()
        {
            return _description;
        }
    }

    static SampleTypeService get()
    {
        return ServiceRegistry.get().getService(SampleTypeService.class);
    }

    static void setInstance(SampleTypeService impl)
    {
        ServiceRegistry.get().registerService(SampleTypeService.class, impl);
    }

    static boolean isSampleStatusEnabled()
    {
        return ExperimentalFeatureService.get().isFeatureEnabled(EXPERIMENTAL_SAMPLE_STATUS);
    }

    Map<String, ExpSampleType> getSampleTypesForRoles(Container container, ContainerFilter filter, ExpProtocol.ApplicationType type);

    /**
     * Create a new SampleType with the provided properties.
     * If a 'Name' property exists in the list, it will be used as the 'id' property of the SampleType.
     * Either a 'Name' property must exist or at least one idCol index must be provided.
     * A name expression may be provided instead of idCols and will be used to generate the sample names.
     */
    @NotNull
    ExpSampleType createSampleType(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol, String nameExpression)
            throws ExperimentException, SQLException;

    /**
     * (MAB) todo need a builder interface, or at least  parameter bean
     */
    @NotNull
    ExpSampleType createSampleType(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                   String nameExpression, @Nullable TemplateInfo templateInfo)
            throws ExperimentException, SQLException;

    @NotNull
    ExpSampleType createSampleType(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                   String nameExpression, String aliquotNameExpression, @Nullable TemplateInfo templateInfo, @Nullable Map<String, String> importAliases, @Nullable String labelColor, @Nullable String metricUnit,
                                   @Nullable Container autoLinkTargetContainer, @Nullable String autoLinkCategory)
            throws ExperimentException, SQLException;

    @NotNull
    ExpSampleType createSampleType();

    @Nullable
    ExpSampleType getSampleType(int rowId);

    @Nullable
    ExpSampleType getSampleType(String lsid);

    @Nullable
    DataState getSampleState(Container container, Integer stateRowId);

    void removeAutoLinkedStudy(@NotNull Container studyContainer, @Nullable User user);

    /**
     * @param includeOtherContainers whether sample types from the shared container or the container's project should be included
     */
    List<? extends ExpSampleType> getSampleTypes(@NotNull Container container, @Nullable User user, boolean includeOtherContainers);

    /**
     * Get a SampleType by name within the definition container.
     */
    ExpSampleType getSampleType(@NotNull Container definitionContainer, @NotNull String sampleTypeName);

    /**
     * Return the sample type for this LSID, optionally pass Container hint for performance
     */
    ExpSampleType getSampleTypeByType(@NotNull String lsid, Container hint);

    /**
     * Get a SampleType by name within scope -- current, project, and shared.
     * Requires a user to check for container read permission.
     */
    ExpSampleType getSampleType(@NotNull Container scope, @NotNull User user, @NotNull String sampleTypeName);

    /**
     * Get a SampleType by rowId within the definition container.
     */
    ExpSampleType getSampleType(@NotNull Container definitionContainer, int rowId);

    /**
     * Get a SampleType by rowId within scope -- current, project, and shared.
     * Requires a user to check for container read permission.
     */
    ExpSampleType getSampleType(@NotNull Container scope, @NotNull User user, int rowId);

    Lsid getSampleTypeLsid(String name, Container container);

    /**
     * Increment and get the sample counters for the given date, or the current date if no date is supplied.
     * The resulting map has keys "dailySampleCount", "weeklySampleCount", "monthlySampleCount", and "yearlySampleCount".
     * <p>
     * In a loop getSampleCountsFunction() is preferred.
     */
    default Map<String, Long> incrementSampleCounts(@Nullable Date counterDate)
    {
        return getSampleCountsFunction(counterDate).apply(null);
    }

    /**
     * Increment and get the sample counters for the given date, or the current date if no date is supplied.
     * The resulting map has keys "dailySampleCount", "weeklySampleCount", "monthlySampleCount", and "yearlySampleCount".
     *
     * You can pass in a Map<> to reuse, or just pass in null each time. e.g.
     *      once:
     *          fn = getSampleCountsFunction(date);
     *      then:
     *          counts = fn.apply(null);
     */
    Function<Map<String,Long>,Map<String,Long>> getSampleCountsFunction(@Nullable Date counterDate);

    void deleteSampleType(int rowId, Container c, User user) throws ExperimentException;

    // used by DomainKind.invalidate()
    void indexSampleType(ExpSampleType sampleType);

    ValidationException updateSampleType(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update, SampleTypeDomainKindProperties options, Container container, User user, boolean includeWarnings);

    boolean parentAliasHasCorrectFormat(String parentAlias);

    void addAuditEvent(User user, Container container, String comment, ExpMaterial sample, Map<String, Object> metadata);

    void addAuditEvent(User user, Container container, String comment, ExpMaterial sample, Map<String, Object> metadata, String updateType);

    // find the max sequence number with '${sampleName}-' prefix
    long getMaxAliquotId(@NotNull String sampleName, @NotNull String sampleTypeLsid, Container container);

    Collection<? extends ExpMaterial> getSamplesNotPermitted(Collection<? extends ExpMaterial> samples, SampleOperations operation);

    String getOperationNotPermittedMessage(Collection<? extends ExpMaterial> samples, SampleOperations operation);

}
