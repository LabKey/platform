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

package org.labkey.assay;

import gwt.client.org.labkey.assay.AssayApplication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.assay.AssayColumnInfoRenderer;
import org.labkey.api.assay.AssayFlagHandler;
import org.labkey.api.assay.AssayHeaderLinkProvider;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayResultsHeaderProvider;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExpQCFlag;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.LsidManager.ExpRunLsidHandler;
import org.labkey.api.exp.LsidManager.LsidHandler;
import org.labkey.api.exp.LsidManager.LsidHandlerFinder;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineService.PipelineProviderSupplier;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AssayReadPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.assay.ParticipantVisitResolver;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.assay.StudyParticipantVisitResolverType;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.assay.ModuleAssayCache.ModuleAssayCollections;
import org.labkey.assay.query.AssaySchemaImpl;
import org.labkey.assay.view.AssayGWTView;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * User: brittp
 * Date: Jun 22, 2007
 * Time: 4:21:59 PM
 */
public class AssayManager implements AssayService
{
    public static final SearchService.SearchCategory ASSAY_CATEGORY = new SearchService.SearchCategory("assay", "Study Assay") {
        @Override
        public Set<String> getPermittedContainerIds(User user, Map<String, Container> containers)
        {
            return getPermittedContainerIds(user, containers, AssayReadPermission.class);
        }
    };

    public static final String EXPERIMENTAL_ASSAY_DATA_IMPORT = "experimental-uxassaydataimport";

    private static final Cache<Container, List<ExpProtocol>> PROTOCOL_CACHE = CacheManager.getCache(CacheManager.UNLIMITED, TimeUnit.HOURS.toMillis(1), "AssayProtocols");

    private final List<AssayProvider> _providers = new CopyOnWriteArrayList<>();
    private final List<AssayHeaderLinkProvider> _headerLinkProviders = new CopyOnWriteArrayList<>();
    private final List<AssayResultsHeaderProvider> _resultsHeaderLinkProviders = new CopyOnWriteArrayList<>();
    private final List<AssayColumnInfoRenderer> _assayColumnInfoRenderers = new CopyOnWriteArrayList<>();

    /**
     * Synchronization lock object for ensuring that batch names are unique
     */
    private static final Object BATCH_NAME_LOCK = new Object();

    public AssayManager()
    {
        LsidManager.get().registerHandlerFinder(new ModuleAssayLsidHandlerFinder());
        PipelineService.get().registerPipelineProviderSupplier(new ModuleAssayPipelineProviderSupplier());
    }

    public static AssayManager get()
    {
        return (AssayManager) AssayService.get();
    }

    public ExpProtocol createAssayDefinition(User user, Container container, GWTProtocol newProtocol) throws ExperimentException
    {
        ExpProtocol.Status status = ExpProtocol.Status.Active;
        if (newProtocol.getStatus() != null)
            status = ExpProtocol.Status.valueOf(newProtocol.getStatus());

        return getProvider(newProtocol.getProviderName()).createAssayDefinition(user, container, newProtocol.getName(),
                newProtocol.getDescription(), status);
    }

    @Override
    public void registerAssayProvider(AssayProvider provider)
    {
        // Blow up if we've already added a provider with this name
        verifyLegalName(provider);
        if (getProvider(provider.getName(), _providers) != null) // Checks against all registered providers (so far)
        {
            throw new IllegalArgumentException("A provider with the name " + provider.getName() + " has already been registered");
        }
        _providers.add(provider);
        PipelineProvider pipelineProvider = provider.getPipelineProvider();
        if (pipelineProvider != null)
        {
            PipelineService.get().registerPipelineProvider(pipelineProvider);
        }
        provider.registerLsidHandler();
    }

    void verifyLegalName(AssayProvider provider)
    {
        if (AssaySchema.ASSAY_LIST_TABLE_NAME.equalsIgnoreCase(provider.getName()) || AssaySchema.ASSAY_LIST_TABLE_NAME.equalsIgnoreCase(provider.getResourceName()))
        {
            throw new IllegalArgumentException("'" + AssaySchema.ASSAY_LIST_TABLE_NAME + "' is not allowed as an AssayProvider name because it conflicts with the built-in query");
        }
    }

    @Override
    @Nullable
    public AssayProvider getProvider(String providerName)
    {
        return getProvider(providerName, getAssayProviders());
    }

    @Nullable AssayProvider getProvider(String providerName, Collection<AssayProvider> providers)
    {
        for (AssayProvider potential : providers)
        {
            if (potential.getName().equals(providerName) || potential.getResourceName().equals(providerName))
            {
                return potential;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public AssayProvider getProvider(ExpProtocol protocol)
    {
        return null == protocol ? null : Handler.Priority.findBestHandler(getAssayProviders(), protocol);
    }

    @Override
    @Nullable
    public AssayProvider getProvider(ExpRun run)
    {
        ExpProtocol protocol = run.getProtocol();
        if (protocol == null)
        {
            return null;
        }
        return getProvider(protocol);
    }

    @Override
    @NotNull
    public Collection<AssayProvider> getAssayProviders()
    {
        // Add the statically registered providers first
        List<AssayProvider> ret = new LinkedList<>(_providers);

        // Now add the module assay providers
        ret.addAll(getModuleAssayCollections().getAssayProviders());

        return Collections.unmodifiableCollection(ret);
    }

    private ModuleAssayCollections getModuleAssayCollections()
    {
        return ModuleAssayCache.get().getModuleAssayCollections();
    }

    private class ModuleAssayPipelineProviderSupplier implements PipelineProviderSupplier
    {
        @NotNull
        @Override
        public Collection<PipelineProvider> getAll()
        {
            return getModuleAssayCollections().getPipelineProviders().values();
        }

        @Nullable
        @Override
        public PipelineProvider findPipelineProvider(String name)
        {
            return getModuleAssayCollections().getPipelineProviders().get(name);
        }
    }

    // Module assays always use the default LSID authority and return a singleton LsidHandler. This matches previous
    // behavior of the static registration approach.
    private class ModuleAssayLsidHandlerFinder implements LsidHandlerFinder
    {
        // ExpRunLsidHandler has no state, so safe to use a singleton.
        private final ExpRunLsidHandler _fileBasedAssayLsidHandler = new ExpRunLsidHandler();

        @Nullable
        @Override
        public LsidHandler findHandler(String authority, String namespacePrefix)
        {
            if (AppProps.getInstance().getDefaultLsidAuthority().equals(authority))
            {
                if (getModuleAssayCollections().getRunLsidPrefixes().contains(namespacePrefix))
                    return _fileBasedAssayLsidHandler;

                AssayProvider provider = getModuleAssayCollections().getResultLsidPrefixes().get(namespacePrefix);
                if (provider != null)
                    return new LsidManager.AssayResultLsidHandler(provider);
            }

            return null;
        }
    }

    @Override
    public void registerAssayHeaderLinkProvider(AssayHeaderLinkProvider provider)
    {
        _headerLinkProviders.add(provider);
    }

    @Override
    public @NotNull List<AssayHeaderLinkProvider> getAssayHeaderLinkProviders()
    {
        return Collections.unmodifiableList(_headerLinkProviders);
    }

    @Override
    public void registerAssayResultsHeaderProvider(AssayResultsHeaderProvider provider)
    {
        _resultsHeaderLinkProviders.add(provider);
    }

    @Override
    public @NotNull List<AssayResultsHeaderProvider> getAssayResultsHeaderProviders()
    {
        return Collections.unmodifiableList(_resultsHeaderLinkProviders);
    }


    @Override
    public void registerAssayColumnInfoRenderer(AssayColumnInfoRenderer renderer)
    {
        _assayColumnInfoRenderers.add(renderer);
    }

    @Override
    public AssayColumnInfoRenderer getAssayColumnInfoRenderer(ExpProtocol protocol, ColumnInfo columnInfo, Container container, User user)
    {
        for (AssayColumnInfoRenderer renderer : _assayColumnInfoRenderers)
        {
            if (renderer.isApplicable(protocol, columnInfo, container, user))
                return renderer;
        }

        return null;
    }

    @Override
    public ExpRunTable createRunTable(ExpProtocol protocol, AssayProvider provider, User user, Container container, ContainerFilter cf)
    {
        return provider.createProtocolSchema(user, container, protocol, null).createRunsTable(cf);
    }

    @Override
    public AssaySchema createSchema(User user, Container container, @Nullable Container targetStudy)
    {
        return new AssaySchemaImpl(user, container, targetStudy);
    }

    @Override
    public @NotNull List<ExpProtocol> getAssayProtocols(Container container)
    {
        return PROTOCOL_CACHE.get(container, null, (c, argument) ->
        {
            // Build up a set of containers so that we can query them all at once
            Set<Container> containers = c.getContainersFor(ContainerType.DataType.protocol);

            List<? extends ExpProtocol> protocols = ExperimentService.get().getExpProtocols(containers.toArray(new Container[containers.size()]));
            List<ExpProtocol> result = new ArrayList<>();

            // Filter to just the ones that have an AssayProvider associated with them
            for (ExpProtocol protocol : protocols)
            {
                AssayProvider p = AssayService.get().getProvider(protocol);
                if (p != null)
                {
                    // We don't want anyone editing our cached object
                    protocol.lock();
                    result.add(protocol);
                }
            }
            // Sort them, just to be nice
            Collections.sort(result);
            return Collections.unmodifiableList(result);
        });
    }

    @Override
    public @NotNull List<ExpProtocol> getAssayProtocols(Container container, @Nullable AssayProvider provider)
    {
        // Take the full list
        List<ExpProtocol> allProtocols = getAssayProtocols(container);
        if (provider == null)
        {
            return allProtocols;
        }

        // Filter it down to just ones that match the provider
        List<ExpProtocol> result = new ArrayList<>();
        for (ExpProtocol p : allProtocols)
        {
            if (provider.equals(AssayService.get().getProvider(p)))
            {
                result.add(p);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public @Nullable ExpProtocol getAssayProtocolByName(Container container, String name)
    {
        if (name != null)
        {
            List<ExpProtocol> allProtocols = getAssayProtocols(container);
            for (ExpProtocol protocol : allProtocols)
            {
                if (name.equals(protocol.getName()))
                    return protocol;
            }
        }
        return null;
    }

    public TableInfo getTableInfoForDomainId(User user, Container container, int domainId, @Nullable ContainerFilter cf) {
        for (ExpProtocol protocol : getAssayProtocols(container))
        {
            AssayProvider provider = getProvider(protocol);
            AssayProtocolSchema schema = provider.createProtocolSchema(user, container, protocol, null);
            for (String tableName : schema.getTableNames())
            {
                TableInfo table = schema.getTable(tableName, cf, true, true);
                if (table != null && table.getDomain() != null && table.getDomain().getTypeId() == domainId)
                    return table;
            }
        }

        return null;
    }

    @Override
    public WebPartView createAssayListView(ViewContext context, boolean portalView, BindException errors)
    {
        String name = AssaySchema.ASSAY_LIST_TABLE_NAME;
        UserSchema schema = AssayService.get().createSchema(context.getUser(), context.getContainer(), null);
        QuerySettings settings = schema.getSettings(context, name, name);
        QueryView queryView;
        if (portalView)
            queryView = new AssayListPortalView(context, settings, errors);
        else
            queryView = new AssayListQueryView(context, settings, errors);

        VBox vbox = new VBox();
        if (portalView)
        {
            vbox.setFrame(WebPartView.FrameType.PORTAL);

            NavTree menu = new NavTree();
            createAssayDataImportButton(context, menu);
            if (context.getContainer().hasPermission(context.getUser(), DesignAssayPermission.class))
            {
                ActionURL insertURL = PageFlowUtil.urlProvider(AssayUrls.class).getChooseAssayTypeURL(context.getContainer());
                insertURL.addReturnURL(context.getActionURL());
                menu.addChild("New Assay Design", insertURL);
            }
            menu.addChild("Manage Assays", PageFlowUtil.urlProvider(AssayUrls.class).getBeginURL(context.getContainer()));
            vbox.setNavMenu(menu);
        }

        vbox.addView(new JspView("/org/labkey/assay/view/assaySetup.jsp"));
        vbox.addView(queryView);
        return vbox;
    }

    @Override
    public ModelAndView createAssayImportView(Map<String, String> properties)
    {
        return new AssayGWTView(new AssayApplication.AssayImporter(), properties);
    }

    @Override
    @NotNull
    public Set<ClientDependency> getClientDependenciesForImportButtons()
    {
        return PageFlowUtil.set(ClientDependency.fromPath("Ext4ClientApi"), ClientDependency.fromPath("extWidgets/ImportWizard.js"));
    }

    @Override
    public @NotNull List<ActionButton> getImportButtons(ExpProtocol protocol, User user, Container currentContainer, boolean isStudyView)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        assert provider != null : "Could not find a provider for protocol: " + protocol;

        // First find all the containers that have contributed data to this protocol
        Set<Container> containers = protocol.getExpRunContainers();

        Container protocolContainer = protocol.getContainer();

        // Always add the current container if we're looking at an assay and under the protocol
        if (!isStudyView &&
                (currentContainer.equals(protocolContainer) ||
                        currentContainer.hasAncestor(protocolContainer) ||
                        protocolContainer.equals(ContainerManager.getSharedContainer())))
            containers.add(currentContainer);


        // Check for write permission
        for (Iterator<Container> iter = containers.iterator(); iter.hasNext(); )
        {
            Container container = iter.next();
            boolean hasPermission = container.hasPermission(user, InsertPermission.class);
            boolean hasPipeline = PipelineService.get().hasValidPipelineRoot(container);
            // Issue 16948: Don't show peer or parent containers in the drop down. Users should navigate to them directly
            // if they want to upload there
            boolean isCurrentOrDescendant = container.equals(currentContainer) || container.isDescendant(currentContainer);
            if (!hasPermission || !hasPipeline || !isCurrentOrDescendant)
            {
                iter.remove();
            }
        }
        if (containers.isEmpty())
            return Collections.emptyList(); // Nowhere to upload to, no button

        List<ActionButton> result = new ArrayList<>();


        if (currentContainer.isWorkbook())
        {
            ActionButton button = new ActionButton(provider.getImportURL(currentContainer, protocol), AbstractAssayProvider.IMPORT_DATA_LINK_NAME);
            button.setActionType(ActionButton.Action.LINK);
            result.add(button);
        }

        //this is the previous assay btn
        else if (!currentContainer.getFolderType().getForceAssayUploadIntoWorkbooks())
        {
            if (containers.size() == 1 && containers.iterator().next().equals(currentContainer))
            {
                // Create one import button for each provider, using the current container
                ActionButton button = new ActionButton(provider.getImportURL(currentContainer, protocol), AbstractAssayProvider.IMPORT_DATA_LINK_NAME);
                button.setActionType(ActionButton.Action.LINK);
                result.add(button);
            }
            else
            {
                // It's not just the current container, so fall through to show a submenu even if there's
                // only one item, in order to indicate that the user is going to be redirected elsewhere
                MenuButton uploadButton = new MenuButton(AbstractAssayProvider.IMPORT_DATA_LINK_NAME);
                // If the current folder is in our list, put it first.
                if (containers.contains(currentContainer))
                {
                    containers.remove(currentContainer);
                    ActionURL url = provider.getImportURL(currentContainer, protocol);
                    uploadButton.addMenuItem("Current " + currentContainer.getContainerNoun(true) + " (" + currentContainer.getTitleFor(ContainerType.TitleContext.importTarget) + ")", url);
                }
                for (Container container : containers)
                {
                    ActionURL url = provider.getImportURL(container, protocol);
                    uploadButton.addMenuItem(container.getPath(), url);
                }
                result.add(uploadButton);
            }
        }

        else
        {
            ActionButton button = new ActionButton(AbstractAssayProvider.IMPORT_DATA_LINK_NAME);
            button.setURL("javascript:void(0)");
            button.setActionType(ActionButton.Action.SCRIPT);
            button.setScript("Ext4.create('LABKEY.ext.ImportWizardWin', {" +
                    "controller: '" + provider.getImportURL(currentContainer, protocol).getController() + "'," +
                    "action: '" + provider.getImportURL(currentContainer, protocol).getAction() + "'," +
                    "urlParams: {rowId: " + protocol.getRowId() + "}" +
                    "}).show();");
            result.add(button);

        }

        return result;
    }

    @Override
    public ExpExperiment createStandardBatch(Container container, String name, ExpProtocol protocol)
    {
        if (name == null)
        {
            // Formats the current date using the default display format. This is intentional and name rendering code paths
            // have been reviewed for encoding. See #31076.
            name = DateUtil.formatDate(container) + " batch";
        }

        ExpExperiment batch = ExperimentService.get().createExpExperiment(container, name);
        // Make sure that our LSID is unique using a GUID.
        // Outside the main transaction, we'll separately give it a unique name
        batch.setLSID(ExperimentService.get().generateLSID(container, ExpExperiment.class, GUID.makeGUID()));
        batch.setBatchProtocol(protocol);

        return batch;
    }

    @Override
    public ExpExperiment ensureUniqueBatchName(ExpExperiment batch, ExpProtocol protocol, User user)
    {
        synchronized (BATCH_NAME_LOCK)
        {
            int suffix = 1;
            String originalName = batch.getName();
            List<? extends ExpExperiment> batches = ExperimentService.get().getMatchingBatches(batch.getName(), batch.getContainer(), protocol);
            while (batches.size() > 1)
            {
                batch.setName(originalName + " " + (++suffix));
                batch.save(user);
                batches = ExperimentService.get().getMatchingBatches(batch.getName(), batch.getContainer(), protocol);
            }

            return batches.get(0);
        }
    }

    @Override
    @Nullable
    public ExpExperiment findBatch(ExpRun run)
    {
        int protocolId = run.getProtocol().getRowId();
        for (ExpExperiment potentialBatch : run.getExperiments())
        {
            ExpProtocol batchProtocol = potentialBatch.getBatchProtocol();
            if (batchProtocol != null && batchProtocol.getRowId() == protocolId)
            {
                return potentialBatch;
            }
        }
        return null;
    }

    /**
     * Creates a single document per assay design/folder combo, with some simple assay info (name, description), plus
     * the names and comments from all of the runs.
     */
    @Override
    public void indexAssays(SearchService.IndexTask task, Container c)
    {
        SearchService ss = SearchService.get();

        if (null == ss)
            return;

        List<ExpProtocol> protocols = getAssayProtocols(c);

        for (ExpProtocol protocol : protocols)
            indexAssay(task, c, protocol);
    }

    @Override
    public void indexAssay(SearchService.IndexTask task, Container c, ExpProtocol protocol)
    {
        AssayProvider provider = getProvider(protocol);

        if (null == provider)
            return;

        String name = protocol.getName();
        String instrument = protocol.getInstrument();
        String description = protocol.getDescription();
        String comment = protocol.getComment();
        User createdBy = protocol.getCreatedBy();
        Date created = protocol.getCreated();
        User modifiedBy = protocol.getModifiedBy();
        Date modified = protocol.getModified();

        ActionURL assayBeginURL = PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(c, protocol, AssayController.AssayBeginAction.class);
        assayBeginURL.setExtraPath(c.getId());
        String keywords = StringUtilsLabKey.joinNonBlank(" ", name, instrument, provider.getName());
        String body = StringUtilsLabKey.joinNonBlank(" ", provider.getName(), description, comment);
        Map<String, Object> m = new HashMap<>();
        m.put(SearchService.PROPERTY.title.toString(), name);
        m.put(SearchService.PROPERTY.keywordsMed.toString(), keywords);
        m.put(SearchService.PROPERTY.categories.toString(), ASSAY_CATEGORY.getName());

        String docId = protocol.getDocumentId();

        List<? extends ExpRun> runs = ExperimentService.get().getExpRuns(c, protocol, null);

        if (!runs.isEmpty())
        {
            StringBuilder runKeywords = new StringBuilder();

            for (ExpRun run : runs)
            {
                runKeywords.append(" ");
                runKeywords.append(run.getName());

                if (null != run.getComments())
                {
                    runKeywords.append(" ");
                    runKeywords.append(run.getComments());
                }
            }

            body += runKeywords.toString();
        }
        WebdavResource r = new SimpleDocumentResource(new Path(docId), docId, c.getId(), "text/plain", body, assayBeginURL, createdBy, created, modifiedBy, modified, m);
        task.addResource(r, SearchService.PRIORITY.item);
    }

    @Override
    public void unindexAssays(@NotNull Collection<? extends ExpProtocol> expProtocols)
    {
        SearchService ss = SearchService.get();
        if (null == ss)
            return;

        for (ExpProtocol protocol : expProtocols)
            ss.deleteResource(protocol.getDocumentId());
    }

    @Override
    public ExpRun createExperimentRun(@Nullable String name, Container container, ExpProtocol protocol, @Nullable File file)
    {
        if (name == null)
        {
            // Check if we have a file to use
            if (file == null || !file.isFile())
            {
                name = "[Untitled]";
            }
            else
            {
                name = file.getName();
            }
        }

        String entityId = GUID.makeGUID();
        ExpRun run = ExperimentService.get().createExperimentRun(container, name);

        Lsid lsid = new Lsid(getProvider(protocol).getRunLSIDPrefix(), "Folder-" + container.getRowId(), entityId);
        run.setLSID(lsid.toString());
        run.setProtocol(ExperimentService.get().getExpProtocol(protocol.getRowId()));
        run.setEntityId(entityId);

        File runRoot;
        if (file == null)
        {
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(container);
            if (pipeRoot == null)
            {
                throw new NotFoundException("Pipeline root is not configured for folder " + container);
            }
            runRoot = pipeRoot.getRootPath();
        }
        else if (file.isFile())
        {
            runRoot = file.getParentFile();
        }
        else
        {
            runRoot = file;
        }
        run.setFilePathRoot(runRoot);

        return run;
    }

    @Override
    public @NotNull List<Pair<Container, String>> getLocationOptions(Container container, User user)
    {
        List<Pair<Container, String>> containers = new ArrayList<>();

        // project
        Container project = container.getProject();
        if (project != null && !container.equals(project))
        {
            if (project.hasPermission(user, DesignAssayPermission.class))
                containers.add(new Pair<>(project, String.format("%s (%s)", "Project", project.getName())));
        }

        // for workbooks, use the parent folder as the current folder (unless it happens to be the project since that has already been added)
        if (container.isWorkbook())
        {
            container = container.getParent();
            if (container != null && container.isProject())
                container = null;
        }

        // current folder
        if (container != null && container.hasPermission(user, DesignAssayPermission.class))
            containers.add(new Pair<>(container, String.format("%s (%s)", "Current Folder", container.getName())));

        // shared project
        Container shared = ContainerManager.getSharedContainer();
        if (shared.hasPermission(user, DesignAssayPermission.class))
            containers.add(new Pair<>(shared, "Shared Folder"));

        return containers;
    }

    private Collection<ObjectProperty> batchAndRunProperties(ExpRun run, @Nullable ExpProtocol protocol) throws ExperimentException
    {
        if (protocol == null)
            protocol = run.getProtocol();
        if (protocol == null)
            throw new ExperimentException("No protocol found for run");

        ExpExperiment batch = AssayService.get().findBatch(run);
        Collection<ObjectProperty> properties = new ArrayList<>(run.getObjectProperties().values());
        if (batch != null)
        {
            properties.addAll(batch.getObjectProperties().values());
        }

        return properties;
    }

    @Override
    public ParticipantVisitResolver createResolver(User user, ExpRun run, @Nullable ExpProtocol protocol, @Nullable AssayProvider provider, Container targetStudyContainer)
            throws IOException, ExperimentException
    {
        if (provider == null)
            provider = AssayService.get().getProvider(protocol);
        if (provider == null)
            throw new ExperimentException("No assay provider found for protocol");

        Collection<ObjectProperty> properties = batchAndRunProperties(run, protocol);

        if (targetStudyContainer == null)
        {
            for (ObjectProperty property : properties)
            {
                if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equalsIgnoreCase(property.getName()))
                {
                    String targetStudy = property.getStringValue();
                    targetStudyContainer = ContainerManager.getForId(targetStudy);
                    break;
                }
            }
        }

        ParticipantVisitResolverType resolverType = null;
        for (ObjectProperty property : properties)
        {
            if (AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equals(property.getName()))
            {
                resolverType = AbstractAssayProvider.findType(property.getStringValue(), provider.getParticipantVisitResolverTypes());
                if (resolverType != null)
                    break;
            }
        }

        if (resolverType == null)
            resolverType = new StudyParticipantVisitResolverType();

        return resolverType.createResolver(run, targetStudyContainer, user);
    }

    public ExpProtocol findExpProtocol(GWTProtocol protocol, Container c)
    {
        ExpProtocol expProtocol = null;
        if (protocol.getProtocolId() != null)
            expProtocol = ExperimentService.get().getExpProtocol(protocol.getProtocolId());
        if (expProtocol == null && protocol.getName() != null)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol.getProviderName());
            if (provider == null)
                throw new NotFoundException("Assay provider '" + protocol.getProviderName() + "' not found");

            List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(c, provider);
            if (protocols.isEmpty())
                throw new NotFoundException( "No assay protocols for '" + protocol.getName() + "' found in container '" + c.getPath() + "'");

            protocols = protocols.stream().filter(p -> protocol.getName().equals(p.getName())).collect(Collectors.toList());
            if (protocols.isEmpty())
                throw new NotFoundException("Assay protocol '" + protocol.getName() + "' not found");

            if (protocols.size() > 1)
                throw new NotFoundException("More than one assay protocol named '" + protocol.getName() + "' was found.");

            expProtocol = protocols.get(0);
        }
        return expProtocol;
    }

    @Override
    public void clearProtocolCache()
    {
        PROTOCOL_CACHE.clear();
    }

    @Override
    public <FlagType extends ExpQCFlag> void saveFlag(Container container, User user, AssayProvider provider, FlagType flag)
    {
        AssayFlagHandler handler = AssayFlagHandler.getHandler(provider);
        if (handler != null)
        {
            handler.saveFlag(container, user, flag);
        }
    }

    @Override
    public <FlagType extends ExpQCFlag> void deleteFlag(Container container, User user, AssayProvider provider, FlagType flag)
    {
        AssayFlagHandler handler = AssayFlagHandler.getHandler(provider);
        if (handler != null)
        {
            handler.deleteFlag(container, user, flag);
        }
    }

    @Override
    public int deleteFlagsForRun(Container container, User user, AssayProvider provider, int runId)
    {
        AssayFlagHandler handler = AssayFlagHandler.getHandler(provider);
        if (handler != null)
        {
            return handler.deleteFlagsForRun(container, user, runId);
        }
        return 0;
    }

    @Override
    public <FlagType extends ExpQCFlag> List<FlagType> getFlags(AssayProvider provider, int runId, Class<FlagType> cls)
    {
        AssayFlagHandler handler = AssayFlagHandler.getHandler(provider);
        if (handler != null)
        {
            return handler.getFlags(runId, cls);
        }
        return Collections.emptyList();
    }

    public void createAssayDataImportButton(ViewContext context, ButtonBar bar)
    {
        if (shouldShowAssayDataImport(context))
        {
            ActionButton button = new ActionButton("Import Data (Experimental)", getAssayDataImportURL(context));
            button.setIconCls("plus");
            button.setActionType(ActionButton.Action.LINK);
            bar.add(button);
        }
    }

    public void createAssayDataImportButton(ViewContext context, NavTree menu)
    {
        if (shouldShowAssayDataImport(context))
        {
            menu.addChild("Import Data", getAssayDataImportURL(context));
        }
    }

    private boolean shouldShowAssayDataImport(ViewContext context)
    {
        boolean experimentalFlagEnabled = AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_ASSAY_DATA_IMPORT);
        return experimentalFlagEnabled && context.getContainer().hasPermission(context.getUser(), InsertPermission.class);
    }

    private ActionURL getAssayDataImportURL(ViewContext context)
    {
        ActionURL importURL = new ActionURL("assay", "assayDataImport", context.getContainer());
        importURL.addReturnURL(context.getActionURL());
        return importURL;
    }

    List<AssayProvider> getRegisteredAssayProviders()
    {
        return _providers;
    }
}
