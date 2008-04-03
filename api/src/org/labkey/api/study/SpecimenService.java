package org.labkey.api.study;

import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;

import java.sql.SQLException;
import java.util.Date;

/**
 * User: brittp
 * Date: Oct 2, 2007
 * Time: 3:35:45 PM
 */
public class SpecimenService
{
    private static SpecimenService.Service _serviceImpl;

    public interface SampleInfo
    {
        String getParticipantId();
        Double getSequenceNum();
        String getSampleId();
    }

    public enum CompletionType
    {
        SpecimenGlobalUniqueId,
        ParticipantId,
        VisitId
    }

    public interface Service
    {
        /** Does a search for matching GlobalUniqueIds or SpecimenNumbers */
        ParticipantVisit getSampleInfo(Container studyContainer, String id) throws SQLException;

        ParticipantVisit getSampleInfo(Container studyContainer, String participantId, Date date) throws SQLException;

        ParticipantVisit getSampleInfo(Container studyContainer, String participantId, Double visit) throws SQLException;

        String getCompletionURLBase(Container studyContainer, CompletionType type);

        Lsid getSpecimenMaterialLsid(Container studyContainer, String id);
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }
}
