/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.common.tools.DataLoader;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.sql.SQLException;

public interface ListDefinition
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
            },
        Varchar("Text (String)")
            {
                protected Object convertKeyInternal(Object key)
                {
                    return key.toString();
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

        public boolean isValidKey(Object key)
        {
            try
            {
                convertKey(key);
                return true;
            }
            catch (KeyConversionException e)
            {
                return false;
            }
        }

        protected abstract Object convertKeyInternal(Object key) throws KeyConversionException;
    }

    public static class KeyConversionException extends NotFoundException
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

    int getListId();
    Container getContainer();
    Domain getDomain();
    String getName();
    String getKeyName();
    void setKeyName(String name);
    void setDescription(String description);
    String getDescription();
    void setTitleColumn(String titleColumn);
    String getTitleColumn();

    KeyType getKeyType();
    void setKeyType(KeyType type);

    void save(User user) throws Exception;
    void deleteListItems(User user, Collection keys) throws SQLException;
    void delete(User user) throws Exception;

    ListItem createListItem();
    ListItem getListItem(Object key);
    ListItem getListItemForEntityId(String entityId);

    int getRowCount();
    List<String> insertListItems(User user, DataLoader loader) throws IOException;

    TableInfo getTable(User user, String alias);

    ActionURL urlShowDefinition();
    ActionURL urlEditDefinition();

    ActionURL urlUpdate(Object pk, ActionURL returnUrl);
    ActionURL urlDetails(Object pk);
    ActionURL urlShowData();
    ActionURL urlShowHistory();

    ActionURL urlFor(Enum action);

    DiscussionSetting getDiscussionSetting();
    void setDiscussionSetting(DiscussionSetting discussionSetting);

    boolean getAllowDelete();
    void setAllowDelete(boolean allowDelete);

    boolean getAllowUpload();
    void setAllowUpload(boolean allowUpload);

    boolean getAllowExport();
    void setAllowExport(boolean allowExport);
}
