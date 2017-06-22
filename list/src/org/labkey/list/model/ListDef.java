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

package org.labkey.list.model;

import org.labkey.api.data.Builder;
import org.labkey.api.data.BuilderObjectFactory;
import org.labkey.api.data.Entity;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.exp.list.ListDefinition.BodySetting;
import org.labkey.api.exp.list.ListDefinition.DiscussionSetting;
import org.labkey.api.exp.list.ListDefinition.IndexSetting;
import org.labkey.api.exp.list.ListDefinition.TitleSetting;
import org.labkey.api.util.UnexpectedException;

import java.util.Date;

public class ListDef extends Entity implements Cloneable
{
    protected int _listId; // Unique within this container
    protected String _name;
    protected int _domainId;
    protected String _keyName;
    protected String _keyType;
    protected String _titleColumn;
    protected String _description;
    protected Date _lastIndexed;

    protected DiscussionSetting _discussionSetting = DiscussionSetting.None;
    protected boolean _allowDelete = true;
    protected boolean _allowUpload = true;
    protected boolean _allowExport = true;

    protected boolean _entireListIndex = false;
    protected IndexSetting _entireListIndexSetting = IndexSetting.MetaData;
    protected TitleSetting _entireListTitleSetting = TitleSetting.Standard;
    protected String _entireListTitleTemplate = null;
    protected BodySetting _entireListBodySetting = BodySetting.TextOnly;
    protected String _entireListBodyTemplate = null;

    protected boolean _eachItemIndex = false;
    protected TitleSetting _eachItemTitleSetting = TitleSetting.Standard;
    protected String _eachItemTitleTemplate = null;
    protected BodySetting _eachItemBodySetting = BodySetting.TextOnly;
    protected String _eachItemBodyTemplate = null;

    protected boolean _fileAttachmentIndex = false;

    public int getListId()
    {
        return _listId;
    }

    public String getName()
    {
        return _name;
    }

    public int getDomainId()
    {
        return _domainId;
    }

    public String getKeyName()
    {
        return _keyName;
    }

    public String getKeyType()
    {
        return _keyType;
    }

    public String getTitleColumn()
    {
        return _titleColumn;
    }

    public String getDescription()
    {
        return _description;
    }

    public Date getLastIndexed()
    {
        return _lastIndexed;
    }

    public int getDiscussionSetting()
    {
        return _discussionSetting.getValue();
    }

    public DiscussionSetting getDiscussionSettingEnum()
    {
        return _discussionSetting;
    }

    public boolean getAllowDelete()
    {
        return _allowDelete;
    }

    public boolean getAllowUpload()
    {
        return _allowUpload;
    }

    public boolean getAllowExport()
    {
        return _allowExport;
    }

    public boolean getEntireListIndex()
    {
        return _entireListIndex;
    }

    public int getEntireListIndexSetting()
    {
        return _entireListIndexSetting.getValue();
    }

    public IndexSetting getEntireListIndexSettingEnum()
    {
        return _entireListIndexSetting;
    }

    public int getEntireListTitleSetting()
    {
        return _entireListTitleSetting.getValue();
    }

    public TitleSetting getEntireListTitleSettingEnum()
    {
        return _entireListTitleSetting;
    }

    public String getEntireListTitleTemplate()
    {
        return _entireListTitleTemplate;
    }

    public int getEntireListBodySetting()
    {
        return _entireListBodySetting.getValue();
    }

    public BodySetting getEntireListBodySettingEnum()
    {
        return _entireListBodySetting;
    }

    public String getEntireListBodyTemplate()
    {
        return _entireListBodyTemplate;
    }

    public boolean getEachItemIndex()
    {
        return _eachItemIndex;
    }

    public int getEachItemTitleSetting()
    {
        return _eachItemTitleSetting.getValue();
    }

    public TitleSetting getEachItemTitleSettingEnum()
    {
        return _eachItemTitleSetting;
    }

    public String getEachItemTitleTemplate()
    {
        return _eachItemTitleTemplate;
    }

    public int getEachItemBodySetting()
    {
        return _eachItemBodySetting.getValue();
    }

    public BodySetting getEachItemBodySettingEnum()
    {
        return _eachItemBodySetting;
    }

    public String getEachItemBodyTemplate()
    {
        return _eachItemBodyTemplate;
    }

    public boolean getFileAttachmentIndex()
    {
        return _fileAttachmentIndex;
    }

    protected void copyTo(ListDef to)
    {
        super.copyTo(to);
        to._listId = _listId;
        to._name = _name;
        to._domainId = _domainId;
        to._keyName = _keyName;
        to._keyType = _keyType;
        to._titleColumn = _titleColumn;
        to._description = _description;
        to._lastIndexed = _lastIndexed;
        to._discussionSetting = _discussionSetting;
        to._allowDelete = _allowDelete;
        to._allowUpload = _allowUpload;
        to._allowExport = _allowExport;
        to._entireListIndex = _entireListIndex;
        to._entireListIndexSetting = _entireListIndexSetting;
        to._entireListTitleSetting = _entireListTitleSetting;
        to._entireListTitleTemplate = _entireListTitleTemplate;
        to._entireListBodySetting = _entireListBodySetting;
        to._entireListBodyTemplate = _entireListBodyTemplate;
        to._eachItemIndex = _eachItemIndex;
        to._eachItemTitleSetting = _eachItemTitleSetting;
        to._eachItemTitleTemplate = _eachItemTitleTemplate;
        to._eachItemBodySetting = _eachItemBodySetting;
        to._eachItemBodyTemplate = _eachItemBodyTemplate;
        to._fileAttachmentIndex = _fileAttachmentIndex;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ListDef listDef = (ListDef) o;

        if (_listId != listDef._listId) return false;
        if (_domainId != listDef._domainId) return false;
        if (_allowDelete != listDef._allowDelete) return false;
        if (_allowUpload != listDef._allowUpload) return false;
        if (_allowExport != listDef._allowExport) return false;
        if (_entireListIndex != listDef._entireListIndex) return false;
        if (_eachItemIndex != listDef._eachItemIndex) return false;
        if (_fileAttachmentIndex != listDef._fileAttachmentIndex) return false;
        if (_name != null ? !_name.equals(listDef._name) : listDef._name != null) return false;
        if (_keyName != null ? !_keyName.equals(listDef._keyName) : listDef._keyName != null) return false;
        if (_keyType != null ? !_keyType.equals(listDef._keyType) : listDef._keyType != null) return false;
        if (_titleColumn != null ? !_titleColumn.equals(listDef._titleColumn) : listDef._titleColumn != null)
            return false;
        if (_description != null ? !_description.equals(listDef._description) : listDef._description != null)
            return false;
        if (_lastIndexed != null ? !_lastIndexed.equals(listDef._lastIndexed) : listDef._lastIndexed != null)
            return false;
        if (_discussionSetting != listDef._discussionSetting) return false;
        if (_entireListIndexSetting != listDef._entireListIndexSetting) return false;
        if (_entireListTitleSetting != listDef._entireListTitleSetting) return false;
        if (_entireListTitleTemplate != null ? !_entireListTitleTemplate.equals(listDef._entireListTitleTemplate) : listDef._entireListTitleTemplate != null)
            return false;
        if (_entireListBodySetting != listDef._entireListBodySetting) return false;
        if (_entireListBodyTemplate != null ? !_entireListBodyTemplate.equals(listDef._entireListBodyTemplate) : listDef._entireListBodyTemplate != null)
            return false;
        if (_eachItemTitleSetting != listDef._eachItemTitleSetting) return false;
        if (_eachItemTitleTemplate != null ? !_eachItemTitleTemplate.equals(listDef._eachItemTitleTemplate) : listDef._eachItemTitleTemplate != null)
            return false;
        if (_eachItemBodySetting != listDef._eachItemBodySetting) return false;
        return _eachItemBodyTemplate != null ? _eachItemBodyTemplate.equals(listDef._eachItemBodyTemplate) : listDef._eachItemBodyTemplate == null;
    }


    @Override
    public int hashCode()
    {
        int result = _listId;
        result = 31 * result + (_name != null ? _name.hashCode() : 0);
        result = 31 * result + _domainId;
        result = 31 * result + (_keyName != null ? _keyName.hashCode() : 0);
        result = 31 * result + (_keyType != null ? _keyType.hashCode() : 0);
        result = 31 * result + (_titleColumn != null ? _titleColumn.hashCode() : 0);
        result = 31 * result + (_description != null ? _description.hashCode() : 0);
        result = 31 * result + (_lastIndexed != null ? _lastIndexed.hashCode() : 0);
        result = 31 * result + (_discussionSetting != null ? _discussionSetting.hashCode() : 0);
        result = 31 * result + (_allowDelete ? 1 : 0);
        result = 31 * result + (_allowUpload ? 1 : 0);
        result = 31 * result + (_allowExport ? 1 : 0);
        result = 31 * result + (_entireListIndex ? 1 : 0);
        result = 31 * result + (_entireListIndexSetting != null ? _entireListIndexSetting.hashCode() : 0);
        result = 31 * result + (_entireListTitleSetting != null ? _entireListTitleSetting.hashCode() : 0);
        result = 31 * result + (_entireListTitleTemplate != null ? _entireListTitleTemplate.hashCode() : 0);
        result = 31 * result + (_entireListBodySetting != null ? _entireListBodySetting.hashCode() : 0);
        result = 31 * result + (_entireListBodyTemplate != null ? _entireListBodyTemplate.hashCode() : 0);
        result = 31 * result + (_eachItemIndex ? 1 : 0);
        result = 31 * result + (_eachItemTitleSetting != null ? _eachItemTitleSetting.hashCode() : 0);
        result = 31 * result + (_eachItemTitleTemplate != null ? _eachItemTitleTemplate.hashCode() : 0);
        result = 31 * result + (_eachItemBodySetting != null ? _eachItemBodySetting.hashCode() : 0);
        result = 31 * result + (_eachItemBodyTemplate != null ? _eachItemBodyTemplate.hashCode() : 0);
        result = 31 * result + (_fileAttachmentIndex ? 1 : 0);
        return result;
    }

    protected ListDef clone()
    {
        try
        {
            return (ListDef) super.clone();
        }
        catch (CloneNotSupportedException cnse)
        {
            throw UnexpectedException.wrap(cnse);
        }
    }

    @Override
    public String toString()
    {
        return getName() + ", rowid: " + getListId();
    }


    public static class ListDefBuilder extends ListDef implements Builder<ListDef>
    {
        public ListDefBuilder()
        {
        }

        public ListDefBuilder(ListDef def)
        {
            def.copyTo(this);
            assert def.equals(this.build());
        }

        @Override
        public ListDef build()
        {
            return super.clone();
        }

        public void setListId(int listId)
        {
            _listId = listId;
        }
        public void setName(String name)
        {
            _name = name;
        }
        public void setDomainId(int domainId)
        {
            _domainId = domainId;
        }
        public void setKeyName(String keyName)
        {
            _keyName = keyName;
        }
        public void setKeyType(String keyType)
        {
            _keyType = keyType;
        }
        public void setTitleColumn(String titleColumn)
        {
            _titleColumn = titleColumn;
        }
        public void setDescription(String description)
        {
            _description = description;
        }
        public void setLastIndexed(Date lastIndexed)
        {
            _lastIndexed = lastIndexed;
        }
        public void setDiscussionSetting(int value)
        {
            _discussionSetting = DiscussionSetting.getForValue(value);
        }
        public void setDiscussionSettingEnum(DiscussionSetting discussionSetting)
        {
            _discussionSetting = discussionSetting;
        }
        public void setAllowDelete(boolean allowDelete)
        {
            _allowDelete = allowDelete;
        }
        public void setAllowUpload(boolean allowUpload)
        {
            _allowUpload = allowUpload;
        }
        public void setAllowExport(boolean allowExport)
        {
            _allowExport = allowExport;
        }
        public void setEntireListIndex(boolean entireListIndex)
        {
            _entireListIndex = entireListIndex;
        }
        public void setEntireListIndexSetting(int settingInt)
        {
            _entireListIndexSetting = IndexSetting.getForValue(settingInt);
        }
        public void setEntireListIndexSettingEnum(IndexSetting setting)
        {
            _entireListIndexSetting = setting;
        }
        public void setEntireListTitleSetting(int settingInt)
        {
            _entireListTitleSetting = TitleSetting.getForValue(settingInt);
        }
        public void setEntireListTitleSettingEnum(TitleSetting setting)
        {
            _entireListTitleSetting = setting;
        }
        public void setEntireListTitleTemplate(String template)
        {
            _entireListTitleTemplate = template;
        }
        public void setEntireListBodySetting(int settingInt)
        {
            _entireListBodySetting = BodySetting.getForValue(settingInt);
        }
        public void setEntireListBodySettingEnum(BodySetting setting)
        {
            _entireListBodySetting = setting;
        }
        public void setEntireListBodyTemplate(String template)
        {
            _entireListBodyTemplate = template;
        }
        public void setEachItemIndex(boolean index)
        {
            _eachItemIndex = index;
        }
        public void setEachItemTitleSetting(int settingInt)
        {
            _eachItemTitleSetting = TitleSetting.getForValue(settingInt);
        }
        public void setEachItemTitleSettingEnum(TitleSetting setting)
        {
            _eachItemTitleSetting = setting;
        }
        public void setEachItemTitleTemplate(String template)
        {
            _eachItemTitleTemplate = template;
        }
        public void setEachItemBodySetting(int settingInt)
        {
            _eachItemBodySetting = BodySetting.getForValue(settingInt);
        }
        public void setEachItemBodySettingEnum(BodySetting setting)
        {
            _eachItemBodySetting = setting;
        }
        public void setEachItemBodyTemplate(String template)
        {
            _eachItemBodyTemplate = template;
        }
        public void setFileAttachmentIndex(boolean index)
        {
            _fileAttachmentIndex = index;
        }
    }

    static
    {
        ObjectFactory.Registry.register(ListDef.class, new BuilderObjectFactory<>(ListDef.class, ListDef.ListDefBuilder.class));
    }
}
