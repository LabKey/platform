package org.labkey.experiment.publish;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.ExpMaterialProtocolInputTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.ui.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.publish.PublishKey;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.study.query.PublishResultsQueryView;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.study.publish.AbstractPublishConfirmAction;
import org.labkey.api.study.publish.PublishConfirmForm;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.PageFlowUtil.urlProvider;

@RequiresPermission(InsertPermission.class)
public class SampleTypePublishConfirmAction extends AbstractPublishConfirmAction<SampleTypePublishConfirmAction.SampleTypePublishConfirmForm>
{
    private ExpSampleType _sampleType;
    private FieldKey _participantId;
    private FieldKey _visitId;
    private FieldKey _date;

    private final String ROW_ID = ExpMaterialTable.Column.RowId.toString();

    public static class SampleTypePublishConfirmForm extends PublishConfirmForm
    {
        private ExpSampleType _sampleType;

        public ExpSampleType getSampleType()
        {
            if (_sampleType == null)
            {
                if (getRowId() != null)
                    _sampleType = SampleTypeService.get().getSampleType(getContainer(), getRowId());
                else
                    throw new NotFoundException("Sample Type ID not specified.");

                if (_sampleType == null)
                    throw new NotFoundException("Sample Type " + getRowId() + " does not exist in " + getContainer().getPath());

                if (!_sampleType.getContainer().hasPermission(getViewContext().getUser(), ReadPermission.class))
                    throw new UnauthorizedException();
            }
            return _sampleType;
        }

        public void setSampleType(ExpSampleType sampleType)
        {
            _sampleType = sampleType;
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
                    public FieldKey getParticipantIDFieldKey(FieldKey participantId)
                    {
                        return participantId;
                    }
                    @Override
                    public FieldKey getVisitIDFieldKey(FieldKey visitId)
                    {
                        return visitId;
                    }
                },
        UserSpecified
                {
                    @Override
                    public FieldKey getParticipantIDFieldKey(FieldKey participantId)
                    {
                        return null;
                    }
                    @Override
                    public FieldKey getVisitIDFieldKey(FieldKey visitId)
                    {
                        return null;
                    }
                };

        // In order to match PublishConfirmForm.DefaultValueSource, we use trivial implementations
        // of functions here that simply return what they are given
        public abstract FieldKey getParticipantIDFieldKey(FieldKey participantId);
        public abstract FieldKey getVisitIDFieldKey(FieldKey visitId);
    }

    private void initializeFieldKeys(ExpSampleType sampleType)
    {
        if (null == _participantId || (null == _visitId && null == _date))
        {
            String visitURI = PropertyType.VISIT_CONCEPT_URI;
            String particpantURI = PropertyType.PARTICIPANT_CONCEPT_URI;

            UserSchema userSchema = QueryService.get().getUserSchema(getUser(), getContainer(), SamplesSchema.SCHEMA_NAME);
            TableInfo tableInfo = userSchema.getTable(sampleType.getName());
            if (tableInfo == null)
                throw new IllegalStateException(String.format("Sample Type %s not found", sampleType.getName()));
            List<ColumnInfo> columnInfoList = tableInfo.getColumns();

            // Firstly attempt to use column type to determine participant, visit, and date fields
            for (ColumnInfo ci : columnInfoList)
            {
                String columnURI = ci.getConceptURI();
                FieldKey fieldKey = ci.getFieldKey();

                if (null != columnURI && columnURI.equals(visitURI))
                {
                    if (ci.getJdbcType().isReal() && _visitId == null)
                        _visitId = fieldKey;
                    if (ci.getJdbcType().isDateOrTime() && _date == null)
                        _date = fieldKey;
                }

                if (null != columnURI && columnURI.equals(particpantURI) && _participantId == null)
                {
                    _participantId = fieldKey;
                }
            }

            // Secondly attempt to use column name as a fallback to determine participant, visit, and date fields
            if (_participantId == null || _visitId == null || _date == null) {
                for (ColumnInfo ci : columnInfoList)
                {
                    String columnName = ci.getName();
                    FieldKey fieldKey = ci.getFieldKey();

                    if (columnName.equalsIgnoreCase("participantid") && _participantId == null)
                        _participantId = fieldKey;

                    if (columnName.equalsIgnoreCase("visitid") && _visitId == null)
                        _visitId = fieldKey;

                    if (columnName.equalsIgnoreCase("date") && _date == null)
                        _date = fieldKey;
                }
            }
        }
    }

    @Override
    public void validateCommand(SampleTypePublishConfirmForm form, Errors errors)
    {
        super.validateCommand(form, errors);
        _sampleType = form.getSampleType();
    }

    @Override
    public boolean handlePost(SampleTypePublishConfirmForm form, BindException errors) throws Exception
    {
        initializeFieldKeys(form.getSampleType());
        return super.handlePost(form, errors);
    }

    @Override
    protected SamplesSchema getUserSchema(SampleTypePublishConfirmForm form)
    {
        return (SamplesSchema) QueryService.get().getUserSchema(form.getUser(), getContainer(), SamplesSchema.SCHEMA_SAMPLES);
    }

    @Override
    protected QuerySettings getQuerySettings(SampleTypePublishConfirmForm form)
    {
        String queryName = form.getSampleType().getName();
        QuerySettings qs = new QuerySettings(form.getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, queryName);
        qs.setSchemaName(SamplesSchema.SCHEMA_NAME);
        qs.setQueryName(queryName);

        return qs;
    }

    @Override
    protected boolean isMismatchedSpecimenInfo(SampleTypePublishConfirmForm form)
    {
        return false;
    }

    @Override
    protected ActionURL getPublishHandlerURL(SampleTypePublishConfirmForm form)
    {
        return urlProvider(ExperimentUrls.class).getLinkToStudyConfirmURL(getContainer(), _sampleType).deleteParameters();
    }

    @Override
    protected Dataset.PublishSource getPublishSource(SampleTypePublishConfirmForm form)
    {
        return Dataset.PublishSource.SampleType;
    }

    @Override
    protected FieldKey getObjectIdFieldKey(SampleTypePublishConfirmForm form)
    {
        return FieldKey.fromParts(ROW_ID);
    }

    @Override
    protected Map<PublishResultsQueryView.ExtraColFieldKeys, FieldKey> getAdditionalColumns(SampleTypePublishConfirmForm form)
    {
        String valueSource = form.getDefaultValueSource();
        DefaultValueSource defaultValueSource = DefaultValueSource.valueOf(valueSource);

        Map<PublishResultsQueryView.ExtraColFieldKeys, FieldKey> additionalCols = new HashMap<>();
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.ParticipantId, defaultValueSource.getParticipantIDFieldKey(_participantId));
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.VisitId, defaultValueSource.getVisitIDFieldKey(_visitId));
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.Date, defaultValueSource.getVisitIDFieldKey(_visitId));
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.SourceId, FieldKey.fromParts(
                ExpMaterialProtocolInputTable.Column.SampleSet.name(),
                ExpMaterialProtocolInputTable.Column.RowId.name()));

        return additionalCols;
    }

    @Override
    protected ActionURL linkToStudy(SampleTypePublishConfirmForm form, Container targetStudy, Map<Integer, PublishKey> dataKeys, List<String> errors)
    {
        List<Map<String, Object>> dataMaps = new ArrayList<>();
        Map<Container, Set<Integer>> rowIdsByTargetContainer = new HashMap<>();

        for (PublishKey publishKey : dataKeys.values())
        {
            Map<String, Object> dataMap = new HashMap<>();

            Container targetStudyContainer = targetStudy;
            if (publishKey.getTargetStudy() != null)
                targetStudyContainer = publishKey.getTargetStudy();
            assert targetStudyContainer != null;

            String sourceLSID = _sampleType.getLSID();

            dataMap.put("ParticipantID", publishKey.getParticipantId());
            dataMap.put("Date", publishKey.getDate());
            dataMap.put("SequenceNum", publishKey.getVisitId());
            dataMap.put("SourceLSID", sourceLSID);
            dataMap.put("RowId", publishKey.getDataId());

            Set<Integer> rowIds = rowIdsByTargetContainer.computeIfAbsent(targetStudyContainer, k -> new HashSet<>());
            rowIds.add(publishKey.getDataId());

            dataMaps.add(dataMap);
        }

        ExpSampleType sampleType = form._sampleType;
        StudyPublishService.get().checkForAlreadyLinkedRows(getUser(), Pair.of(Dataset.PublishSource.SampleType, sampleType.getRowId()), errors, rowIdsByTargetContainer);
        if (!errors.isEmpty())
        {
            return null;
        }

        return StudyPublishService.get().publishData(getUser(), form.getContainer(), targetStudy, sampleType.getName(),
                Pair.of(Dataset.PublishSource.SampleType, sampleType.getRowId()),
                dataMaps, ROW_ID, errors);
    }

    @Override
    public ModelAndView getView(SampleTypePublishConfirmForm form, boolean reshow, BindException errors) throws Exception
    {
        if (_sampleType == null)
            return new HtmlView(HtmlString.unsafe("<span class='labkey-error'>Could not resolve the source Sample Type.</span>"));

        return super.getView(form, reshow, errors);
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        setHelpTopic(new HelpTopic("linkSampleData"));
        root.addChild("Sample Types", new ActionURL(ExperimentController.ListSampleTypesAction.class, getContainer()));
        if (_sampleType != null)
            root.addChild(_sampleType.getName(), urlProvider(ExperimentUrls.class).getShowSampleTypeURL(_sampleType));
        if (_targetStudyName != null)
            root.addChild("Link to " + _targetStudyName + ": Verify Results");
    }
}
