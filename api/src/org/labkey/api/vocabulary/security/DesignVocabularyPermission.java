package org.labkey.api.vocabulary.security;

import org.labkey.api.security.permissions.AbstractPermission;

public class DesignVocabularyPermission extends AbstractPermission
{
    public DesignVocabularyPermission()
    {
        super("Design Vocabularies",
                "May design new vocabularies and modify designs of existing vocabularies.");
    }
}
