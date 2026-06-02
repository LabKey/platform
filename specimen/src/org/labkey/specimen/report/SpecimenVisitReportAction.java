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
package org.labkey.specimen.report;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.specimen.actions.ReportConfigurationBean;
import org.labkey.specimen.actions.SpecimenController;
import org.labkey.specimen.actions.SpecimenController.AutoReportListAction;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

public abstract class SpecimenVisitReportAction<FormType extends SpecimenVisitReportParameters> extends SimpleViewAction<FormType>
{
    private FormType _form;

    public SpecimenVisitReportAction(Class<FormType> beanClass)
    {
        super(beanClass);
    }

    @Override
    public ModelAndView getView(FormType specimenVisitReportForm, BindException errors)
    {
        _form = specimenVisitReportForm;
        if (specimenVisitReportForm.isExcelExport())
        {
            SpecimenReportExcelWriter writer = new SpecimenReportExcelWriter(specimenVisitReportForm);
            writer.write(getViewContext().getResponse());
            return null;
        }
        else
        {
            JspView<FormType> reportView = new JspView<>("/org/labkey/specimen/view/specimenVisitReport.jsp", specimenVisitReportForm);
            reportView.setIsWebPart(false);
            if (this.isPrint())
            {
                return reportView;
            }
            else
            {
                // Need unique id only in webpart case
                ReportConfigurationBean bean = new ReportConfigurationBean(specimenVisitReportForm, false, 0);
                return new VBox(new JspView<>("/org/labkey/specimen/view/autoReportList.jsp", bean), reportView);
            }
        }
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        Container c = getContainer();
        ActionURL overviewURL = new ActionURL(SpecimenController.OverviewAction.class, c);
        root.addChild("Specimen Overview", overviewURL);
        root.addChild("Available Reports", new ActionURL(AutoReportListAction.class, c));
        root.addChild("Specimen Report: " + _form.getLabel());
    }
}
