/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRunTable;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.assay.query.AssayListPortalView;
import org.labkey.study.assay.query.AssayListQueryView;
import org.labkey.study.assay.query.AssaySchema;
import org.springframework.web.servlet.mvc.Controller;

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
        try
        {
            getProvider(provider.getName());
        }
        catch (IllegalArgumentException e)
        {
            _providers.add(provider);
            return;
        }
        throw new IllegalArgumentException("A provider with the name " + provider.getName() + " has already been registered");
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
        throw new IllegalArgumentException("Unknown provider: " + providerName);
    }

    public AssayProvider getProvider(ExpProtocol protocol)
    {
        return Handler.Priority.findBestHandler(_providers, protocol);
    }

    public List<AssayProvider> getAssayProviders()
    {
        return Collections.unmodifiableList(_providers);
    }

    public ExpRunTable createRunTable(String alias, ExpProtocol protocol, AssayProvider provider, User user, Container container)
    {
        return new AssaySchema(user, container).createRunTable(alias, protocol, provider);
    }

    public UserSchema createSchema(User user, Container container)
    {
        return new AssaySchema(user, container);
    }

    public List<ExpProtocol> getAssayProtocols(Container container)
    {
        List<ExpProtocol> protocols = new ArrayList<ExpProtocol>();
        ExpProtocol[] containerProtocols = ExperimentService.get().getExpProtocols(container);
        addTopLevelProtocols(containerProtocols, protocols);
        Container project = container.getProject();
        if (project != null && !container.equals(project))
        {
            ExpProtocol[] projectProtocols = ExperimentService.get().getExpProtocols(container.getProject());
            addTopLevelProtocols(projectProtocols, protocols);
        }
        return protocols;
    }

    public boolean hasAssayProtocols(Container container)
    {
        ExpProtocol[] containerProtocols = ExperimentService.get().getExpProtocols(container);
        if (containerProtocols != null)
        {
            for (ExpProtocol protocol : containerProtocols)
            {
                if (AssayService.get().getProvider(protocol) != null)
                    return true;
            }
        }
        Container project = container.getProject();
        if (project != null && !container.equals(project))
        {
            ExpProtocol[] projectProtocols = ExperimentService.get().getExpProtocols(project);
            if (projectProtocols != null)
            {
                for (ExpProtocol protocol : projectProtocols)
                {
                    if (AssayService.get().getProvider(protocol) != null)
                        return true;
                }
            }
        }
        return false;
    }

    private void addTopLevelProtocols(ExpProtocol[] potential, List<ExpProtocol> returnList)
    {
        for (ExpProtocol protocol : potential)
        {
            if (AssayService.get().getProvider(protocol) != null)
                returnList.add(protocol);
        }
    }

    public QueryView createAssayListView(ViewContext context, boolean portalView)
    {
        String name = "AssayList";
        QuerySettings settings = new QuerySettings(context, name);
        settings.setSchemaName(AssayService.ASSAY_SCHEMA_NAME);
        settings.setQueryName(name);
        if (portalView)
            return new AssayListPortalView(context, settings);
        return new AssayListQueryView(context, settings);
    }

    public List<ActionButton> getImportButtons(ExpProtocol protocol, User user, Container currentContainer, boolean includeOtherContainers)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        assert provider != null : "Could not find a provider for protocol: " + protocol;
        Set<Container> containers = new TreeSet<Container>();
        if (includeOtherContainers)
        {
            // First find all the containers that have contributed data to this protocol
            ExpRun[] runs = protocol.getExpRuns();
            for (ExpRun run : runs)
                containers.add(run.getContainer());

            // If there are none, include the container of the protocol itself
            if (containers.size() == 0)
                containers.add(protocol.getContainer());
        }
        else
        {
            containers.add(currentContainer);
        }

        // Check for write permission
        for (Iterator<Container> iter = containers.iterator(); iter.hasNext();)
        {
            Container container = iter.next();
            if (!container.hasPermission(user, ACL.PERM_INSERT) || !provider.allowUpload(user, container, protocol))
            {
                iter.remove();
            }
        }
        if (containers.size() == 0)
            return Collections.emptyList(); // Nowhere to upload to, no button

        List<ActionButton> result = new ArrayList<ActionButton>();

        if (containers.size() == 1)
        {
            Container c = containers.iterator().next();
            // If it's the current container, we want a simple button
            if (c.equals(currentContainer))
            {
                for (Map.Entry<String, Class<? extends Controller>> entry : provider.getImportActions().entrySet())
                {
                    result.add(new ActionButton(PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(c, protocol, entry.getValue()), entry.getKey()));
                }
            }
        }
        else
        {
            // It's not the current container, so fall through to show a submenu even though there's
            // only one item, in order to indicate that the user is going to be redirected elsewhere
            for (Map.Entry<String, Class<? extends Controller>> entry : provider.getImportActions().entrySet())
            {
                MenuButton uploadButton = new MenuButton(entry.getKey());
                for(Container container : containers)
                {
                    ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(container, protocol, entry.getValue());
                    uploadButton.addMenuItem(container.getPath(), url);
                }
                result.add(uploadButton);
            }
        }

        return result;
    }
}
