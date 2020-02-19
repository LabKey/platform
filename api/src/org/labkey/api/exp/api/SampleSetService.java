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
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface SampleSetService
{
    String MODULE_NAME = "Experiment";

    static SampleSetService get()
    {
        return ServiceRegistry.get().getService(SampleSetService.class);
    }

    static void setInstance(SampleSetService impl)
    {
        ServiceRegistry.get().registerService(SampleSetService.class, impl);
    }

    Map<String, ExpSampleSet> getSampleSetsForRoles(Container container, ContainerFilter filter, ExpProtocol.ApplicationType type);

    /**
     * Create a new SampleSet with the provided properties.
     * If a 'Name' property exists in the list, it will be used as the 'id' property of the SampleSet.
     * Either a 'Name' property must exist or at least one idCol index must be provided.
     * A name expression may be provided instead of idCols and will be used to generate the sample names.
     */
    @NotNull
    ExpSampleSet createSampleSet(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol, String nameExpression)
            throws ExperimentException, SQLException;

    /**
     * (MAB) todo need a builder interface, or at least  parameter bean
     */
    @NotNull
    ExpSampleSet createSampleSet(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                 String nameExpression, @Nullable TemplateInfo templateInfo)
            throws ExperimentException, SQLException;

    @NotNull
    ExpSampleSet createSampleSet(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                 String nameExpression, @Nullable TemplateInfo templateInfo, Map<String, String> importAliases)
            throws ExperimentException, SQLException;

    @NotNull
    ExpSampleSet createSampleSet();

    @Nullable
    ExpSampleSet getSampleSet(int rowId);

    @Nullable
    ExpSampleSet getSampleSet(String lsid);

    /**
     * @param includeOtherContainers whether sample sets from the shared container or the container's project should be included
     */
    List<? extends ExpSampleSet> getSampleSets(@NotNull Container container, @Nullable User user, boolean includeOtherContainers);

    /**
     * Get a SampleSet by name within the definition container.
     */
    ExpSampleSet getSampleSet(@NotNull Container definitionContainer, @NotNull String sampleSetName);

    /**
     * Return the sampleset for this LSID, optionally pass Container hint for performance
     */
    ExpSampleSet getSampleSetByType(@NotNull String lsid, Container hint);

    /**
     * Get a SampleSet by name within scope -- current, project, and shared.
     * Requires a user to check for container read permission.
     */
    ExpSampleSet getSampleSet(@NotNull Container scope, @NotNull User user, @NotNull String sampleSetName);

    /**
     * Get a SampleSet by rowId within the definition container.
     */
    ExpSampleSet getSampleSet(@NotNull Container definitionContainer, int rowId);

    /**
     * Get a SampleSet by rowId within scope -- current, project, and shared.
     * Requires a user to check for container read permission.
     */
    ExpSampleSet getSampleSet(@NotNull Container scope, @NotNull User user, int rowId);

    String getDefaultSampleSetLsid();
    String getDefaultSampleSetMaterialLsidPrefix();

    Lsid getSampleSetLsid(String name, Container container);

    /**
     * Increment and get the sample counters for the given date, or the current date if no date is supplied.
     * The resulting map has keys "dailySampleCount", "weeklySampleCount", "monthlySampleCount", and "yearlySampleCount".
     */
    Map<String, Long> incrementSampleCounts(@Nullable Date counterDate);

    void deleteSampleSet(int rowId, Container c, User user) throws ExperimentException;

    // used by DomainKind.invalidate()
    void indexSampleSet(ExpSampleSet sampleSet);

    ValidationException updateSampleSet(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update, SampleTypeDomainKindProperties options, Container container, User user, boolean includeWarnings);
}
