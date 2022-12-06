package org.labkey.api.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.LoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.data.PHI;
import org.labkey.api.security.User;
import org.labkey.api.study.model.ParticipantMapper;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.xml.StudyDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ExportContext with the minimal number of study-related references needed for specimen exports
 */
public class SimpleStudyExportContext extends AbstractStudyContext
{
    private Set<Integer> _visitIds = null;
    private List<String> _participants = new ArrayList<>();

    private final PHI _phiLevel;
    private final ParticipantMapper _participantMapper;
    private final boolean _maskClinic;

    protected SimpleStudyExportContext(User user, Container c, StudyDocument studyDoc, Set<String> dataTypes, PHI phiLevel, ParticipantMapper participantMapper, boolean maskClinic, LoggerGetter logger, @Nullable VirtualFile root)
    {
        super(user, c, studyDoc, dataTypes, logger, root);
        _phiLevel = phiLevel;
        _participantMapper = participantMapper;
        _maskClinic = maskClinic;
    }

    public Set<Integer> getVisitIds()
    {
        return _visitIds;
    }

    public void setVisitIds(Set<Integer> visits)
    {
        _visitIds = visits;
    }

    public List<String> getParticipants()
    {
        return _participants;
    }

    public void setParticipants(List<String> participants)
    {
        _participants = participants;
    }

    @Override
    public PHI getPhiLevel()
    {
        return _phiLevel;
    }

    @Override
    public boolean isAlternateIds()
    {
        return getParticipantMapper().isAlternateIds();
    }

    @Override
    public boolean isMaskClinic()
    {
        return _maskClinic;
    }

    @Override
    public boolean isShiftDates()
    {
        return getParticipantMapper().isShiftDates();
    }

    public ParticipantMapper getParticipantMapper()
    {
        return _participantMapper;
    }
}
