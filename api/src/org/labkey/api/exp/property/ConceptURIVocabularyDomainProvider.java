package org.labkey.api.exp.property;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentParentFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/*
 * Property fields can have a Vocabulary Domain attached to them. For example, SMILES columns use vocabulary properties to store molecular properties.
 */
public interface ConceptURIVocabularyDomainProvider
{
    @NotNull Domain ensureDomain(@NotNull String propertyName, @NotNull ExpObject expObject, User user);

    @Nullable List<FieldKey> addLookupColumns(@NotNull ExpObject expObject, @NotNull ExpTable expTable, @NotNull MutableColumnInfo parentCol, @NotNull String sourceFieldName);

    @Nullable String getDomainURI(@NotNull String sourceFieldName, @NotNull ExpObject expObject);

    @Nullable String getDomainName(@NotNull String sourceFieldName, @NotNull ExpObject expObject);

    @NotNull DataIteratorBuilder getDataIteratorBuilder(@NotNull DataIteratorBuilder data, @NotNull ExpDataClass expDataClass, @NotNull AttachmentParentFactory attachmentParentFactory, Container container, User user);

    @NotNull Map<String, Object> getUpdateRowProperties(User user, Container c, @NotNull Map<String, Object> rowStripped, Map<String, Object> oldRow, @NotNull AttachmentParentFactory attachmentParentFactory, @NotNull String sourceFieldName, @NotNull String propertyColumnName, boolean hasMultipleSourceFields);

    @NotNull Collection<String> getImportTemplateExcludeColumns(@NotNull String propertyColumnName);

    @NotNull FieldKey getColumnFieldKey(@NotNull ColumnInfo parent, @NotNull PropertyDescriptor pd);
}
