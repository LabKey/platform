package org.labkey.study.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayTableMetadata;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.ui.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.publish.PublishKey;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.study.query.PublishResultsQueryView;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.study.publish.AbstractPublishConfirmAction;
import org.labkey.api.study.publish.PublishConfirmForm;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.study.query.PublishResultsQueryView.ExtraColFieldKeys;

@RequiresPermission(InsertPermission.class)
public class AssayPublishConfirmAction extends AbstractPublishConfirmAction<AssayPublishConfirmAction.AssayPublishConfirmForm>
{
    private ExpProtocol _protocol;
    private AssayProtocolSchema _protocolSchema;

    public static class AssayPublishConfirmForm extends PublishConfirmForm
    {
        private ExpProtocol _protocol;
        private AssayProvider _provider;

        ExpProtocol getProtocol()
        {
            if (_protocol == null)
            {
                if (getRowId() != null)
                    _protocol = ExperimentService.get().getExpProtocol(getRowId().intValue());
                else
                    throw new NotFoundException("Protocol ID not specified.");

                if (_protocol == null)
                    throw new NotFoundException("Assay " + getRowId() + " does not exist in " + getContainer().getPath());

                // we still make sure that the current user can read from the protocol container:
                if (!_protocol.getContainer().hasPermission(getViewContext().getUser(), ReadPermission.class))
                    throw new UnauthorizedException();

                _provider = AssayService.get().getProvider(_protocol);
            }
            return _protocol;
        }

        @NotNull
        public AssayProvider getProvider()
        {
            if (_provider == null)
            {
                getProtocol();
            }
            return _provider;
        }
    }

    /**
     * Names need to match those found in PublishConfirmForm.DefaultValueSource
     */
    public enum DefaultValueSource
    {
        PublishSource
                {
                    @Override
                    public FieldKey getParticipantIDFieldKey(AssayTableMetadata tableMetadata)
                    {
                        return tableMetadata.getParticipantIDFieldKey();
                    }
                    @Override
                    public FieldKey getVisitIDFieldKey(AssayTableMetadata tableMetadata, TimepointType type)
                    {
                        return tableMetadata.getVisitIDFieldKey(type);
                    }
                },
        Specimen
                {
                    @Override
                    public FieldKey getParticipantIDFieldKey(AssayTableMetadata tableMetadata)
                    {
                        return new FieldKey(tableMetadata.getSpecimenIDFieldKey(), "ParticipantID");
                    }
                    @Override
                    public FieldKey getVisitIDFieldKey(AssayTableMetadata tableMetadata, TimepointType type)
                    {
                        if (type == TimepointType.VISIT)
                        {
                            return new FieldKey(tableMetadata.getSpecimenIDFieldKey(), "Visit");
                        }
                        else
                        {
                            return new FieldKey(tableMetadata.getSpecimenIDFieldKey(), "DrawTimestamp");
                        }
                    }
                },
        UserSpecified
                {
                    @Override
                    public FieldKey getParticipantIDFieldKey(AssayTableMetadata tableMetadata)
                    {
                        return null;
                    }
                    @Override
                    public FieldKey getVisitIDFieldKey(AssayTableMetadata tableMetadata, TimepointType type)
                    {
                        return null;
                    }
                };

        public abstract FieldKey getParticipantIDFieldKey(AssayTableMetadata tableMetadata);
        public abstract FieldKey getVisitIDFieldKey(AssayTableMetadata tableMetadata, TimepointType type);
    }

    @Override
    public void validateCommand(AssayPublishConfirmForm form, Errors errors)
    {
        super.validateCommand(form, errors);
        _protocol = form.getProtocol();
    }

    @Override
    protected UserSchema getUserSchema(AssayPublishConfirmForm form)
    {
        if (_protocolSchema == null)
        {
            AssayProvider provider = form.getProvider();
            _protocolSchema = provider.createProtocolSchema(getUser(), getContainer(), _protocol, _targetStudy);
        }
        return _protocolSchema;
    }

    @Override
    protected QuerySettings getQuerySettings(AssayPublishConfirmForm form)
    {
        if (_protocolSchema == null)
            getUserSchema(form);
        return _protocolSchema.getSettings(getViewContext(), AssayProtocolSchema.DATA_TABLE_NAME, AssayProtocolSchema.DATA_TABLE_NAME);
    }

    @Override
    protected boolean isMismatchedSpecimenInfo(AssayPublishConfirmForm form)
    {
        if (_protocolSchema == null)
            getUserSchema(form);
        return StudyPublishService.get().hasMismatchedInfo(_allObjects, _protocolSchema);
    }

    @Override
    protected boolean showSpecimenMatchColumn(AssayPublishConfirmForm form)
    {
        return true;
    }

    @Override
    protected ActionURL getPublishHandlerURL(AssayPublishConfirmForm form)
    {
        return PageFlowUtil.urlProvider(StudyUrls.class).getLinkToStudyConfirmURL(getContainer(), _protocol).deleteParameters();
    }

    @Override
    protected Dataset.PublishSource getPublishSource(AssayPublishConfirmForm form)
    {
        return Dataset.PublishSource.Assay;
    }

    @Override
    protected FieldKey getObjectIdFieldKey(AssayPublishConfirmForm form)
    {
        return form.getProvider().getTableMetadata(_protocol).getResultRowIdFieldKey();
    }

    @Override
    protected Map<PublishResultsQueryView.ExtraColFieldKeys, FieldKey> getAdditionalColumns(AssayPublishConfirmForm form)
    {
        Map<ExtraColFieldKeys, FieldKey> additionalCols = new HashMap<>();
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        String valueSource = form.getDefaultValueSource();
        DefaultValueSource defaultValueSource = DefaultValueSource.valueOf(valueSource);

        Pair<ExpProtocol.AssayDomainTypes, DomainProperty> targetStudyDomainProperty = provider.findTargetStudyProperty(_protocol);
        AssayTableMetadata tableMetadata = provider.getTableMetadata(_protocol);

        additionalCols.put(ExtraColFieldKeys.SourceId, tableMetadata.getRunRowIdFieldKeyFromResults());
        additionalCols.put(ExtraColFieldKeys.ObjectId, tableMetadata.getResultRowIdFieldKey());

        // TODO : can we transition away from using the defaultValueSource and just query the tableMetadata
        additionalCols.put(ExtraColFieldKeys.ParticipantId, defaultValueSource.getParticipantIDFieldKey(tableMetadata));
        additionalCols.put(ExtraColFieldKeys.VisitId, defaultValueSource.getVisitIDFieldKey(tableMetadata, TimepointType.VISIT));
        additionalCols.put(ExtraColFieldKeys.Date, defaultValueSource.getVisitIDFieldKey(tableMetadata, TimepointType.DATE));

        FieldKey specimenIDFieldKey = tableMetadata.getSpecimenIDFieldKey();
        additionalCols.put(ExtraColFieldKeys.SpecimenId, specimenIDFieldKey);
        additionalCols.put(ExtraColFieldKeys.SpecimenMatch, new FieldKey(tableMetadata.getSpecimenIDFieldKey(), AbstractAssayProvider.ASSAY_SPECIMEN_MATCH_COLUMN_NAME));
        additionalCols.put(ExtraColFieldKeys.SpecimenPtid, new FieldKey(new FieldKey(specimenIDFieldKey, "Specimen"), "ParticipantID"));
        additionalCols.put(ExtraColFieldKeys.SpecimenVisit, new FieldKey(new FieldKey(specimenIDFieldKey, "Specimen"), "SequenceNum"));
        additionalCols.put(ExtraColFieldKeys.SpecimenDate, new FieldKey(new FieldKey(specimenIDFieldKey, "Specimen"), "DrawTimestamp"));

        UserSchema userSchema = getUserSchema(form);
        QueryView view = new QueryView(userSchema, getQuerySettings(form), null);
        DataView dataView = view.createDataView();
        Map<FieldKey, ColumnInfo> selectColumns = dataView.getDataRegion().getSelectColumns();

        // If there are any sample columns in the assay, we can try to match existing samples in the target study
        List<ColumnInfo> sampleCols = selectColumns.values().stream()
                .filter(c -> PropertyType.SAMPLE_CONCEPT_URI.equalsIgnoreCase(c.getConceptURI()))
                .collect(Collectors.toList());

        if (sampleCols.size() == 1)
            additionalCols.put(ExtraColFieldKeys.SampleId, sampleCols.get(0).getFieldKey());

        if (!selectColumns.containsKey(additionalCols.get(ExtraColFieldKeys.Date)))
        {
            // issue 41982 : look for an alternate date column if the standard assay date field does not exist
            List<ColumnInfo> dateCols = selectColumns.values().stream()
                    .filter(c -> JdbcType.TIMESTAMP.equals(c.getJdbcType()) &&
                            (!c.getName().equalsIgnoreCase("Created") && !c.getName().equalsIgnoreCase("Modified")))
                    .collect(Collectors.toList());

            if (dateCols.size() == 1)
                additionalCols.put(ExtraColFieldKeys.Date, dateCols.get(0).getFieldKey());
        }

        // Add the TargetStudy FieldKey only if it exists on the Result domain.
        if (targetStudyDomainProperty != null && targetStudyDomainProperty.first == ExpProtocol.AssayDomainTypes.Result)
        {
            additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.TargetStudy, tableMetadata.getTargetStudyFieldKey());
        }
        return additionalCols;
    }

    /**
     * Specifies the columns in the publish results query view that should not be visible (but still be in the data view)
     * @return
     */
    @Override
    protected Set<String> getHiddenPublishResultsCaptions(AssayPublishConfirmForm form)
    {
        Set<String> hidden = super.getHiddenPublishResultsCaptions(form);

        // unclear why this conditional logic exists, it seems to imply that we may not want to hide this column
        // if the user had added a column in the result domain with the same caption
        Pair<ExpProtocol.AssayDomainTypes, DomainProperty> targetStudyDomainProperty = form.getProvider().findTargetStudyProperty(_protocol);
        if (targetStudyDomainProperty != null && targetStudyDomainProperty.first != ExpProtocol.AssayDomainTypes.Result)
            hidden.add(AbstractAssayProvider.TARGET_STUDY_PROPERTY_CAPTION);

        return hidden;
    }

    @Override
    protected ActionURL linkToStudy(AssayPublishConfirmForm form, Container targetStudy, Map<Integer, PublishKey> publishData, List<String> publishErrors)
    {
        return form.getProvider().linkToStudy(getUser(), getContainer(), _protocol, targetStudy, form.getAutoLinkCategory(), publishData, publishErrors);
    }

    @Override
    public ModelAndView getView(AssayPublishConfirmForm form, boolean reshow, BindException errors) throws Exception
    {
        if (_protocol == null)
            return new HtmlView(HtmlString.unsafe("<span class='labkey-error'>Could not resolve the source protocol.</span>"));

        return super.getView(form, reshow, errors);
    }

    @Override
    public boolean handlePost(AssayPublishConfirmForm form, BindException errors) throws Exception
    {
        return super.handlePost(form, errors);
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        getPageConfig().setHelpTopic(new HelpTopic("publishAssayData"));
        root.addChild("Assay List", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(getContainer()));
        if (_protocol != null)
            root.addChild(_protocol.getName(), PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        if (_targetStudyName != null)
            root.addChild("Link to " + (_targetStudyName == null ? "Study" : _targetStudyName) + ": Verify Results");
    }
}
