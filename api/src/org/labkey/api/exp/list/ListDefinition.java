/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.exp.list;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.VirtualFile;
import org.springframework.web.servlet.mvc.Controller;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface ListDefinition extends Comparable<ListDefinition>
{
    enum KeyType
    {
        Integer("Integer")
            {
                protected Object convertKeyInternal(Object key)
                {
                    if (key instanceof Integer)
                        return key;
                    else
                        return java.lang.Integer.valueOf(key.toString());
                }
                public PropertyType getPropertyType()
                {
                    return PropertyType.INTEGER;
                }
            },
        AutoIncrementInteger("Auto-Increment Integer")
            {
                protected Object convertKeyInternal(Object key)
                {
                    if (key instanceof Integer)
                        return key;
                    else
                        return java.lang.Integer.valueOf(key.toString());
                }
                public PropertyType getPropertyType()
                {
                    return PropertyType.INTEGER;
                }
            },
        Varchar("Text (String)")
            {
                protected Object convertKeyInternal(Object key)
                {
                    return key.toString();
                }
                public PropertyType getPropertyType()
                {
                    return PropertyType.STRING;
                }
            };

        final String label;

        KeyType(String label)
        {
            this.label = label;
        }

        public String getLabel()
        {
            return label;
        }

        public Object convertKey(Object key)
        {
            try
            {
                return convertKeyInternal(key);
            }
            catch (Exception e)
            {
                throw new KeyConversionException(key, this, e);
            }
        }

        protected abstract Object convertKeyInternal(Object key) throws KeyConversionException;
        public abstract PropertyType getPropertyType();
    }

    class KeyConversionException extends NotFoundException
    {
        protected KeyConversionException(Object key, KeyType type, Throwable cause)
        {
            super(null == key ? "Primary key is missing" : "Could not convert key value \"" + key + "\" to type " + type.getLabel(), cause);
        }
    }

    enum DiscussionSetting
    {
        None(0, "None"),
        OnePerItem(1, "Allow one discussion per item"),
        ManyPerItem(2, "Allow multiple discussions per item");

        private final int _value;
        private final String _text;

        DiscussionSetting(int value, String text)
        {
            _value = value;
            _text = text;
        }

        public static DiscussionSetting getForValue(int value)
        {
            for (DiscussionSetting s : DiscussionSetting.values())
                if (s.getValue() == value)
                    return s;

            return null;
        }

        public boolean isLinked()
        {
            return _value > 0;
        }

        public String getText()
        {
            return _text;
        }

        public int getValue()
        {
            return _value;
        }
    }

    enum IndexSetting
    {
        MetaData(0, true, false),
        ItemData(1, false, true),
        Both(2, true, true);

        private final int _value;
        private final boolean _metaData;
        private final boolean _itemData;

        IndexSetting(int value, boolean metaData, boolean itemData)
        {
            _value = value;
            _metaData = metaData;
            _itemData = itemData;
        }

        public int getValue()
        {
            return _value;
        }

        public boolean indexMetaData()
        {
            return _metaData;
        }

        public boolean indexItemData()
        {
            return _itemData;
        }

        public static IndexSetting getForValue(int value)
        {
            for (IndexSetting s : IndexSetting.values())
                if (s.getValue() == value)
                    return s;

            return null;
        }
    }

    enum BodySetting
    {
        TextOnly(0)
                {
                    @Override
                    public boolean accept(ColumnInfo column)
                    {
                        return AllFields.accept(column) && column.isStringType();
                    }
                },
        AllFields(1)
                {
                    @Override
                    public boolean accept(ColumnInfo column)
                    {
                        return column.isUserEditable();
                    }
                },
        Custom(2)
                {
                    @Override
                    public boolean accept(ColumnInfo column)
                    {
                        return TextOnly.accept(column);  // This gets called if "custom" is selected but template is blank; use default template in that case
                    }
                };

        private final int _value;

        BodySetting(int value)
        {
            _value = value;
        }

        public int getValue()
        {
            return _value;
        }

        public static BodySetting getForValue(int value)
        {
            for (BodySetting s : BodySetting.values())
                if (s.getValue() == value)
                    return s;

            return null;
        }

        abstract public boolean accept(ColumnInfo column);
    }

    enum TitleSetting
    {
        Standard(0),
        Custom(1);

        private final int _value;

        TitleSetting(int value)
        {
            _value = value;
        }

        public int getValue()
        {
            return _value;
        }

        public static TitleSetting getForValue(int value)
        {
            for (TitleSetting s : TitleSetting.values())
                if (s.getValue() == value)
                    return s;

            return null;
        }
    }

    int getListId();
    void setPreferredListIds(Collection<Integer> preferredListIds); // Attempts to use this list IDs when inserting
    Container getContainer();
    @Nullable Domain getDomain();

    String getName();
    String getKeyName();
    void setKeyName(String name);
    void setDescription(String description);
    String getDescription();
    void setTitleColumn(String titleColumn);
    String getTitleColumn();
    Date getModified();
    void setModified(Date modified);
    Date getLastIndexed();
    void setLastIndexed(Date modified);

    KeyType getKeyType();
    void setKeyType(KeyType type);

    void save(User user) throws Exception;
    void save(User user, boolean ensureKey) throws Exception;
    void delete(User user) throws DomainNotFoundException;

    ListItem createListItem();
    ListItem getListItem(Object key, User user);
    ListItem getListItem(Object key, User user, Container c);
    ListItem getListItemForEntityId(String entityId, User user);

    int insertListItems(User user, Container container, List<ListItem> listItems) throws IOException;
    int insertListItems(User user, Container container, DataLoader loader, @NotNull BatchValidationException errors, @Nullable VirtualFile attachmentDir, @Nullable ListImportProgress progress, boolean supportAutoIncrementKey, boolean importByAlternateKey) throws IOException;
    int insertListItems(User user, Container container, DataLoader loader, @NotNull BatchValidationException errors, @Nullable VirtualFile attachmentDir, @Nullable ListImportProgress progress, boolean supportAutoIncrementKey, boolean importByAlternateKey, boolean useMerge) throws IOException;

    @Nullable TableInfo getTable(User user);
    @Nullable TableInfo getTable(User user, Container c);

    ActionURL urlShowDefinition();
    ActionURL urlUpdate(User user, Container container, @Nullable Object pk, @Nullable URLHelper returnUrl);
    ActionURL urlDetails(@Nullable Object pk);
    ActionURL urlDetails(@Nullable Object pk, Container c);
    ActionURL urlShowData();
    ActionURL urlShowData(Container c);
    ActionURL urlShowHistory(Container c);

    ActionURL urlFor(Class<? extends Controller> actionClass);
    ActionURL urlFor(Class<? extends Controller> actionClass, Container c);

    Collection<String> getDependents(User user);

    DiscussionSetting getDiscussionSetting();
    void setDiscussionSetting(DiscussionSetting discussionSetting);

    boolean getAllowDelete();
    void setAllowDelete(boolean allowDelete);

    boolean getAllowUpload();
    void setAllowUpload(boolean allowUpload);

    boolean getAllowExport();
    void setAllowExport(boolean allowExport);

    boolean getEntireListIndex();
    void setEntireListIndex(boolean index);

    IndexSetting getEntireListIndexSetting();
    void setEntireListIndexSetting(IndexSetting setting);

    TitleSetting getEntireListTitleSetting();
    void setEntireListTitleSetting(TitleSetting setting);

    String getEntireListTitleTemplate();
    void setEntireListTitleTemplate(String template);

    BodySetting getEntireListBodySetting();
    void setEntireListBodySetting(BodySetting setting);

    String getEntireListBodyTemplate();
    void setEntireListBodyTemplate(String template);

    boolean getEachItemIndex();
    void setEachItemIndex(boolean index);

    TitleSetting getEachItemTitleSetting();
    void setEachItemTitleSetting(TitleSetting setting);

    String getEachItemTitleTemplate();
    void setEachItemTitleTemplate(String template);

    BodySetting getEachItemBodySetting();
    void setEachItemBodySetting(BodySetting setting);

    String getEachItemBodyTemplate();
    void setEachItemBodyTemplate(String template);

    boolean getFileAttachmentIndex();
    void setFileAttachmentIndex(boolean index);
}
