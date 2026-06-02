/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.specimen.actions.ReportConfigurationBean;
import org.labkey.specimen.report.SpecimenVisitReportParameters;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

import java.util.Set;

public class SpecimenReportWebPartFactory extends BaseWebPartFactory
{
    public static final String REPORT_TYPE_PARAMETER_NAME = "reportType";

    public SpecimenReportWebPartFactory()
    {
        super("Specimen Report", true, true, LOCATION_BODY);
    }

    @Override
    public WebPartView getWebPartView(@NotNull ViewContext context, @NotNull Portal.WebPart part)
    {
        SpecimenVisitReportParameters factory = getFactory(context, part);

        JspView<SpecimenVisitReportParameters> reportView = new JspView<>("/org/labkey/specimen/view/specimenVisitReport.jsp", factory);
        WebPartView configView = new JspView<>("/org/labkey/specimen/view/autoReportList.jsp", new ReportConfigurationBean(factory, false, part.getIndex()));
        ActionURL url = new ActionURL(factory.getAction(), context.getContainer());

        VBox outer = new VBox(configView, reportView);
        outer.setTitleHref(url);
        outer.setFrame(WebPartView.FrameType.PORTAL);
        outer.setTitle("Specimen Report: " + factory.getLabel());

        return outer;
    }

    private SpecimenVisitReportParameters getFactory(ViewContext context, Portal.WebPart part)
    {
        @Nullable String name = part.getPropertyMap().get(REPORT_TYPE_PARAMETER_NAME);
        ReportConfigurationBean bean = new ReportConfigurationBean(context);
        Set<String> categories = bean.getCategories();

        // First element of first category is the default (should be "TypeSummary")
        SpecimenVisitReportParameters factory = bean.getFactories(categories.iterator().next()).getFirst();
        assert "TypeSummary".equals(factory.getReportType());

        if (null == name)
            return factory;

        for (String category : categories)
        {
            for (SpecimenVisitReportParameters candidate : bean.getFactories(category))
            {
                if (candidate.getReportType().equals(name))
                    return candidate;
            }
        }

        return factory;
    }

    @Override
    public HttpView<?> getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new JspView<>("/org/labkey/specimen/view/customizeSpecimenReportWebPart.jsp", webPart);
    }
}
