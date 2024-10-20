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

package org.labkey.api.assay;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExpQCFlag;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.ValidationError;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.assay.ParticipantVisitResolver;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface AssayService
{
    String BATCH_COLUMN_NAME = "Batch";
    String ASSAY_DIR_NAME = "assay";

    static AssayService get()
    {
        return ServiceRegistry.get().getService(AssayService.class);
    }

    static void setInstance(AssayService impl)
    {
        ServiceRegistry.get().registerService(AssayService.class, impl);
    }

    void registerAssayProvider(AssayProvider provider);

    void registerAssayListener(AssayListener listener);

    @Nullable
    AssayProvider getProvider(String providerName);

    @Nullable
    AssayProvider getProvider(@Nullable ExpProtocol protocol);

    @Nullable
    AssayProvider getProvider(ExpRun run);

    @NotNull Collection<AssayProvider> getAssayProviders();

    WebPartView createAssayListView(ViewContext context, boolean portalView, BindException errors);

    ExpRunTable createRunTable(ExpProtocol protocol, AssayProvider provider, User user, Container container, ContainerFilter cf);

    AssaySchema createSchema(User user, Container container, @Nullable Container targetStudy);

    /**
     * @return all the assay protocols that are in scope in the given container
     */
    @NotNull List<ExpProtocol> getAssayProtocols(Container container);

    /**
     * @return all the assay protocols that are in scope in the given container, filtered to only include those that are owned by the given provider
     */
    @NotNull List<ExpProtocol> getAssayProtocols(Container container, @Nullable AssayProvider provider);

    /**
     * @return an assay protocol that matches the given name for the assay protocols that are in scope in the given container
     */
    @Nullable ExpProtocol getAssayProtocolByName(Container container, String name);

    /**
     * Populates the import button with possible containers
     *
     * @param isStudyView true if this view is from a study, and thus should exclude the current container
     *                    unless it already has assay data in it
     */
    @NotNull List<ActionButton> getImportButtons(ExpProtocol protocol, User user, Container currentContainer, boolean isStudyView);

    @NotNull
    Set<ClientDependency> getClientDependenciesForImportButtons();

    /**
     * Creates a batch object but does not save it to the database
     *
     * @param container location for this batch to live
     * @param name      preferred name for the batch. If null, a default name will be assigned. If the name is already in
     */
    ExpExperiment createStandardBatch(Container container, @Nullable String name, ExpProtocol protocol);

    /**
     * Ensures that the batch name is unique within the container. Will add unique numeric suffix until it is.
     */
    ExpExperiment ensureUniqueBatchName(ExpExperiment batch, ExpProtocol protocol, User user);

    /**
     * @return the batch object for the assay run, if it has one.
     */
    @Nullable
    ExpExperiment findBatch(ExpRun run);

    void indexAssay(SearchService.IndexTask task, Container c, ExpProtocol protocol);
    void indexAssays(SearchService.IndexTask task, Container c);

    void deindexAssays(@NotNull Collection<? extends ExpProtocol> expProtocols);

    /**
     * Creates a run, but does not persist it to the database. Creates the run only, no protocol applications, etc.
     */
    ExpRun createExperimentRun(@Nullable String name, Container container, ExpProtocol protocol, @Nullable File file);

    /**
     * Returns the list of valid locations an assay design can be created in.
     *
     * @return the list of containers as pairs of container objects and corresponding label.
     */
    @NotNull List<Pair<Container, String>> getLocationOptions(Container container, User user);

    /**
     * Searches the ExpRun and ExpBatch for the configured participant visit resolver type.  If none is found,
     * the StudyParticipantVisitResolverType will be used.  If targetStudyContainer is null, the ExpRun
     * and ExpBatch will be searched for the configured TargetStudy.
     *
     * @param run                  experiment run
     * @param protocol             The run's protocol.  If null, the ExpRun.getProtocol() will be used.
     * @param provider             The assay provider.  If null, the provider will be found from the protocol.
     * @param targetStudyContainer The target study.  If null, the ExpRun and ExpBatch properties will be searched.
     * @return The resolver.
     */
    ParticipantVisitResolver createResolver(User user, ExpRun run, @Nullable ExpProtocol protocol, @Nullable AssayProvider provider, @Nullable Container targetStudyContainer)
            throws IOException, ExperimentException;

    void clearProtocolCache();

    /**
     * Register a provider that will add text links to the assay header link display.
     *
     * @param provider the provider that will determine which links to add based on a given ExpProtocol
     */
    void registerAssayHeaderLinkProvider(AssayHeaderLinkProvider provider);

    /**
     * Returns the list of registered providers which can add links to the assay header link listing.
     *
     * @return the list of registered providers
     */
    @NotNull List<AssayHeaderLinkProvider> getAssayHeaderLinkProviders();

    /**
     * Register a provider that will add text links to the assay results header link display.
     *
     * @param provider the provider that will determine which links to add based on a given ExpProtocol
     */
    void registerAssayResultsHeaderProvider(AssayResultsHeaderProvider provider);

    /**
     * Returns the list of registered providers which can add links to the assay results header link listing.
     *
     * @return the list of registered providers
     */
    @NotNull List<AssayResultsHeaderProvider> getAssayResultsHeaderProviders();

    /**
     * Register a renderer to be used on the assay insert form to customize the input field.
     *
     * @param renderer the renderer that will determine the display of the input field based on the column info.
     */
    void registerAssayColumnInfoRenderer(AssayColumnInfoRenderer renderer);

    /**
     * Return the first applicable renderer for the provided parameters.
     *
     * @return AssayColumnInfoRenderer
     */
    AssayColumnInfoRenderer getAssayColumnInfoRenderer(ExpProtocol protocol, ColumnInfo columnInfo, Container container, User user);

    /**
     * Saves a ExpQCFlag instance for the specified run.
     * The assay provider must implement an instance of the AssayFlagHandler interface
     */
    <FlagType extends ExpQCFlag> void saveFlag(Container container, User user, AssayProvider provider, FlagType flag);

    /**
     * Delete the flags for the run.
     * The assay provider must implement an instance of the AssayFlagHandler interface
     *
     * @return the count of flags deleted
     */
    int deleteFlagsForRun(Container container, User user, AssayProvider provider, int runId);

    /**
     * Delete the specified flag.
     * The assay provider must implement an instance of the AssayFlagHandler interface
     */
    <FlagType extends ExpQCFlag> void deleteFlag(Container container, User user, AssayProvider provider, FlagType flag);

    /**
     * Returns the flags for the specified run.
     * The assay provider must implement an instance of the AssayFlagHandler interface
     */
    <FlagType extends ExpQCFlag> List<FlagType> getFlags(AssayProvider provider, int runId, Class<FlagType> cls);

    /**
     * Returns the TableInfo for the given assay domain based on the assay domain ID.
     */
    TableInfo getTableInfoForDomainId(User user, Container container, int domainId, @Nullable ContainerFilter cf);

    void onBeforeAssayResultDelete(Container container, User user, ExpRun run, Map<String, Object> resultRow);

    /**
     * Provides a mechanism to check or validate the results for a specified protocol. An instance of ResultsCheckHelper
     * is used to generate the SQL used during the validation of protocol results.
     */
    @NotNull Collection<Map<String, Object>> checkResults(Container container, User user, ExpProtocol protocol, ResultsCheckHelper checker);
    @NotNull <E> Collection<E> checkResults(Container container, User user, ExpProtocol protocol, ResultsCheckHelper checker, Class<E> clazz);

    /** Returns the lineage "role" for an assay run/result property. */
    @NotNull String getPropertyInputLineageRole(@NotNull DomainProperty dp);

    interface ResultsCheckHelper
    {
        @NotNull Logger getLogger();

        /**
         * Checks whether the results table is valid, has the required fields etc. If errors are returned
         * the validation will not run and the errors will be logged.
         */
        @NotNull List<ValidationError> isValid(ExpProtocol protocol, TableInfo dataTable);

        /**
         * The SQLFragment to execute to run the check, the SQL should return the expected results returned to the checkResults
         * function.
         */
        @Nullable SQLFragment getValidationSql(Container container, User user, ExpProtocol protocol, TableInfo dataTable);

        /**
         * The container filter to use during construction of the assay results table
         */
        @Nullable ContainerFilter getContainerFilter();
    }
}
