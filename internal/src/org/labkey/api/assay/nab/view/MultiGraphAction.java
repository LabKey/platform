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
import org.labkey.api.assay.nab.NabGraph;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 6/11/13
 */
public class MultiGraphAction<FormType extends GraphSelectedForm> extends SimpleViewAction<FormType>
{
    public ModelAndView getView(FormType form, BindException errors) throws Exception
    {
        int[] ids = form.getId();
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getProtocolId());
        DilutionAssayProvider provider = (DilutionAssayProvider)AssayService.get().getProvider(protocol);
        Map<DilutionSummary, DilutionAssayRun> summaries = provider.getDataHandler().getDilutionSummaries(getUser(), form.getFitTypeEnum(), ids);
        Set<Integer> cutoffSet = new HashSet<>();
        for (DilutionSummary summary : summaries.keySet())
        {
            for (int cutoff : summary.getAssay().getCutoffs())
                cutoffSet.add(cutoff);
        }

        int[] cutoffs = new int[cutoffSet.size()];
        int i = 0;
        for (int value : cutoffSet)
            cutoffs[i++] = value;

        NabGraph.Config config = getGraphConfig(form);

        config.setCutoffs(cutoffs);
        NabGraph.renderChartPNG(getContainer(), getViewContext().getResponse(), summaries, config);
        return null;
    }

    protected NabGraph.Config getGraphConfig(FormType form)
    {
        NabGraph.Config config = new NabGraph.Config();
        config.setLockAxes(false);
        config.setCaptionColumn(form.getCaptionColumn());
        config.setChartTitle(form.getChartTitle());
        if (form.getHeight() > 0)
            config.setHeight(form.getHeight());
        if (form.getWidth() > 0)
        config.setWidth(form.getWidth());

        return config;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        throw new UnsupportedOperationException();
    }
}
