/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.assay.nab.view;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.assay.dilution.DilutionAssayProvider;
import org.labkey.api.assay.dilution.DilutionAssayRun;
import org.labkey.api.assay.nab.NabGraph;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.nab.NabUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.actions.AssayHeaderView;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: 5/15/13
 */
public class RunDetailsHeaderView extends AssayHeaderView
{
    private ExpRun _run;
    private Container _container;
    private final boolean _showGraphLayoutOptions;
    private List<DilutionAssayRun.SampleResult> _samples;
    private Map<String, PropertyDescriptor> _propertyDescriptorMap = new CaseInsensitiveHashMap<>();
    private User _user;

    public RunDetailsHeaderView(Container container, ExpProtocol protocol, AssayProvider provider, ExpRun run, List<DilutionAssayRun.SampleResult> samples, User user)
    {
        super(protocol, provider, true, true, null);
        _container = container;
        _run = run;
        _samples = samples;
        _showGraphLayoutOptions = (samples.size() > 10);
        _user = user;

        for (DilutionAssayRun.SampleResult sample : samples)
        {
            for (Map.Entry<PropertyDescriptor, Object> entry : sample.getSampleProperties().entrySet())
            {
                if (entry.getValue() != null)
                {
                    if (!_propertyDescriptorMap.containsKey(entry.getKey().getName()))
                        _propertyDescriptorMap.put(entry.getKey().getName(), entry.getKey());
                }
            }
        }
    }

    @Override
    public List<NavTree> getLinks()
    {
        List<NavTree> links = new ArrayList<>();

        links.add(new NavTree("View Runs", PageFlowUtil.addLastFilterParameter(PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getViewContext().getContainer(), _protocol, _containerFilter), AssayProtocolSchema.getLastFilterScope(_protocol))));
        links.add(new NavTree("View Results", PageFlowUtil.addLastFilterParameter(PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(getViewContext().getContainer(), _protocol, _containerFilter, _run.getRowId()), AssayProtocolSchema.getLastFilterScope(_protocol))));

        if (getViewContext().getContainer().hasPermission(_user, InsertPermission.class))
        {
            links.add(new NavTree(AbstractAssayProvider.IMPORT_DATA_LINK_NAME, _provider.getImportURL(_container, _protocol)));

            if (getViewContext().getContainer().hasPermission(_user, DeletePermission.class))
            {
                ActionURL rerunURL = getProvider().getImportURL(_container, getProtocol());
                if (rerunURL != null)
                {
                    rerunURL.addParameter("reRunId", _run.getRowId());
                    links.add(new NavTree("Delete and Re-import", rerunURL));
                }
            }
        }

        // quality control actions menu
        if (_provider instanceof DilutionAssayProvider && (((DilutionAssayProvider)_provider).getAssayQCRunURL(_viewContext, _run) != null))
            links.add(getQCMenu());

        NavTree graphOptionsMenu = new NavTree("Change Graph Options");
        graphOptionsMenu.addChild(getCurveFitMenu());
        graphOptionsMenu.addChild(getGraphSizeMenu());

        if (_showGraphLayoutOptions)
        {
            graphOptionsMenu.addChild(getSamplesPerGraphMenu());
            graphOptionsMenu.addChild(getGraphLayoutMenu());
        }
        graphOptionsMenu.addChild(getDataIdentifiersMenu());
        links.add(graphOptionsMenu);

        ActionURL downloadURL = PageFlowUtil.urlProvider(NabUrls.class).urlDownloadDatafile(_container).addParameter("rowId", _run.getRowId());
        links.add(new NavTree("Download Datafile", downloadURL));
        links.add(new NavTree("Print", getViewContext().cloneActionURL().addParameter("_print", "true")));
        return links;
    }

    private NavTree getCurveFitMenu()
    {
        String currentFit = getViewContext().getActionURL().getParameter("fitType");
        NavTree menu = new NavTree("Curve Type");
        for (StatsService.CurveFitType type : StatsService.CurveFitType.values())
        {
            ActionURL changeCurveURL = getViewContext().cloneActionURL();
            changeCurveURL.replaceParameter("fitType", type.name());

            NavTree item = new NavTree(type.getLabel(), changeCurveURL);
            item.setSelected(type.name().equals(currentFit));

            menu.addChild(item);
        }

        return menu;
    }

    private NavTree getGraphSizeMenu()
    {
        int currentWidth = NumberUtils.toInt(getViewContext().getActionURL().getParameter("graphWidth"), NabGraph.DEFAULT_WIDTH);
        NavTree menu = new NavTree("Graph Size");

        ActionURL url = getViewContext().cloneActionURL();
        url.replaceParameter("graphWidth", String.valueOf(NabGraph.DEFAULT_WIDTH));
        url.replaceParameter("graphHeight", String.valueOf(NabGraph.DEFAULT_HEIGHT));
        NavTree item = new NavTree("Small", url);
        item.setSelected(currentWidth == NabGraph.DEFAULT_WIDTH);
        menu.addChild(item);

        url = getViewContext().cloneActionURL();
        url.replaceParameter("graphWidth", "600");
        url.replaceParameter("graphHeight", "550");
        item = new NavTree("Medium", url);
        item.setSelected(currentWidth == 600);
        menu.addChild(item);

        url = getViewContext().cloneActionURL();
        url.replaceParameter("graphWidth", "800");
        url.replaceParameter("graphHeight", "600");
        item = new NavTree("Large", url);
        item.setSelected(currentWidth == 800);
        menu.addChild(item);

        return menu;
    }

    private NavTree getGraphLayoutMenu()
    {
        int currentLayout = NumberUtils.toInt(getViewContext().getActionURL().getParameter("graphsPerRow"), NabGraph.DEFAULT_GRAPHS_PER_ROW);
        NavTree menu = new NavTree("Graphs per Row");

        ActionURL url = getViewContext().cloneActionURL();
        url.replaceParameter("graphsPerRow", "1");
        NavTree item = new NavTree("One", url);
        item.setSelected(currentLayout == 1);
        menu.addChild(item);

        url = getViewContext().cloneActionURL();
        url.replaceParameter("graphsPerRow", "2");
        item = new NavTree("Two", url);
        item.setSelected(currentLayout == 2);
        menu.addChild(item);

        url = getViewContext().cloneActionURL();
        url.replaceParameter("graphsPerRow", "3");
        item = new NavTree("Three", url);
        item.setSelected(currentLayout == 3);
        menu.addChild(item);

        url = getViewContext().cloneActionURL();
        url.replaceParameter("graphsPerRow", "4");
        item = new NavTree("Four", url);
        item.setSelected(currentLayout == 4);
        menu.addChild(item);

        return menu;
    }

    private NavTree getSamplesPerGraphMenu()
    {
        int currentLayout = NumberUtils.toInt(getViewContext().getActionURL().getParameter("maxSamplesPerGraph"), NabGraph.DEFAULT_MAX_SAMPLES_PER_GRAPH);
        NavTree menu = new NavTree("Samples per Graph");

        ActionURL url = getViewContext().cloneActionURL();
        url.replaceParameter("maxSamplesPerGraph", "5");
        NavTree item = new NavTree("5", url);
        item.setSelected(currentLayout == 5);
        menu.addChild(item);

        url = getViewContext().cloneActionURL();
        url.replaceParameter("maxSamplesPerGraph", "10");
        item = new NavTree("10", url);
        item.setSelected(currentLayout == 10);
        menu.addChild(item);

        url = getViewContext().cloneActionURL();
        url.replaceParameter("maxSamplesPerGraph", "15");
        item = new NavTree("15", url);
        item.setSelected(currentLayout == 15);
        menu.addChild(item);

        url = getViewContext().cloneActionURL();
        url.replaceParameter("maxSamplesPerGraph", "20");
        item = new NavTree("20", url);
        item.setSelected(currentLayout == 20);
        menu.addChild(item);

        return menu;
    }

    private NavTree getDataIdentifiersMenu()
    {
        String currentIdentifier = StringUtils.defaultString(getViewContext().getActionURL().getParameter(RunDetailOptions.DATA_IDENTIFIER_PARAM), "");
        NavTree menu = new NavTree("Data Identifiers");

        for (RunDetailOptions.DataIdentifier identifier : RunDetailOptions.DataIdentifier.values())
        {
            if (identifier.isSelectable())
            {
                ActionURL url = getViewContext().cloneActionURL();
                url.replaceParameter(RunDetailOptions.DATA_IDENTIFIER_PARAM, identifier.name());
                NavTree item = new NavTree(identifier.getCaption(), url);

                if (containsSampleProperties(identifier.getRequiredProperties()))
                    item.setSelected(identifier.name().equals(currentIdentifier));
                else
                {
                    item.setDisabled(true);
                    item.setDescription("This option is disabled because the required information does not exist.");
                }

                menu.addChild(item);
            }
        }
        return menu;
    }

    private NavTree getQCMenu()
    {
        NavTree qcMenu = new NavTree("View QC");

        // must be an administrator to access the QC workflow
        if (_container.hasPermission(_user, AdminPermission.class))
        {
            NavTree excludedDataMenu = new NavTree("Review/QC Data", ((DilutionAssayProvider)_provider).getAssayQCRunURL(_viewContext, _run));
            qcMenu.addChild(excludedDataMenu);
        }
        NavTree excludedDataMenu = new NavTree("View Excluded Data", ((DilutionAssayProvider)_provider).getAssayQCRunURL(_viewContext, _run).addParameter("edit", false));
        qcMenu.addChild(excludedDataMenu);

        return qcMenu;
    }

    private boolean containsSampleProperties(String[] names)
    {
        for (String name : names)
        {
            if (!_propertyDescriptorMap.containsKey(name))
                return false;
        }
        return true;
    }

    public boolean isShowGraphLayoutOptions()
    {
        return _showGraphLayoutOptions;
    }
}
