/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.study.assay;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.MenuButton;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.*;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.study.assay.query.AssayListPortalView;
import org.labkey.study.assay.query.AssayListQueryView;
import org.labkey.study.assay.query.AssaySchemaImpl;
import org.labkey.study.model.StudyManager;

import java.util.*;

/**
 * User: brittp
 * Date: Jun 22, 2007
 * Time: 4:21:59 PM
 */
public class AssayManager implements AssayService.Interface
{
    private List<AssayProvider> _providers = new ArrayList<AssayProvider>();

    public AssayManager()
    {
    }

    public static synchronized AssayManager get()
    {
        return (AssayManager) AssayService.get();
    }

    public ExpProtocol createAssayDefinition(User user, Container container, GWTProtocol newProtocol)
            throws ExperimentException
    {
        return getProvider(newProtocol.getProviderName()).createAssayDefinition(user, container, newProtocol.getName(),
                newProtocol.getDescription());
    }

    public void registerAssayProvider(AssayProvider provider)
    {
        // Blow up if we've already added a provider with this name
        if (getProvider(provider.getName()) != null)
        {
            throw new IllegalArgumentException("A provider with the name " + provider.getName() + " has already been registered");
        }
        _providers.add(provider);
        PipelineProvider pipelineProvider = provider.getPipelineProvider();
        if (pipelineProvider != null)
        {
            PipelineService.get().registerPipelineProvider(pipelineProvider);
        }
    }

    public AssayProvider getProvider(String providerName)
    {
        for (AssayProvider potential : _providers)
        {
            if (potential.getName().equals(providerName))
            {
                return potential;
            }
        }
        return null;
    }

    public AssayProvider getProvider(ExpProtocol protocol)
    {
        return Handler.Priority.findBestHandler(_providers, protocol);
    }

    public List<AssayProvider> getAssayProviders()
    {
        return Collections.unmodifiableList(_providers);
    }

    public ExpRunTable createRunTable(ExpProtocol protocol, AssayProvider provider, User user, Container container)
    {
        return (ExpRunTable)new AssaySchemaImpl(user, container).getTable(getRunsTableName(protocol));
    }

    public AssaySchema createSchema(User user, Container container)
    {
        return new AssaySchemaImpl(user, container);
    }

    public String getBatchesTableName(ExpProtocol protocol)
    {
        return AssaySchemaImpl.getBatchesTableName(protocol);
    }

    public String getRunsTableName(ExpProtocol protocol)
    {
        return AssaySchemaImpl.getRunsTableName(protocol);
    }

    public String getResultsTableName(ExpProtocol protocol)
    {
        return AssaySchemaImpl.getResultsTableName(protocol);
    }

    public boolean hasAssayProtocols(Container container)
    {
        ExpProtocol[] containerProtocols = ExperimentService.get().getExpProtocols(container);
        if (containerProtocols != null && containerProtocols.length > 0)
            return true;

        Container project = container.getProject();
        if (project != null && !container.equals(project))
        {
            ExpProtocol[] projectProtocols = ExperimentService.get().getExpProtocols(container.getProject());
            if (projectProtocols != null && projectProtocols.length > 0)
                return true;
        }

        return false;
    }

    public List<ExpProtocol> getAssayProtocols(Container container)
    {
        List<ExpProtocol> protocols = new ArrayList<ExpProtocol>();
        ExpProtocol[] containerProtocols = ExperimentService.get().getExpProtocols(container);
        addTopLevelProtocols(containerProtocols, protocols);
        Container project = container.getProject();
        if (project != null && !container.equals(project))
        {
            ExpProtocol[] projectProtocols = ExperimentService.get().getExpProtocols(project);
            addTopLevelProtocols(projectProtocols, protocols);
        }
        Container sharedContainer = ContainerManager.getSharedContainer();
        if (!container.equals(sharedContainer) && !sharedContainer.equals(project))
        {
            ExpProtocol[] projectProtocols = ExperimentService.get().getExpProtocols(sharedContainer);
            addTopLevelProtocols(projectProtocols, protocols);
        }
        return protocols;
    }

    private void addTopLevelProtocols(ExpProtocol[] potential, List<ExpProtocol> returnList)
    {
        for (ExpProtocol protocol : potential)
        {
            if (AssayService.get().getProvider(protocol) != null)
                returnList.add(protocol);
        }
    }

    public WebPartView createAssayListView(ViewContext context, boolean portalView)
    {
        String name = "AssayList";
        QuerySettings settings = new QuerySettings(context, name);
        settings.setSchemaName(AssaySchema.NAME);
        settings.setQueryName(name);
        QueryView queryView;
        if (portalView)
            queryView = new AssayListPortalView(context, settings);
        else
            queryView = new AssayListQueryView(context, settings);

        VBox vbox = new VBox();
        if (portalView)
            vbox.setFrame(WebPartView.FrameType.PORTAL);
        vbox.addView(new JspView("/org/labkey/study/assay/view/assaySetup.jsp"));
        vbox.addView(queryView);
        return vbox;
    }

    public List<ActionButton> getImportButtons(ExpProtocol protocol, User user, Container currentContainer, boolean isStudyView)
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
        for (Iterator<Container> iter = containers.iterator(); iter.hasNext();)
        {
            Container container = iter.next();
            boolean hasPermission = container.hasPermission(user, InsertPermission.class);
            boolean hasPipeline = PipelineService.get().hasValidPipelineRoot(container);
            if (!hasPermission || !hasPipeline)
            {
                iter.remove();
            }
        }
        if (containers.size() == 0)
            return Collections.emptyList(); // Nowhere to upload to, no button

        List<ActionButton> result = new ArrayList<ActionButton>();

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
                uploadButton.addMenuItem("Current Folder (" + currentContainer.getPath() + ")", url);
            }
            for(Container container : containers)
            {
                ActionURL url = provider.getImportURL(container, protocol);
                uploadButton.addMenuItem(container.getPath(), url);
            }
            result.add(uploadButton);
        }

        return result;
    }

    public ExpExperiment createStandardBatch(Container container, String namePrefix, ExpProtocol protocol)
    {
        if (namePrefix == null)
        {
            namePrefix = DateUtil.formatDate() + " batch";
        }
        ExpExperiment batch;
        int batchNumber = 1;
        do
        {
            String name = namePrefix;
            if (batchNumber > 1)
            {
                name = namePrefix + " " + batchNumber;
            }
            batchNumber++;
            batch = ExperimentService.get().createExpExperiment(container, name);
            batch.setBatchProtocol(protocol);
        }
        while(ExperimentService.get().getExpExperiment(batch.getLSID()) != null);
        return batch;
    }

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

    public void indexAssays(SearchService.IndexTask task, Container c)
    {
        SearchService ss = ServiceRegistry.get().getService(SearchService.class);

        if (null == ss)
            return;

        List<ExpProtocol> protocols = getAssayProtocols(c);

        for (ExpProtocol protocol : protocols)
        {
            AssayProvider provider = getProvider(protocol);

            if (null == provider)
                continue;

            ExpRun[] runs = ExperimentService.get().getExpRuns(c, protocol, null);

            if (0 == runs.length)
                continue;

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

            String name = protocol.getName();
            String instrument = protocol.getInstrument();
            String description = protocol.getDescription();
            String comment = protocol.getComment();

            ActionURL assayRunsURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(c, protocol);

            String searchTitle = StringUtils.trimToEmpty(name) + " " + StringUtils.trimToEmpty(instrument) + " " + StringUtils.trimToEmpty(provider.getName());
            String body = StringUtils.trimToEmpty(provider.getName()) + " " + StringUtils.trimToEmpty(description) + " " + StringUtils.trimToEmpty(comment) + runKeywords.toString();
            Map<String, Object> m = new HashMap<String, Object>();
            m.put(SearchService.PROPERTY.displayTitle.toString(), name);
            m.put(SearchService.PROPERTY.searchTitle.toString(), searchTitle);
            m.put(SearchService.PROPERTY.categories.toString(), StudyManager.assayCategory.getName());

            String docId = "assay:" + c.getId() + ":" + protocol.getRowId();
            WebdavResource r = new SimpleDocumentResource(new Path(docId), docId, c.getId(), "text/plain", body.getBytes(), assayRunsURL, m);
            task.addResource(r, SearchService.PRIORITY.item);
        }
    }
}

