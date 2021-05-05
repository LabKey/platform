package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.study.query.PublishResultsQueryView;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolver that tries to resolve subject and timepoint information using sample IDs
 */
public class SampleParticipantVisitResolver extends StudyParticipantVisitResolver
{
    private final Map<Integer, ParticipantVisit> _resolvedSamples = new HashMap<>();

    public SampleParticipantVisitResolver(Container runContainer, @Nullable Container targetStudyContainer, User user)
    {
        super(runContainer, targetStudyContainer, user);
    }

    @Override
    protected @NotNull ParticipantVisit resolveParticipantVisit(String sampleId, String participantID, Double visitID, Date date, Container targetStudyContainer)
    {
        ParticipantVisitImpl originalInfo = new ParticipantVisitImpl(sampleId, participantID, visitID, date, getRunContainer(), targetStudyContainer);
        if (targetStudyContainer != null)
        {
            Integer id = Integer.valueOf(sampleId);

            if (_resolvedSamples.containsKey(id))
                return mergeParticipantVisitInfo(originalInfo, _resolvedSamples.get(id));

            ExpMaterial expMaterial = ExperimentService.get().getExpMaterial(id);
            if (expMaterial != null)
            {
                ExpSampleType sampleType = expMaterial.getSampleType();
                if (sampleType != null)
                {
                    for (Dataset dataset : StudyPublishService.get().getDatasetsForPublishSource(sampleType.getRowId(), Dataset.PublishSource.SampleType))
                    {
                        if (dataset.getContainer() == targetStudyContainer)
                        {
                            TableInfo tableInfo = dataset.getTableInfo(getUser(), false);
                            Filter datasetFilter = new SimpleFilter(FieldKey.fromParts(dataset.getKeyPropertyName()), id);
                            Map<String, Object> row = new TableSelector(tableInfo, datasetFilter, null).getMap();
                            if (row != null && !row.isEmpty())
                            {
                                Study study = StudyService.get().getStudy(targetStudyContainer);
                                ParticipantVisit studyInfo =  new ParticipantVisitImpl(sampleId,
                                        String.valueOf(row.get(study.getSubjectColumnName())),
                                        PublishResultsQueryView.convertObjectToDouble(row.get("SequenceNum")),
                                        PublishResultsQueryView.convertObjectToDate(targetStudyContainer, row.get("Date")),
                                        getRunContainer(),
                                        targetStudyContainer);

                                // remember resolved samples
                                _resolvedSamples.put(id, studyInfo);
                                return mergeParticipantVisitInfo(originalInfo, studyInfo);
                            }
                        }
                    }
                }
            }

            // cache the miss
            _resolvedSamples.put(id, originalInfo);
        }
        return originalInfo;
    }

    /**
     * Does the sample exist in the target study (in one of the sample type datasets)
     */
    public boolean isSampleMatched(Integer sampleId)
    {
        return _resolvedSamples.containsKey(sampleId);
    }
}
