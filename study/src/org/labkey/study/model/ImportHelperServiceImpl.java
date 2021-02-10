package org.labkey.study.model;

import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.importer.ImportHelperService;

public class ImportHelperServiceImpl implements ImportHelperService
{
    @Override
    public SequenceNumTranslator getSequenceNumTranslator(Study study)
    {
        return new SequenceNumImportHelper(study, null);
    }

    @Override
    public ParticipantIdTranslator getParticipantIdTranslator(Study study, User user) throws ValidationException
    {
        return new ParticipantIdImportHelper(study, user, null);
    }
}
