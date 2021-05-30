package org.labkey.specimen.action;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.specimen.SpecimenRequestManager;
import org.labkey.api.specimen.actions.ShowSearchAction;
import org.labkey.api.specimen.actions.SpecimenReportActions;
import org.labkey.api.specimen.actions.SpecimenWebPartForm;
import org.labkey.api.specimen.security.permissions.ManageDisplaySettingsPermission;
import org.labkey.api.specimen.settings.RepositorySettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;

// TEMPORARY: Move specimen actions from study SpecimenController to here. Once all actions are moved, we'll rename this.
public class SpecimenController2 extends SpringActionController
{
    private static final DefaultActionResolver _resolver = new DefaultActionResolver(
        SpecimenController2.class,
        ShowSearchAction.class,

        // Report actions from SpecimenReportActions
        SpecimenReportActions.ParticipantSummaryReportAction.class,
        SpecimenReportActions.ParticipantTypeReportAction.class,
        SpecimenReportActions.ParticipantSiteReportAction.class,
        SpecimenReportActions.RequestReportAction.class,
        SpecimenReportActions.RequestEnrollmentSiteReportAction.class,
        SpecimenReportActions.RequestSiteReportAction.class,
        SpecimenReportActions.RequestParticipantReportAction.class,
        SpecimenReportActions.TypeParticipantReportAction.class,
        SpecimenReportActions.TypeSummaryReportAction.class,
        SpecimenReportActions.TypeCohortReportAction.class
    );

    private Study _study = null;

    public SpecimenController2()
    {
        setActionResolver(_resolver);
    }

    @Nullable
    public Study getStudy()
    {
        if (null == _study)
            _study = StudyService.get().getStudy(getContainer());
        return _study;
    }

    @RequiresPermission(ManageDisplaySettingsPermission.class)
    public static class ManageSpecimenWebPartAction extends SimpleViewAction<SpecimenWebPartForm>
    {
        @Override
        public ModelAndView getView(SpecimenWebPartForm form, BindException errors)
        {
            RepositorySettings settings = SettingsManager.get().getRepositorySettings(getContainer());
            ArrayList<String[]> groupings = settings.getSpecimenWebPartGroupings();
            form.setGrouping1(groupings.get(0));
            form.setGrouping2(groupings.get(1));
            form.setColumns(SpecimenRequestManager.get().getGroupedValueAllowedColumns());
            return new JspView<>("/org/labkey/specimen/view/manageSpecimenWebPart.jsp", form);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageSpecimens#group");
            urlProvider(StudyUrls.class).addManageStudyNavTrail(root, getContainer(), getUser());
            root.addChild("Configure Specimen Web Part");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SaveSpecimenWebPartSettingsAction extends MutatingApiAction<SpecimenWebPartForm>
    {
        @Override
        public ApiResponse execute(SpecimenWebPartForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Study study = getStudy();
            if (study != null)
            {
                Container container = getContainer();
                RepositorySettings settings = SettingsManager.get().getRepositorySettings(container);
                ArrayList<String[]> groupings = new ArrayList<>(2);
                groupings.add(form.getGrouping1());
                groupings.add(form.getGrouping2());
                settings.setSpecimenWebPartGroupings(groupings);
                SettingsManager.get().saveRepositorySettings(container, settings);
                response.put("success", true);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }

    @RequiresSiteAdmin
    public static class PivotAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<>("/org/labkey/specimen/view/pivot.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }
}
