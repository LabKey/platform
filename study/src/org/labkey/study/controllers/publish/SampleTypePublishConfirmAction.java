package org.labkey.study.controllers.publish;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.ExpMaterialProtocolInputTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.publish.AbstractPublishConfirmAction;
import org.labkey.api.study.publish.PublishConfirmForm;
import org.labkey.api.study.publish.PublishKey;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.study.query.PublishResultsQueryView;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.study.query.PublishResultsQueryView.ExtraColFieldKeys;
import static org.labkey.api.util.PageFlowUtil.urlProvider;

@RequiresPermission(InsertPermission.class)
public class SampleTypePublishConfirmAction extends AbstractPublishConfirmAction<SampleTypePublishConfirmAction.SampleTypePublishConfirmForm>
{
    private ExpSampleType _sampleType;
    private Map<ExtraColFieldKeys, FieldKey> _fieldKeyMap;
    private final String ROW_ID = ExpMaterialTable.Column.RowId.toString();

    public static class SampleTypePublishConfirmForm extends PublishConfirmForm
    {
        private ExpSampleType _sampleType;

        public ExpSampleType getSampleType()
        {
            if (_sampleType == null)
            {
                if (getRowId() != null)
                    _sampleType = SampleTypeService.get().getSampleType(getContainer(), getUser(), getRowId());
                else
                    throw new NotFoundException("Sample Type ID not specified.");

                if (_sampleType == null)
                    throw new NotFoundException("Sample Type " + getRowId() + " does not exist in " + getContainer().getPath());
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
                    public FieldKey getParticipantIDFieldKey(Map<ExtraColFieldKeys, FieldKey> fieldKeyMap)
                    {
                        return fieldKeyMap.get(ExtraColFieldKeys.ParticipantId);
                    }
                    @Override
                    public FieldKey getVisitIDFieldKey(Map<ExtraColFieldKeys, FieldKey> fieldKeyMap)
                    {
                        return fieldKeyMap.get(ExtraColFieldKeys.VisitId);
                    }
                    @Override
                    public FieldKey getDateFieldKey(Map<ExtraColFieldKeys, FieldKey> fieldKeyMap)
                    {
                        return fieldKeyMap.get(ExtraColFieldKeys.Date);
                    }
                },
        UserSpecified
                {
                    @Override
                    public FieldKey getParticipantIDFieldKey(Map<ExtraColFieldKeys, FieldKey> fieldKeyMap)
                    {
                        return null;
                    }
                    @Override
                    public FieldKey getVisitIDFieldKey(Map<ExtraColFieldKeys, FieldKey> fieldKeyMap)
                    {
                        return null;
                    }
                    @Override
                    public FieldKey getDateFieldKey(Map<ExtraColFieldKeys, FieldKey> fieldKeyMap)
                    {
                        return null;
                    }
                };

        // In order to match PublishConfirmForm.DefaultValueSource, we use trivial implementations
        // of functions here that simply return what they are given
        public abstract FieldKey getParticipantIDFieldKey(Map<ExtraColFieldKeys, FieldKey> fieldKeyMap);
        public abstract FieldKey getVisitIDFieldKey(Map<ExtraColFieldKeys, FieldKey> fieldKeyMap);
        public abstract FieldKey getDateFieldKey(Map<ExtraColFieldKeys, FieldKey> fieldKeyMap);
    }

    private void initializeFieldKeys(SampleTypePublishConfirmForm form)
    {
        if (_fieldKeyMap == null)
            _fieldKeyMap = StudyPublishService.get().getSamplePublishFieldKeys(getUser(), getContainer(), form.getSampleType(), getQuerySettings(form));
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
        initializeFieldKeys(form);
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
        return urlProvider(StudyUrls.class).getLinkToStudyConfirmURL(getContainer(), _sampleType).deleteParameters();
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
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.ParticipantId, defaultValueSource.getParticipantIDFieldKey(_fieldKeyMap));
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.VisitId, defaultValueSource.getVisitIDFieldKey(_fieldKeyMap));
        additionalCols.put(PublishResultsQueryView.ExtraColFieldKeys.Date, defaultValueSource.getDateFieldKey(_fieldKeyMap));
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
        List<? extends ExpMaterial> samples = ExperimentService.get().getExpMaterials(dataKeys.keySet());
        SampleTypeService sampleService = SampleTypeService.get();
        Collection<? extends ExpMaterial> unlinkableSamples = sampleService.getSamplesNotPermitted(samples, SampleTypeService.SampleOperations.LinkToStudy);
        if (!unlinkableSamples.isEmpty())
            errors.add(sampleService.getOperationNotPermittedMessage(unlinkableSamples, SampleTypeService.SampleOperations.LinkToStudy));

        for (PublishKey publishKey : dataKeys.values())
        {
            Map<String, Object> dataMap = new HashMap<>();

            Container targetStudyContainer = targetStudy;
            if (publishKey.getTargetStudy() != null)
                targetStudyContainer = publishKey.getTargetStudy();
            assert targetStudyContainer != null;

            String sourceLSID = _sampleType.getLSID();

            dataMap.put(StudyPublishService.PARTICIPANTID_PROPERTY_NAME, publishKey.getParticipantId());
            dataMap.put(StudyPublishService.DATE_PROPERTY_NAME, publishKey.getDate());
            dataMap.put(StudyPublishService.SEQUENCENUM_PROPERTY_NAME, publishKey.getVisitId());
            dataMap.put(StudyPublishService.SOURCE_LSID_PROPERTY_NAME, sourceLSID);
            dataMap.put(ROW_ID, publishKey.getDataId());

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

        return StudyPublishService.get().publishData(
                getUser(),
                form.getContainer(),
                targetStudy,
                form.getAutoLinkCategory(),
                sampleType.getName(),
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
        setHelpTopic("linkSampleData");
        root.addChild("Sample Types", urlProvider(ExperimentUrls.class).getShowSampleTypeListURL(getContainer()));
        if (_sampleType != null)
            root.addChild(_sampleType.getName(), urlProvider(ExperimentUrls.class).getShowSampleTypeURL(_sampleType));
        if (_targetStudyName != null)
            root.addChild("Link to " + _targetStudyName + ": Verify Results");
    }
}
