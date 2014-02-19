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
import org.labkey.api.assay.dilution.DilutionDataHandler;
import org.labkey.api.assay.nab.GraphForm;
import org.labkey.api.assay.nab.NabGraph;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: klum
 * Date: 5/21/13
 */
public abstract class DilutionGraphAction extends SimpleViewAction<GraphForm>
{
    public ModelAndView getView(GraphForm form, BindException errors) throws Exception
    {
        if (form.getRowId() == -1)
            throw new NotFoundException("Run ID not specified.");
        ExpRun run = ExperimentService.get().getExpRun(form.getRowId());
        if (run == null)
            throw new NotFoundException("Run " + form.getRowId() + " does not exist.");

        User user = getGraphUser();
        DilutionAssayRun assay = getAssayRun(run, form.getFitTypeEnum(), user);
        if (assay == null)
            throw new NotFoundException("Could not load Dilution results for run " + form.getRowId() + ".");

        NabGraph.Config config = getGraphConfig(form, assay);
        NabGraph.renderChartPNG(getContainer(), getViewContext().getResponse(), assay, config);
        return null;
    }

    protected User getGraphUser()
    {
        return getViewContext().getUser();
    }

    protected NabGraph.Config getGraphConfig(GraphForm form, DilutionAssayRun assay)
    {
        NabGraph.Config config = new NabGraph.Config();
        config.setCutoffs(assay.getCutoffs());
        config.setLockAxes(assay.isLockAxes());
        config.setFirstSample(form.getFirstSample());
        config.setMaxSamples(form.getMaxSamples());
        if (form.getHeight() > 0)
            config.setHeight(form.getHeight());
        if (form.getWidth() > 0)
            config.setWidth(form.getWidth());
        config.setDataIdentifier(form.getDataIdentifier());

        return config;
    }

    protected DilutionAssayRun getAssayRun(ExpRun run, StatsService.CurveFitType fit, User user) throws ExperimentException
    {
        try
        {
            AssayProvider provider = AssayService.get().getProvider(run.getProtocol());
            if (!(provider instanceof DilutionAssayProvider))
                throw new IllegalArgumentException("Run " + run.getRowId() + " is not a NAb run.");

            return ((DilutionAssayProvider)provider).getDataHandler().getAssayResults(run, user, fit);
        }
        catch (DilutionDataHandler.MissingDataFileException e)
        {
            throw new NotFoundException(e.getMessage());
        }
    }

    public NavTree appendNavTrail(NavTree root)
    {
        throw new UnsupportedOperationException();
    }
}
