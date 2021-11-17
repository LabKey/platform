package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.study.query.PublishResultsQueryView;
import org.labkey.api.util.Pair;
import org.labkey.api.view.DataView;
import org.springframework.beans.MutablePropertyValues;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.study.query.PublishResultsQueryView.ExtraColFieldKeys;

/**
 * Resolver that tries to resolve subject and timepoint information using sample IDs
 */
public class SampleParticipantVisitResolver extends StudyParticipantVisitResolver
{
    private final Map<Integer, Pair<Boolean, ParticipantVisit>> _resolvedSamples = new HashMap<>();
    private Map<ExtraColFieldKeys, FieldKey> _fieldKeyMap;

    public SampleParticipantVisitResolver(Container runContainer, @Nullable Container targetStudyContainer, User user)
    {
        super(runContainer, targetStudyContainer, user);
    }

    @Override
    protected @NotNull ParticipantVisit resolveParticipantVisit(String sampleId, String participantID, Double visitID, Date date, Container targetStudyContainer)
    {
        ParticipantVisitImpl originalInfo = new ParticipantVisitImpl(sampleId, participantID, visitID, date, getRunContainer(), targetStudyContainer);
        if (targetStudyContainer != null && sampleId != null)
        {
            Integer id = Integer.valueOf(sampleId);

            if (_resolvedSamples.containsKey(id))
            {
                ParticipantVisit studyInfo = _resolvedSamples.get(id).getValue();
                return studyInfo != null ? mergeParticipantVisitInfo(originalInfo, studyInfo) : originalInfo;
            }

            ExpMaterial expMaterial = ExperimentService.get().getExpMaterial(id);
            if (expMaterial != null)
            {
                ExpSampleType sampleType = expMaterial.getSampleType();
                if (sampleType != null)
                {
                    Study study = StudyService.get().getStudy(targetStudyContainer);
                    for (Dataset dataset : StudyPublishService.get().getDatasetsForPublishSource(sampleType.getRowId(), Dataset.PublishSource.SampleType))
                    {
                        if (dataset.getContainer() == targetStudyContainer)
                        {
                            TableInfo tableInfo = dataset.getTableInfo(getUser());
                            Filter datasetFilter = new SimpleFilter(FieldKey.fromParts(dataset.getKeyPropertyName()), id);
                            Map<String, Object> row = new TableSelector(tableInfo, datasetFilter, null).getMap();
                            if (row != null && !row.isEmpty())
                            {
                                ParticipantVisit studyInfo =  new ParticipantVisitImpl(sampleId,
                                        String.valueOf(row.get(study.getSubjectColumnName())),
                                        PublishResultsQueryView.convertObjectToDouble(row.get("SequenceNum")),
                                        PublishResultsQueryView.convertObjectToDate(targetStudyContainer, row.get("Date")),
                                        getRunContainer(),
                                        targetStudyContainer);

                                // remember resolved samples
                                _resolvedSamples.put(id, new Pair<>(true, studyInfo));
                                return mergeParticipantVisitInfo(originalInfo, studyInfo);
                            }
                        }
                    }

                    // attempt to match up the subject/timepoint information even if the sample has not been published to
                    // a study yet, this includes traversing any parent lineage samples for corresponding information
                    QuerySettings qs = new QuerySettings(new MutablePropertyValues(), QueryView.DATAREGIONNAME_DEFAULT);
                    qs.setSchemaName(SamplesSchema.SCHEMA_NAME);
                    qs.setQueryName(sampleType.getName());

                    if (_fieldKeyMap == null)
                        _fieldKeyMap = StudyPublishService.get().getSamplePublishFieldKeys(getUser(), getRunContainer(), sampleType, qs);

                    if (!_fieldKeyMap.isEmpty())
                    {
                        UserSchema schema = QueryService.get().getUserSchema(getUser(), getRunContainer(), qs.getSchemaName());
                        if (schema != null)
                        {
                            qs.setBaseFilter(new SimpleFilter(FieldKey.fromParts("RowId"), id));
                            QueryView view = new QueryView(schema, qs, new NullSafeBindException(new Object(), "form"));
                            DataView dataView = view.createDataView();
                            RenderContext ctx = dataView.getRenderContext();
                            Map<FieldKey, ColumnInfo> selectColumns = dataView.getDataRegion().getSelectColumns();

                            try (Results rs = view.getResults())
                            {
                                ctx.setResults(rs);
                                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
                                if (rs.next())
                                {
                                    ctx.setRow(factory.getRowMap(rs));
                                    String ptid = null;
                                    Double visitId = null;
                                    Date visitDate = null;

                                    if (_fieldKeyMap.containsKey(ExtraColFieldKeys.ParticipantId) &&
                                            selectColumns.containsKey(_fieldKeyMap.get(ExtraColFieldKeys.ParticipantId)))
                                    {
                                        var col = selectColumns.get(_fieldKeyMap.get(ExtraColFieldKeys.ParticipantId));
                                        ptid = PublishResultsQueryView.convertObjectToString(PublishResultsQueryView.getColumnValue(col, ctx));
                                    }
                                    if (_fieldKeyMap.containsKey(ExtraColFieldKeys.VisitId) &&
                                            selectColumns.containsKey(_fieldKeyMap.get(ExtraColFieldKeys.VisitId)))
                                    {
                                        var col = selectColumns.get(_fieldKeyMap.get(ExtraColFieldKeys.VisitId));
                                        visitId = PublishResultsQueryView.convertObjectToDouble(PublishResultsQueryView.getColumnValue(col, ctx));
                                    }
                                    if (_fieldKeyMap.containsKey(ExtraColFieldKeys.Date) &&
                                            selectColumns.containsKey(_fieldKeyMap.get(ExtraColFieldKeys.Date)))
                                    {
                                        var col = selectColumns.get(_fieldKeyMap.get(ExtraColFieldKeys.Date));
                                        visitDate = PublishResultsQueryView.convertObjectToDate(getRunContainer(), PublishResultsQueryView.getColumnValue(col, ctx));
                                    }

                                    ParticipantVisit studyInfo =  new ParticipantVisitImpl(sampleId, ptid, visitId, visitDate,
                                            getRunContainer(),
                                            targetStudyContainer);

                                    // remember resolved samples
                                    _resolvedSamples.put(id, new Pair<>(false, studyInfo));
                                    return mergeParticipantVisitInfo(originalInfo, studyInfo);
                                }
                            }
                            catch (Exception e)
                            {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }

            // cache the miss
            _resolvedSamples.put(id, new Pair<>(false, null));
        }
        return originalInfo;
    }

    /**
     * Does the sample exist in the target study (in one of the sample type datasets)
     */
    public boolean isSampleMatched(Integer sampleId)
    {
        if (_resolvedSamples.containsKey(sampleId))
        {
            Pair<Boolean, ParticipantVisit> studyInfo = _resolvedSamples.get(sampleId);
            return studyInfo.getKey();
        }
        return false;
    }
}
