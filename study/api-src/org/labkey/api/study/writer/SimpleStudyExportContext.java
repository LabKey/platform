package org.labkey.api.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.LoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.specimen.Vial;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.xml.StudyDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ExportContext with the minimal number of study-related references needed for specimen exports
 */
public class SimpleStudyExportContext extends AbstractContext
{
    private Set<Integer> _visitIds = null;
    private List<String> _participants = new ArrayList<>();
    private List<Vial> _vials = null;

    protected SimpleStudyExportContext(User user, Container c, StudyDocument studyDoc, Set<String> dataTypes, LoggerGetter logger, @Nullable VirtualFile root)
    {
        super(user, c, studyDoc, dataTypes, logger, root);
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

    public List<Vial> getVials()
    {
        return _vials;
    }

    public void setVials(List<Vial> vials)
    {
        _vials = vials;
    }
}
