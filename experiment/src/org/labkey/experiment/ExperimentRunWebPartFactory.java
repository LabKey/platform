/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
package org.labkey.experiment;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.experiment.api.ExpProtocolImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
* User: jeckels
* Date: Jan 19, 2010
*/
public class ExperimentRunWebPartFactory extends BaseWebPartFactory
{
    public static final String EXPERIMENT_RUN_FILTER = "experimentRunFilter";

    public ExperimentRunWebPartFactory()
    {
        super(ExperimentModule.EXPERIMENT_RUN_WEB_PART_NAME, true, false);

    }

    private String getConfiguredRunFilterName(Portal.WebPart webPart)
    {
        return webPart.getPropertyMap().get(EXPERIMENT_RUN_FILTER);
    }

    public static class Bean
    {
        private final Set<ExperimentRunType> _types;
        private final String _defaultRunFilterName;

        public Bean(Set<ExperimentRunType> types, String defaultRunFilterName)
        {
            _types = types;
            _defaultRunFilterName = defaultRunFilterName;
        }

        public String getDefaultRunFilterName()
        {
            return _defaultRunFilterName;
        }

        public Set<ExperimentRunType> getTypes()
        {
            return _types;
        }
    }

    @Override
    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        Set<ExperimentRunType> types = ExperimentService.get().getExperimentRunTypes(context.getContainer());
        return new JspView<>("/org/labkey/experiment/customizeRunWebPart.jsp", new Bean(types, getConfiguredRunFilterName(webPart)));
    }

    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        String selectedTypeName = getConfiguredRunFilterName(webPart);
        Set<ExperimentRunType> types = ExperimentService.get().getExperimentRunTypes(portalCtx.getContainer());

        ExperimentRunType selectedType = ExperimentRunType.getSelectedFilter(types, selectedTypeName);

        if (selectedType == null)
        {
            // Try to find the most specific kind of run that we will be asked to show so we can present the
            // best set of columns
            List<ExpProtocolImpl> protocols = ExperimentServiceImpl.get().getExpProtocolsForRunsInContainer(portalCtx.getContainer());
            if (protocols.size() > 1)
            {
                Set<ExperimentRunType> runTypes = new TreeSet<>();
                for (ExpProtocol protocol : protocols)
                {
                    runTypes.add(ChooseExperimentTypeBean.getBestTypeSelection(types, selectedType, Arrays.asList(protocol)));
                }

                VBox result = new VBox();
                result.setTitle("Experiment Runs");
                result.setFrame(WebPartView.FrameType.PORTAL);
                for (ExperimentRunType runType : runTypes)
                {
                    ExperimentRunListView runView = ExperimentService.get().createExperimentRunWebPart(new ViewContext(portalCtx), runType);
                    if (runType != ExperimentRunType.ALL_RUNS_TYPE)
                    {
                        runView.setTitle(runType.getDescription() + " Runs");
                    }
                    else
                    {
                        runView.setTitle(runType.getDescription());
                    }
                    result.addView(runView);
                }

                return result;
            }
            selectedType = ChooseExperimentTypeBean.getBestTypeSelection(types, selectedType, protocols);
        }

        ExperimentRunListView result = ExperimentService.get().createExperimentRunWebPart(new ViewContext(portalCtx), selectedType);
        if (selectedType != ExperimentRunType.ALL_RUNS_TYPE)
        {
            result.setTitle(result.getTitle() + " (" + selectedType.getDescription() + ")");
        }
        return result;
    }
}
