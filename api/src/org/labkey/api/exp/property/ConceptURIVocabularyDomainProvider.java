package org.labkey.api.exp.property;

import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.List;

public interface ConceptURIVocabularyDomainProvider
{
    Domain ensureVocabularyDomain(String propertyName, ExpObject expObject, User user);

    List<FieldKey> addVocabularyLookupColumns(ExpObject expObject, ExpTable expTable, MutableColumnInfo parentCol, String sourceFieldName);

    String getVocabularyDomainName(String smilesFieldName, ExpObject expObject);
}
