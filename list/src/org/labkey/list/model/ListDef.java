/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import org.labkey.api.data.CacheKey;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.exp.list.ListDefinition.BodySetting;
import org.labkey.api.exp.list.ListDefinition.DiscussionSetting;
import org.labkey.api.exp.list.ListDefinition.IndexSetting;
import org.labkey.api.exp.list.ListDefinition.TitleSetting;
import org.labkey.api.util.UnexpectedException;

import java.util.Date;

public class ListDef extends Entity implements Cloneable
{
    public enum Column
    {
        rowId,
        name,
        domainId,
    }

    static public class Key extends CacheKey<ListDef, Column>
    {
        public Key(Container container)
        {
            super(ListManager.get().getTinfoList(), ListDef.class, container);
        }
    }

    @Deprecated
    private int _rowId;  // Unique within the server... will be removed after hard table conversion
    private int _listId; // Unique within this container
    private String _name;
    private int _domainId;
    private String _keyName;
    private String _keyType;
    private String _titleColumn;
    private String _description;
    private Date _lastIndexed;

    private DiscussionSetting _discussionSetting = DiscussionSetting.None;
    private boolean _allowDelete = true;
    private boolean _allowUpload = true;
    private boolean _allowExport = true;

    private boolean _entireListIndex = false;
    private IndexSetting _entireListIndexSetting = IndexSetting.MetaData;
    private TitleSetting _entireListTitleSetting = TitleSetting.Standard;
    private String _entireListTitleTemplate = null;
    private BodySetting _entireListBodySetting = BodySetting.TextOnly;
    private String _entireListBodyTemplate = null;

    private boolean _eachItemIndex = false;
    private TitleSetting _eachItemTitleSetting = TitleSetting.Standard;
    private String _eachItemTitleTemplate = null;
    private BodySetting _eachItemBodySetting = BodySetting.TextOnly;
    private String _eachItemBodyTemplate = null;

    @Deprecated
    public int getRowId()
    {
        return _rowId;
    }

    @Deprecated
    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getListId()
    {
        return _listId;
    }

    public void setListId(int listId)
    {
        _listId = listId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public int getDomainId()
    {
        return _domainId;
    }

    public void setDomainId(int domainId)
    {
        _domainId = domainId;
    }

    public String getKeyName()
    {
        return _keyName;
    }

    public void setKeyName(String keyName)
    {
        _keyName = keyName;
    }

    public String getKeyType()
    {
        return _keyType;
    }

    public void setKeyType(String keyType)
    {
        _keyType = keyType;
    }

    public String getTitleColumn()
    {
        return _titleColumn;
    }

    public void setTitleColumn(String titleColumn)
    {
        _titleColumn = titleColumn;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public Date getLastIndexed()
    {
        return _lastIndexed;
    }

    public void setLastIndexed(Date lastIndexed)
    {
        _lastIndexed = lastIndexed;
    }

    public int getDiscussionSetting()
    {
        return _discussionSetting.getValue();
    }

    public void setDiscussionSetting(int value)
    {
        _discussionSetting = DiscussionSetting.getForValue(value);
    }

    public DiscussionSetting getDiscussionSettingEnum()
    {
        return _discussionSetting;
    }

    public void setDiscussionSettingEnum(DiscussionSetting discussionSetting)
    {
        _discussionSetting = discussionSetting;
    }

    public boolean getAllowDelete()
    {
        return _allowDelete;
    }

    public void setAllowDelete(boolean allowDelete)
    {
        _allowDelete = allowDelete;
    }

    public boolean getAllowUpload()
    {
        return _allowUpload;
    }

    public void setAllowUpload(boolean allowUpload)
    {
        _allowUpload = allowUpload;
    }

    public boolean getAllowExport()
    {
        return _allowExport;
    }

    public void setAllowExport(boolean allowExport)
    {
        _allowExport = allowExport;
    }

    public boolean getEntireListIndex()
    {
        return _entireListIndex;
    }

    public void setEntireListIndex(boolean entireListIndex)
    {
        _entireListIndex = entireListIndex;
    }

    public int getEntireListIndexSetting()
    {
        return _entireListIndexSetting.getValue();
    }

    public void setEntireListIndexSetting(int settingInt)
    {
        _entireListIndexSetting = IndexSetting.getForValue(settingInt);
    }

    public IndexSetting getEntireListIndexSettingEnum()
    {
        return _entireListIndexSetting;
    }

    public void setEntireListIndexSettingEnum(IndexSetting setting)
    {
        _entireListIndexSetting = setting;
    }

    public int getEntireListTitleSetting()
    {
        return _entireListTitleSetting.getValue();
    }

    public void setEntireListTitleSetting(int settingInt)
    {
        _entireListTitleSetting = TitleSetting.getForValue(settingInt);
    }

    public TitleSetting getEntireListTitleSettingEnum()
    {
        return _entireListTitleSetting;
    }

    public void setEntireListTitleSettingEnum(TitleSetting setting)
    {
        _entireListTitleSetting = setting;
    }

    public String getEntireListTitleTemplate()
    {
        return _entireListTitleTemplate;
    }

    public void setEntireListTitleTemplate(String template)
    {
        _entireListTitleTemplate = template;
    }

    public int getEntireListBodySetting()
    {
        return _entireListBodySetting.getValue();
    }

    public void setEntireListBodySetting(int settingInt)
    {
        _entireListBodySetting = BodySetting.getForValue(settingInt);
    }

    public BodySetting getEntireListBodySettingEnum()
    {
        return _entireListBodySetting;
    }

    public void setEntireListBodySettingEnum(BodySetting setting)
    {
        _entireListBodySetting = setting;
    }

    public String getEntireListBodyTemplate()
    {
        return _entireListBodyTemplate;
    }

    public void setEntireListBodyTemplate(String template)
    {
        _entireListBodyTemplate = template;
    }

    public boolean getEachItemIndex()
    {
        return _eachItemIndex;
    }

    public void setEachItemIndex(boolean index)
    {
        _eachItemIndex = index;
    }

    public int getEachItemTitleSetting()
    {
        return _eachItemTitleSetting.getValue();
    }

    public void setEachItemTitleSetting(int settingInt)
    {
        _eachItemTitleSetting = TitleSetting.getForValue(settingInt);
    }

    public TitleSetting getEachItemTitleSettingEnum()
    {
        return _eachItemTitleSetting;
    }

    public void setEachItemTitleSettingEnum(TitleSetting setting)
    {
        _eachItemTitleSetting = setting;
    }

    public String getEachItemTitleTemplate()
    {
        return _eachItemTitleTemplate;
    }

    public void setEachItemTitleTemplate(String template)
    {
        _eachItemTitleTemplate = template;
    }

    public int getEachItemBodySetting()
    {
        return _eachItemBodySetting.getValue();
    }

    public void setEachItemBodySetting(int settingInt)
    {
        _eachItemBodySetting = BodySetting.getForValue(settingInt);
    }

    public BodySetting getEachItemBodySettingEnum()
    {
        return _eachItemBodySetting;
    }

    public void setEachItemBodySettingEnum(BodySetting setting)
    {
        _eachItemBodySetting = setting;
    }

    public String getEachItemBodyTemplate()
    {
        return _eachItemBodyTemplate;
    }

    public void setEachItemBodyTemplate(String template)
    {
        _eachItemBodyTemplate = template;
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
}
