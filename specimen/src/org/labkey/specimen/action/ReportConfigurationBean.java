package org.labkey.specimen.action;

import org.labkey.api.query.CustomView;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.specimen.SpecimenQuerySchema;
import org.labkey.specimen.report.SpecimenVisitReportParameters;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ViewContext;
import org.labkey.specimen.report.participant.ParticipantSiteReportFactory;
import org.labkey.specimen.report.participant.ParticipantSummaryReportFactory;
import org.labkey.specimen.report.participant.ParticipantTypeReportFactory;
import org.labkey.specimen.report.request.RequestEnrollmentSiteReportFactory;
import org.labkey.specimen.report.request.RequestLocationReportFactory;
import org.labkey.specimen.report.request.RequestParticipantReportFactory;
import org.labkey.specimen.report.request.RequestReportFactory;
import org.labkey.specimen.report.specimentype.TypeCohortReportFactory;
import org.labkey.specimen.report.specimentype.TypeParticipantReportFactory;
import org.labkey.specimen.report.specimentype.TypeSummaryReportFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReportConfigurationBean
{
    private static final String COUNTS_BY_DERIVATIVE_TYPE_TITLE = "Collected Vials by Type and Timepoint";
    private static final String REQUESTS_BY_DERIVATIVE_TYPE_TITLE = "Requested Vials by Type and Timepoint";

    private final Map<String, List<SpecimenVisitReportParameters>> _reportFactories = new LinkedHashMap<>();
    private final int _uniqueId;
    private final ViewContext _viewContext;

    private boolean _listView = true;
    private boolean _hasReports = true;

    public ReportConfigurationBean(ViewContext viewContext)
    {
        Study study = StudyService.get().getStudy(viewContext.getContainer());
        _viewContext = viewContext;
        registerReportFactory(COUNTS_BY_DERIVATIVE_TYPE_TITLE, new TypeSummaryReportFactory());
        registerReportFactory(COUNTS_BY_DERIVATIVE_TYPE_TITLE, new TypeParticipantReportFactory());
        if (StudyService.get().showCohorts(_viewContext.getContainer(), _viewContext.getUser()))
            registerReportFactory(COUNTS_BY_DERIVATIVE_TYPE_TITLE, new TypeCohortReportFactory());
        if (study != null)
        {
            boolean enableSpecimenRequest = SettingsManager.get().getRepositorySettings(study.getContainer()).isEnableRequests();
            if (!study.isAncillaryStudy() && !study.isSnapshotStudy() && enableSpecimenRequest)
            {
                registerReportFactory(REQUESTS_BY_DERIVATIVE_TYPE_TITLE, new RequestReportFactory());
                registerReportFactory(REQUESTS_BY_DERIVATIVE_TYPE_TITLE, new RequestLocationReportFactory());
                registerReportFactory(REQUESTS_BY_DERIVATIVE_TYPE_TITLE, new RequestEnrollmentSiteReportFactory());
                registerReportFactory(REQUESTS_BY_DERIVATIVE_TYPE_TITLE, new RequestParticipantReportFactory());
            }
            String subjectNoun = StudyService.get().getSubjectNounSingular(viewContext.getContainer());
            registerReportFactory("Collected Vials by " + subjectNoun + " by Timepoint", new ParticipantSummaryReportFactory());
            registerReportFactory("Collected Vials by " + subjectNoun + " by Timepoint", new ParticipantTypeReportFactory());
            registerReportFactory("Collected Vials by " + subjectNoun + " by Timepoint", new ParticipantSiteReportFactory());
        }

        _uniqueId = 0;
    }

    // TODO: listView parameter is always false... remove?
    public ReportConfigurationBean(SpecimenVisitReportParameters singleFactory, boolean listView, int uniqueId)
    {
        _listView = listView;
        _viewContext = singleFactory.getViewContext();
        assert (_viewContext != null) : "Expected report factory to be instantiated by Spring.";
        registerReportFactory(COUNTS_BY_DERIVATIVE_TYPE_TITLE, singleFactory);
        _hasReports = StudyService.get().getStudy(_viewContext.getContainer()) != null && !singleFactory.getReports().isEmpty();
        _uniqueId = uniqueId;
    }

    private void registerReportFactory(String category, SpecimenVisitReportParameters factory)
    {
        // we have to explicitly set the view context for these reports, since the factories aren't being newed-up by Spring in the usual way:
        factory.setViewContext(_viewContext);
        List<SpecimenVisitReportParameters> factories = _reportFactories.get(category);

        if (factories == null)
        {
            factories = new ArrayList<>();
            _reportFactories.put(category, factories);
        }

        factories.add(factory);
    }

    public Set<String> getCategories()
    {
        return _reportFactories.keySet();
    }

    public List<SpecimenVisitReportParameters> getFactories(String category)
    {
        return _reportFactories.get(category);
    }

    public boolean isListView()
    {
        return _listView;
    }

    public Map<String, CustomView> getCustomViews(ViewContext context)
    {
        // 13485 - Use provider to handle NULL study
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), SchemaKey.fromParts(SpecimenQuerySchema.SCHEMA_NAME));
        QueryDefinition def = QueryService.get().createQueryDefForTable(schema, "SpecimenDetail");
        return def.getCustomViews(context.getUser(), context.getRequest(), false, false);
    }

    public boolean hasReports()
    {
        return _hasReports;
    }

    public int getUniqueId()
    {
        return _uniqueId;
    }
}
