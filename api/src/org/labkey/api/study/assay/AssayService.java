/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Jul 13, 2007
 */
public interface AssayService
{
    String BATCH_COLUMN_NAME = "Batch";
    String ASSAY_DIR_NAME = "assay";

    static AssayService get()
    {
        return ServiceRegistry.get(AssayService.class);
    }

    static void setInstance(AssayService impl)
    {
        ServiceRegistry.get().registerService(AssayService.class, impl);
    }

    void registerAssayProvider(AssayProvider provider);

    @Nullable
    AssayProvider getProvider(String providerName);

    @Nullable
    AssayProvider getProvider(ExpProtocol protocol);

    @Nullable
    AssayProvider getProvider(ExpRun run);

    @NotNull Collection<AssayProvider> getAssayProviders();

    WebPartView createAssayListView(ViewContext context, boolean portalView, BindException errors);

    ModelAndView createAssayDesignerView(Map<String, String> properties);

    ModelAndView createAssayImportView(Map<String, String> properties);

    ExpRunTable createRunTable(ExpProtocol protocol, AssayProvider provider, User user, Container container);

    AssaySchema createSchema(User user, Container container, @Nullable Container targetStudy);

    /** @return all of the assay protocols that are in scope in the given container */
    @NotNull List<ExpProtocol> getAssayProtocols(Container container);

    /** @return all of the assay protocols that are in scope in the given container, filtered to only include those that are owned by the given provider */
    @NotNull List<ExpProtocol> getAssayProtocols(Container container, @Nullable AssayProvider provider);

    /** @return an assay protocol that matches the given name for the assay protocols that are in scope in the given container */
    @Nullable ExpProtocol getAssayProtocolByName(Container container, String name);

    /**
     * Populates the import button with possible containers
     * @param isStudyView true if this view is from a study, and thus should exclude the current container
     * unless it already has assay data in it
     */
    @NotNull List<ActionButton> getImportButtons(ExpProtocol protocol, User user, Container currentContainer, boolean isStudyView);

    /**
     * Creates a batch object but does not save it to the database
     * @param container location for this batch to live
     * @param name preferred name for the batch. If null, a default name will be assigned. If the name is already in
     */
    ExpExperiment createStandardBatch(Container container, @Nullable String name, ExpProtocol protocol);

    /** Ensures that the batch name is unique within the container. Will add unique numeric suffix until it is. */
    ExpExperiment ensureUniqueBatchName(ExpExperiment batch, ExpProtocol protocol, User user);

    /**
     * @return the batch object for the assay run, if it has one.
     */
    @Nullable
    ExpExperiment findBatch(ExpRun run);

    void indexAssays(SearchService.IndexTask task, Container c);

    /**
     * Creates a run, but does not persist it to the database. Creates the run only, no protocol applications, etc.
     */
    ExpRun createExperimentRun(@Nullable String name, Container container, ExpProtocol protocol, @Nullable File file);

    /**
     * Returns the list of valid locations an assay design can be created in.
     * @return the list of containers as pairs of container objects and corresponding label.
     */
    @NotNull List<Pair<Container, String>> getLocationOptions(Container container, User user);

    /**
     * Searches the ExpRun and ExpBatch for the configured participant visit resolver type.  If none is found,
     * the StudyParticipantVisitResolverType will be used.  If targetStudyContainer is null, the ExpRun
     * and ExpBatch will be searched for the configured TargetStudy.
     *
     * @param run experiment run
     * @param protocol The run's protocol.  If null, the ExpRun.getProcotol() will be used.
     * @param provider The assay provider.  If null, the provider will be found from the protocol.
     * @param targetStudyContainer  The target study.  If null, the ExpRun and ExpBatch properties will be searched.
     * @return The resolver.
     * @throws ExperimentException
     */
    ParticipantVisitResolver createResolver(User user, ExpRun run, @Nullable ExpProtocol protocol, @Nullable AssayProvider provider, @Nullable Container targetStudyContainer)
            throws IOException, ExperimentException;

    void clearProtocolCache();

    /**
     * Register a provider that will add text links to the assay header link display.
     * @param provider the provider that will determine which links to add based on a given ExpProtocol
     */
    void registerAssayHeaderLinkProvider(AssayHeaderLinkProvider provider);

    /**
     * Returns the list of registered providers which can add links to the assay header link listing.
     * @return the list of registered providers
     */
    @NotNull List<AssayHeaderLinkProvider> getAssayHeaderLinkProviders();

    /**
     * Register a renderer to be used on the assay insert form to customize the input field.
     * @param renderer the renderer that will determine the display of the input field based on the column info.
     */
    void registerAssayColumnInfoRenderer(AssayColumnInfoRenderer renderer);

    /**
     * Return the first applicable renderer for the provided parameters.
     * @return AssayColumnInfoRenderer
     */
    AssayColumnInfoRenderer getAssayColumnInfoRenderer(ExpProtocol protocol, ColumnInfo columnInfo, Container container, User user);
}
