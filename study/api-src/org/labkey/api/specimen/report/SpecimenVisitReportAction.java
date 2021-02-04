package org.labkey.api.specimen.report;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.specimen.actions.ReportConfigurationBean;
import org.labkey.api.study.SpecimenUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
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
                return new VBox(new JspView<>("/org/labkey/study/view/specimen/autoReportList.jsp", bean), reportView);
            }
        }
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        PageFlowUtil.urlProvider(SpecimenUrls.class).addSpecimenNavTrail(root, "Specimen Report: " + _form.getLabel(), getContainer());
    }
}
