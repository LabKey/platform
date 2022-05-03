package org.labkey.api.exp.property;

import org.labkey.api.attachments.AttachmentParentFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ConceptURIVocabularyDomainProvider
{
    Domain ensureVocabularyDomain(String propertyName, ExpObject expObject, User user);

    List<FieldKey> addVocabularyLookupColumns(ExpObject expObject, ExpTable expTable, MutableColumnInfo parentCol, String sourceFieldName);

    String getVocabularyDomainURI(String smilesFieldName, ExpObject expObject);

    String getVocabularyDomainName(String sourceFieldName, ExpObject expObject);

    DataIteratorBuilder getDataIteratorBuilder(DataIteratorBuilder data, ExpDataClass expDataClass, AttachmentParentFactory attachmentParentFactory, Container container, User user);

    Map<String, Object> getUpdateRowProperties(User user, Container c, Map<String, Object> rowStripped, Map<String, Object> oldRow, AttachmentParentFactory attachmentParentFactory, String sourceFieldName, String propertyColumnName, boolean hasMultipleSourceFields);

    Collection<String> getImportTemplateExcludeColumns(String propertyColumnName);
}
