/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.assay.dilution.query;

import org.labkey.api.assay.dilution.DilutionAssayProvider;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 6/11/13
 */
public abstract class DilutionResultsQueryView extends ResultsQueryView
{
    protected Map<String, Object> _extraDetailsUrlParams = new HashMap<>();

    public DilutionResultsQueryView(ExpProtocol protocol, ViewContext context, QuerySettings settings)
    {
        super(protocol, context, settings);
    }

    public abstract ActionURL getGraphSelectedURL();
    public abstract ActionURL getRunDetailsURL(Object runId);

    protected String getChartTitle(String propLabel)
    {
        return "Neutralization by " + propLabel;
    }

    protected void addGraphSubItems(NavTree parent, Domain domain, String formId, Set<String> excluded)
    {
        ActionURL graphSelectedURL = getGraphSelectedURL();
        for (DomainProperty prop : domain.getProperties())
        {
            if (!excluded.contains(prop.getName()))
            {
                NavTree menuItem = new NavTree(prop.getLabel(), "#");
                menuItem.setScript("document.forms[" + PageFlowUtil.jsString(formId) + "].action = '" + graphSelectedURL.getLocalURIString() + "';\n" +
                        "document.forms[" + PageFlowUtil.jsString(formId) + "].captionColumn.value = '" + prop.getName() + "';\n" +
                        "document.forms[" + PageFlowUtil.jsString(formId) + "].chartTitle.value = '" + getChartTitle(prop.getLabel()) + "';\n" +
                        "document.forms[" + PageFlowUtil.jsString(formId) + "].method = 'POST';\n" +
                        "document.forms[" + PageFlowUtil.jsString(formId) + "].submit(); return false;");
                parent.addChild(menuItem);
            }
        }
    }

    protected Set<String> getExcludedSampleProperties()
    {
        Set<String> excluded = new HashSet<>();
        excluded.add(DilutionAssayProvider.SAMPLE_METHOD_PROPERTY_NAME);
        excluded.add(DilutionAssayProvider.SAMPLE_INITIAL_DILUTION_PROPERTY_NAME);
        excluded.add(DilutionAssayProvider.SAMPLE_DILUTION_FACTOR_PROPERTY_NAME);

        return excluded;
    }

    protected Set<String> getExcludedRunProperties()
    {
        Set<String> excluded = new HashSet<>();
        excluded.add(DilutionAssayProvider.CURVE_FIT_METHOD_PROPERTY_NAME);
        excluded.add(DilutionAssayProvider.LOCK_AXES_PROPERTY_NAME);
        excluded.addAll(Arrays.asList(DilutionAssayProvider.CUTOFF_PROPERTIES));

        return excluded;
    }

    @Override
    public DataView createDataView()
    {
        DataView view = super.createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setRecordSelectorValueColumns("RowId");
        rgn.addHiddenFormField("protocolId", "" + _protocol.getRowId());
        ButtonBar bbar = new ButtonBar(view.getDataRegion().getButtonBar(DataRegion.MODE_GRID));
        view.getDataRegion().setButtonBar(bbar);

        ActionURL graphSelectedURL = getGraphSelectedURL();
        MenuButton graphSelectedButton = new MenuButton("Graph");
        rgn.addHiddenFormField("captionColumn", "");
        rgn.addHiddenFormField("chartTitle", "");

        graphSelectedButton.addMenuItem("Default Graph", "#",
                "document.forms[" + PageFlowUtil.jsString(rgn.getFormId()) + "].action = '" + graphSelectedURL.getLocalURIString() + "';\n" +
                "document.forms[" + PageFlowUtil.jsString(rgn.getFormId()) + "].method = 'POST';\n" +
                "document.forms[" + PageFlowUtil.jsString(rgn.getFormId()) + "].submit(); return false;");

        Domain sampleDomain = ((DilutionAssayProvider) _provider).getSampleWellGroupDomain(_protocol);
        NavTree sampleSubMenu = new NavTree("Custom Caption (Sample)");
        addGraphSubItems(sampleSubMenu, sampleDomain, rgn.getFormId(), getExcludedSampleProperties());
        graphSelectedButton.addMenuItem(sampleSubMenu);

        Domain runDomain = _provider.getRunDomain(_protocol);
        NavTree runSubMenu = new NavTree("Custom Caption (Run)");
        addGraphSubItems(runSubMenu, runDomain, rgn.getFormId(), getExcludedRunProperties());
        graphSelectedButton.addMenuItem(runSubMenu);
        graphSelectedButton.setRequiresSelection(true);
        bbar.add(graphSelectedButton);

        rgn.addDisplayColumn(0, new SimpleDisplayColumn()
        {
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                Object runId = ctx.getRow().get(DilutionProviderSchema.RUN_ID_COLUMN_NAME);
                if (runId != null)
                {
                    ActionURL url = getRunDetailsURL(runId);
                    if (!_extraDetailsUrlParams.isEmpty())
                        url.addParameters(_extraDetailsUrlParams);

                    Map<String, String> title = new HashMap<>();
                    title.put("title", "View run details");
                    out.write(PageFlowUtil.textLink("run details", url, "", "", title));
                }
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> set)
            {
                super.addQueryFieldKeys(set);
                ColumnInfo runIdColumn = getTable().getColumn(DilutionProviderSchema.RUN_ID_COLUMN_NAME);
                if (runIdColumn != null)
                    set.add(runIdColumn.getFieldKey());
            }
        });
        return view;
    }

    public void setExtraDetailsUrlParams(Map<String, Object> extraDetailsUrlParams)
    {
        _extraDetailsUrlParams = extraDetailsUrlParams;
    }
}
