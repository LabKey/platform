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

import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRunTable;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QueryParam;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.assay.query.AssayListPortalView;
import org.labkey.study.assay.query.AssayListQueryView;
import org.labkey.study.assay.query.AssaySchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public ActionURL getProtocolURL(Container container, ExpProtocol protocol, String action)
    {
        ActionURL url = new ActionURL("assay", action, container);
        if (protocol != null)
            url.addParameter("rowId", protocol.getRowId());
        return url;
    }

    public ActionURL getDesignerURL(Container container, ExpProtocol protocol, boolean copy)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
            return null;
        return provider.getDesignerURL(container, protocol, copy);
    }

    public ActionURL getDesignerURL(Container container, String providerName)
    {
        AssayProvider provider = getProvider(providerName);
        if (provider == null)
        {
            return null;
        }
        return provider.getDesignerURL(container, null, false);
    }

    public ActionURL getPublishConfirmURL(Container container, ExpProtocol protocol)
    {
        return getProtocolURL(container, protocol, "publishConfirm");
    }

    public ActionURL getAssayRunsURL(Container container, ExpProtocol protocol)
    {
        return getAssayRunsURL(container, protocol, null);
    }

    public ActionURL getAssayRunsURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter)
    {
        ActionURL url = getProtocolURL(container, protocol, "assayRuns");
        if (containerFilter != null)
        {
            url.addParameter(protocol.getName() + " Runs." + QueryParam.containerFilterName, containerFilter.name());
        }
        return url;
    }

    public ActionURL getAssayListURL(Container container)
    {
        return getProtocolURL(container, null, "begin");
    }

    public ActionURL getUploadWizardURL(Container container, ExpProtocol protocol)
    {
        return getProvider(protocol).getUploadWizardURL(container, protocol);
    }

    public ActionURL getAssayDataURL(Container container, ExpProtocol protocol, int... runIds)
    {
        return getAssayDataURL(container, protocol, null, runIds);
    }

    public ActionURL getAssayDataURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter, int... runIds)
    {
        ActionURL result = getProtocolURL(container, protocol, "assayData");
        AssayProvider provider = getProvider(protocol);
        if (runIds.length > 1)
        {
            String sep = "";
            StringBuilder filterValue = new StringBuilder();
            for (int runId : runIds)
            {
                filterValue.append(sep).append(runId);
                sep = ";";
            }
            result.addFilter(provider.getRunDataTableName(protocol),
                    provider.getRunIdFieldKeyFromDataRow(), CompareType.IN, filterValue.toString());
        }
        else if (runIds.length == 1)
        {
            result.addFilter(provider.getRunDataTableName(protocol),
                    provider.getRunIdFieldKeyFromDataRow(), CompareType.EQUAL, runIds[0]);
        }
        if (containerFilter != null)
            result.addParameter(protocol.getName() + " Data." + QueryParam.containerFilterName, containerFilter.name());
        return result;
    }
}
