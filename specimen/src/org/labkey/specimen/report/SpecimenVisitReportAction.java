package org.labkey.specimen.report;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.specimen.actions.ReportConfigurationBean;
import org.labkey.specimen.actions.SpecimenController2;
import org.labkey.specimen.actions.SpecimenController2.AutoReportListAction;
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
        ActionURL overviewURL = new ActionURL(SpecimenController2.OverviewAction.class, c);
        root.addChild("Specimen Overview", overviewURL);
        root.addChild("Available Reports", new ActionURL(AutoReportListAction.class, c));
        root.addChild("Specimen Report: " + _form.getLabel());
    }
}
