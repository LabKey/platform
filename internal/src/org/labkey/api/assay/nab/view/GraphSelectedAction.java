/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.assay.dilution.DilutionAssayProvider;
import org.labkey.api.assay.dilution.DilutionAssayRun;
import org.labkey.api.assay.dilution.DilutionSummary;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.study.actions.AssayHeaderView;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 6/11/13
 */
public abstract class GraphSelectedAction<FormType extends GraphSelectedForm> extends SimpleViewAction<FormType>
{
    private ExpProtocol _protocol;

    public ModelAndView getView(FormType form, BindException errors) throws Exception
    {
        _protocol = ExperimentService.get().getExpProtocol(form.getProtocolId());
        if (_protocol == null)
        {
            throw new NotFoundException();
        }
        int[] objectIds;
        if (form.getId() != null)
            objectIds = form.getId();
        else
        {
            Set<String> objectIdStrings = DataRegionSelection.getSelected(getViewContext(), false);
            if (objectIdStrings.isEmpty())
            {
                throw new NotFoundException("No samples specified.");
            }
            objectIds = new int[objectIdStrings.size()];
            int idx = 0;
            for (String objectIdString : objectIdStrings)
                objectIds[idx++] = Integer.parseInt(objectIdString);
        }

        Set<Integer> cutoffSet = new HashSet<>();
        DilutionAssayProvider provider = (DilutionAssayProvider) AssayService.get().getProvider(_protocol);
        Map<DilutionSummary, DilutionAssayRun> summaries = provider.getDataHandler().getDilutionSummaries(getUser(), form.getFitTypeEnum(), objectIds);
        for (DilutionSummary summary : summaries.keySet())
        {
            for (int cutoff : summary.getAssay().getCutoffs())
                cutoffSet.add(cutoff);
        }

        int[] cutoffs = new int[cutoffSet.size()];
        int i = 0;
        for (int value : cutoffSet)
            cutoffs[i++] = value;

        GraphSelectedBean bean = createSelectionBean(getViewContext(), _protocol, cutoffs, objectIds, form.getCaptionColumn(), form.getChartTitle());

        JspView<GraphSelectedBean> multiGraphView = new JspView<>("/org/labkey/api/assay/nab/view/multiRunGraph.jsp", bean);

        return new VBox(new AssayHeaderView(_protocol, provider, false, true, null), multiGraphView);
    }

    protected abstract GraphSelectedBean createSelectionBean(ViewContext context, ExpProtocol protocol, int[] cutoffs,
                                                             int[] dataObjectIds, String caption, String title);

    public NavTree appendNavTrail(NavTree root)
    {
        ActionURL assayListURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(getContainer());
        ActionURL runListURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol);
        return root.addChild("Assay List", assayListURL).addChild(_protocol.getName() +
                " Runs", runListURL).addChild("Graph Selected Specimens");
    }
}
