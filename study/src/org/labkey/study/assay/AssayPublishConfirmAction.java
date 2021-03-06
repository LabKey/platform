package org.labkey.study.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayTableMetadata;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
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
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.publish.AbstractPublishConfirmAction;
import org.labkey.study.publish.PublishConfirmForm;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiresPermission(InsertPermission.class)
public class AssayPublishConfirmAction extends AbstractPublishConfirmAction<AssayPublishConfirmAction.AssayPublishConfirmForm>
{
    private ExpProtocol _protocol;
    private AssayProtocolSchema _protocolSchema;

    public static class AssayPublishConfirmForm extends PublishConfirmForm
    {
        private Integer _rowId;
        private ExpProtocol _protocol;
        private AssayProvider _provider;

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

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

    public enum DefaultValueSource
    {
        Assay
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
    protected ActionURL getPublishHandlerURL(AssayPublishConfirmForm form)
    {
        return PageFlowUtil.urlProvider(StudyUrls.class).getCopyToStudyConfirmURL(getContainer(), _protocol).deleteParameters();
    }

    @Override
    protected FieldKey getObjectIdFieldKey(AssayPublishConfirmForm form)
    {
        return form.getProvider().getTableMetadata(_protocol).getResultRowIdFieldKey();
    }

    @Override
    protected Map<PublishResultsQueryView.ExtraColFieldKeys, FieldKey> getAdditionalColumns(AssayPublishConfirmForm form)
    {
        Map<PublishResultsQueryView.ExtraColFieldKeys, FieldKey> additionalCols = new HashMap<>();
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        String valueSource = form.getDefaultValueSource();
        DefaultValueSource defaultValueSource = DefaultValueSource.valueOf(valueSource);

        Pair<ExpProtocol.AssayDomainTypes, DomainProperty> targetStudyDomainProperty = provider.findTargetStudyProperty(_protocol);
        AssayTableMetadata tableMetadata = provider.getTableMetadata(_protocol);

        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.RunId, tableMetadata.getRunRowIdFieldKeyFromResults());
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.ObjectId, tableMetadata.getResultRowIdFieldKey());

        // TODO : can we transition away from using the defaultValueSource and just query the tableMetadata
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.ParticipantId, defaultValueSource.getParticipantIDFieldKey(tableMetadata));
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.VisitId, defaultValueSource.getVisitIDFieldKey(tableMetadata, TimepointType.VISIT));
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.Date, defaultValueSource.getVisitIDFieldKey(tableMetadata, TimepointType.DATE));

        FieldKey specimenIDFieldKey = tableMetadata.getSpecimenIDFieldKey();
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.SpecimenId, specimenIDFieldKey);
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.SpecimenMatch, new FieldKey(tableMetadata.getSpecimenIDFieldKey(), AbstractAssayProvider.ASSAY_SPECIMEN_MATCH_COLUMN_NAME));
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.SpecimenPtid, new FieldKey(new FieldKey(specimenIDFieldKey, "Specimen"), "ParticipantID"));
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.SpecimenVisit, new FieldKey(new FieldKey(specimenIDFieldKey, "Specimen"), "SequenceNum"));
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.SpecimenDate, new FieldKey(new FieldKey(specimenIDFieldKey, "Specimen"), "DrawTimestamp"));

        // Add the TargetStudy FieldKey only if it exists on the Result domain.
        if (targetStudyDomainProperty != null && targetStudyDomainProperty.first == ExpProtocol.AssayDomainTypes.Result)
        {
            additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.TargetStudy, tableMetadata.getTargetStudyFieldKey());
        }
        return additionalCols;
    }

    @Override
    protected Map<String, Object> getHiddenFormFields(AssayPublishConfirmForm form)
    {
        Map<String, Object> fields = new HashMap<>();

        fields.put("rowId", _protocol.getRowId());
        String returnURL = getViewContext().getRequest().getParameter(ActionURL.Param.returnUrl.name());
        if (returnURL == null)
        {
            returnURL = getViewContext().getActionURL().toString();
        }
        fields.put(ActionURL.Param.returnUrl.name(), returnURL);

        return fields;
    }

    @Override
    protected ActionURL copyToStudy(AssayPublishConfirmForm form, Container targetStudy, Map<Integer, PublishKey> publishData, List<String> publishErrors)
    {
        return form.getProvider().copyToStudy(getUser(), getContainer(), _protocol, targetStudy, publishData, publishErrors);
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
            root.addChild("Copy to " + (_targetStudyName == null ? "Study" : _targetStudyName) + ": Verify Results");
    }
}
