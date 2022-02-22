package org.labkey.specimen.actions;

import org.apache.commons.lang3.time.DateUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.annotations.Migrate;
import org.labkey.api.data.Container;
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.model.SpecimenComment;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyInternalService;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.ViewContext;
import org.labkey.specimen.SpecimenManager;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class UpdateSpecimenCommentsBean extends SpecimensViewBean
{
    private final String _referrer;
    private final String _currentComment;
    private final boolean _mixedComments;
    private final boolean _currentFlagState;
    private final boolean _mixedFlagState;
    private final Map<String, Map<String, Long>> _participantVisitMap;

    public UpdateSpecimenCommentsBean(ViewContext context, List<Vial> vials, String referrer)
    {
        super(context, vials, false, false, true, true);
        _referrer = referrer;
        Map<Vial, SpecimenComment> currentComments = SpecimenManager.get().getSpecimenComments(vials);
        boolean mixedComments = false;
        boolean mixedFlagState = false;
        SpecimenComment prevComment = currentComments.get(vials.get(0));

        for (int i = 1; i < vials.size() && (!mixedFlagState || !mixedComments); i++)
        {
            SpecimenComment comment = currentComments.get(vials.get(i));

            // a missing comment indicates a 'false' for history conflict:
            boolean currentFlagState = comment != null && comment.isQualityControlFlag();
            boolean previousFlagState = prevComment != null && prevComment.isQualityControlFlag();
            mixedFlagState = mixedFlagState || currentFlagState != previousFlagState;
            String currentCommentString = comment != null ? comment.getComment() : null;
            String previousCommentString = prevComment != null ? prevComment.getComment() : null;
            mixedComments = mixedComments || !Objects.equals(previousCommentString, currentCommentString);
            prevComment = comment;
        }

        _currentComment = !mixedComments && prevComment != null ? prevComment.getComment() : null;
        _currentFlagState = mixedFlagState || (prevComment != null && prevComment.isQualityControlFlag());
        _mixedComments = mixedComments;
        _mixedFlagState = mixedFlagState;
        _participantVisitMap = generateParticipantVisitMap(vials, SpecimenController.getStudyRedirectIfNull(context.getContainer()));
    }

    protected Map<String, Map<String, Long>> generateParticipantVisitMap(List<Vial> vials, Study study)
    {
        Map<String, Map<String, Long>> pvMap = new TreeMap<>();

        if (TimepointType.CONTINUOUS == study.getTimepointType())
            return Collections.emptyMap();

        boolean isDateStudy = TimepointType.DATE == study.getTimepointType();
        Date startDate = isDateStudy ? study.getStartDate() : new Date();

        for (Vial vial : vials)
        {
            Double visit;
            if (isDateStudy)
                if (null != vial.getDrawTimestamp())
                    visit = Double.valueOf((vial.getDrawTimestamp().getTime() - startDate.getTime()) / DateUtils.MILLIS_PER_DAY);
                else
                    visit = null;
            else
                visit = vial.getVisitValue();

            if (visit != null)
            {
                String ptid = vial.getPtid();
                BigDecimal sequenceNum = StudyInternalService.get().getSequenceNum(visit);
                Visit v = StudyInternalService.get().getVisitForSequence(study, sequenceNum);
                if (ptid != null && v != null)
                {
                    if (!pvMap.containsKey(ptid))
                        pvMap.put(ptid, new HashMap<>());
                    pvMap.get(ptid).put(v.getDisplayString(), vial.getRowId());
                }
            }
        }
        return pvMap;
    }

    public String getReferrer()
    {
        return _referrer;
    }

    public String getCurrentComment()
    {
        return _currentComment;
    }

    public boolean isMixedComments()
    {
        return _mixedComments;
    }

    public boolean isCurrentFlagState()
    {
        return _currentFlagState;
    }

    public boolean isMixedFlagState()
    {
        return _mixedFlagState;
    }

    public Map<String, Map<String, Long>> getParticipantVisitMap()
    {
        return _participantVisitMap;
    }
}
