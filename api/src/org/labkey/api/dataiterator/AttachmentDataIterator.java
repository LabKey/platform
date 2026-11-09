/*
 * Copyright (c) 2016-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.dataiterator;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentParentFactory;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.FileNameUniquifier;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.writer.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttachmentDataIterator extends WrapperDataIterator
{
    final VirtualFile attachmentDir;
    final BatchValidationException errors;
    final int entityIdIndex;
    final ArrayList<_AttachmentUploadHelper> attachmentColumns;
    final Map<String, String> attachmentColumnsAliases;
    final QueryUpdateService.InsertOption insertOption;
    final User user;
    final Container container;
    final AttachmentParentFactory parentFactory;


    AttachmentDataIterator(DataIterator insertIt, BatchValidationException errors,
                           User user,
                           @Nullable VirtualFile attachmentDir,
                           int entityIdIndex,
                           ArrayList<_AttachmentUploadHelper> attachmentColumns,
                           Map<String, String> attachmentColumnsAliases,
                           QueryUpdateService.InsertOption insertOption,
                           Container container,
                           AttachmentParentFactory parentFactory)
    {
        super(insertIt);
        this.attachmentDir = attachmentDir;
        this.errors = errors;
        this.entityIdIndex = entityIdIndex;
        this.attachmentColumns = attachmentColumns;
        this.attachmentColumnsAliases = attachmentColumnsAliases;
        this.insertOption = insertOption;
        this.user = user;
        this.container = container;
        this.parentFactory = parentFactory;
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        boolean ret = super.next();
        if (!ret)
            return false;

        ArrayList<AttachmentFile> attachmentFiles = null;
        List<String> oldAttachments = new ArrayList<>();

        try
        {
            Map<String, Object> existing = getExistingRecord();

            for (_AttachmentUploadHelper p : attachmentColumns)
            {
                Object attachmentValue = get(p.index);
                String oldAttachmentValue = null;
                if (insertOption.allowUpdate && existing != null)
                {
                    // GitHub Issue 915: Bulk edit doesn't completely remove attachments for sources
                    Object oldValue = existing.get(p.domainProperty.getName());
                    if (oldValue == null)
                        oldValue = existing.get(attachmentColumnsAliases.get(p.domainProperty.getName()));
                    if (oldValue != null)
                        oldAttachmentValue = oldValue.toString();
                }

                if (null == attachmentValue)
                {
                    // Remove existing attachment
                    if (!StringUtils.isEmpty(oldAttachmentValue))
                        oldAttachments.add(oldAttachmentValue);
                    continue;
                }

                String filename;
                AttachmentFile attachmentFile;

                if (attachmentValue instanceof String str)
                {
                    if (StringUtils.isEmpty(str))
                    {
                        // Remove existing attachment
                        if (!StringUtils.isEmpty(oldAttachmentValue))
                            oldAttachments.add(oldAttachmentValue);
                        continue;
                    }

                    if (str.equals(oldAttachmentValue)) // no change
                        continue;

                    if (!StringUtils.isEmpty(oldAttachmentValue)) // replace old attachment with new attachment, so mark old attachment for deletion
                        oldAttachments.add(oldAttachmentValue);

                    if (null == attachmentDir)
                    {
                        errors.addRowError(propertyValidationException(p.domainProperty, attachmentValue));
                        return false;
                    }

                    filename = str;
                    InputStream aIS = attachmentDir.getDir(p.domainProperty.getName()).getInputStream(p.uniquifier.uniquify(filename));
                    if (aIS == null)
                    {
                        errors.addRowError(new ValidationException("Could not find referenced file " + filename, p.domainProperty.getName()));
                        return false;
                    }
                    attachmentFile = new InputStreamAttachmentFile(aIS, filename);
                }
                else if (attachmentValue instanceof AttachmentFile file)
                {
                    attachmentFile = file;
                    filename = attachmentFile.getFilename();
                    if (!StringUtils.isEmpty(oldAttachmentValue))
                        oldAttachments.add(oldAttachmentValue);
                }
                else if (attachmentValue instanceof File file)
                {
                    attachmentFile = new FileAttachmentFile(file);
                    filename = attachmentFile.getFilename();
                    if (!StringUtils.isEmpty(oldAttachmentValue))
                        oldAttachments.add(oldAttachmentValue);
                }
                else
                {
                    errors.addRowError(propertyValidationException(p.domainProperty, attachmentValue));
                    return false;
                }

                if (entityIdIndex == 0)
                {
                    errors.addRowError(rowValidationException("Unable to create attachment file."));
                    return false;
                }

                if (null == filename)
                    continue;

                if (null == attachmentFiles)
                    attachmentFiles = new ArrayList<>();
                attachmentFiles.add(attachmentFile);
            }

            if ((null == attachmentFiles || attachmentFiles.isEmpty()) && oldAttachments.isEmpty())
                return ret;

            String entityId = String.valueOf(get(entityIdIndex));
            var attachmentParent = getAttachmentParent(entityId, container);

            if (!oldAttachments.isEmpty())
                AttachmentService.get().deleteAttachments(attachmentParent, oldAttachments, user);

            if (null != attachmentFiles && !attachmentFiles.isEmpty())
                AttachmentService.get().addAttachments(attachmentParent, attachmentFiles, user);

            return ret;
        }
        catch (AttachmentService.DuplicateFilenameException | AttachmentService.FileTooLargeException e)
        {
            errors.addRowError(new ValidationException(e.getMessage()));
            return false;
        }
        catch (Exception x)
        {
            throw UnexpectedException.wrap(x);
        }
        finally
        {
            if (attachmentFiles != null)
            {
                for (AttachmentFile attachmentFile : attachmentFiles)
                {
                    try { attachmentFile.closeInputStream(); }
                    catch (IOException | IllegalStateException ignored) {}
                }
            }
        }
    }

    private ValidationException propertyValidationException(DomainProperty property, Object value)
    {
        return rowValidationException(String.format("Cannot upload '%s' to %s type field '%s'.", value, property.getType().getLabel(), property.getName()));
    }

    private ValidationException rowValidationException(String message)
    {
        return new ValidationException("Row " + get(0) + ": " + message);
    }

    public static DataIteratorBuilder getAttachmentDataIteratorBuilder(TableInfo ti, @NotNull final DataIteratorBuilder builder,
                                                                       final User user,
                                                                       @Nullable final VirtualFile attachmentDir,
                                                                       Container container,
                                                                       AttachmentParentFactory parentFactory)
    {
        return getAttachmentDataIteratorBuilder(ti, builder, user, attachmentDir, container, parentFactory, FieldKey.fromParts("entityId"));
    }

    public static DataIteratorBuilder getAttachmentDataIteratorBuilder(TableInfo ti, @NotNull final DataIteratorBuilder builder,
                                                                       final User user,
                                                                       @Nullable final VirtualFile attachmentDir,
                                                                       Container container,
                                                                       AttachmentParentFactory parentFactory,
                                                                       FieldKey entityId)
    {
        return context -> {
            //Adding as check against Issue #26599
            if (builder == null)
                throw new IllegalStateException("Originating data iterator is null");

            DataIterator it = builder.getDataIterator(context);
            if (it == null)
                return null; // can happen if context has errors

            Domain domain = ti.getDomain();
            if (domain == null)
                return it;

            // find attachment columns
            int entityIdIndex = 0;
            final ArrayList<_AttachmentUploadHelper> attachmentColumns = new ArrayList<>();
            final Map<String, String> attachmentColumnsAliases = new HashMap<>();

            for (int c = 1; c <= it.getColumnCount(); c++)
            {
                try
                {
                    ColumnInfo col = it.getColumnInfo(c);

                    if (col.getFieldKey().equals(entityId))
                        entityIdIndex = c;

                    // TODO: Issue 22505: Don't seem to have attachment information in the ColumnInfo, so we need to lookup the DomainProperty
                    // UNDONE: PropertyURI is not propagated, need to use name
                    DomainProperty domainProperty = domain.getPropertyByName(col.getName());
                    if (null == domainProperty || domainProperty.getPropertyDescriptor().getPropertyType() != PropertyType.ATTACHMENT)
                        continue;

                    attachmentColumns.add(new _AttachmentUploadHelper(c,domainProperty));
                    attachmentColumnsAliases.put(domainProperty.getName(), col.getAlias().getId());
                }
                catch (IndexOutOfBoundsException ignored) // Until issue is resolved between StatementDataIterator.getColumnCount() and SimpleTranslator.getColumnCount()
                {
                }
            }

            if (!attachmentColumns.isEmpty())
            {
                return new AttachmentDataIterator(it, context.getErrors(), user, attachmentDir, entityIdIndex, attachmentColumns, attachmentColumnsAliases, context.getInsertOption(), container, parentFactory);
            }

            return it;
        };
    }

    protected AttachmentParent getAttachmentParent(String entityId, Container c) { return parentFactory != null ? parentFactory.generateAttachmentParent(entityId, c): null; }


    private static class _AttachmentUploadHelper
    {
        _AttachmentUploadHelper(int i, DomainProperty dp)
        {
            index=i;
            domainProperty = dp;
        }
        final int index;
        final DomainProperty domainProperty;
        final FileNameUniquifier uniquifier = new FileNameUniquifier();
    }
}

