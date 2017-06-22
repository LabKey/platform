/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

package org.labkey.list.client;

import com.google.gwt.user.client.rpc.IsSerializable;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.IntegerProperty;
import org.labkey.api.gwt.client.util.StringProperty;

/**
 * User: matthewb
 * Date: Apr 30, 2007
 * Time: 9:51:51 AM
 */
public class GWTList implements IsSerializable
{
    public StringProperty name = new StringProperty();
    public StringProperty description = new StringProperty();
//    private StringProperty _getTypeURI = new StringProperty();
    public StringProperty keyPropertyName = new StringProperty();
    public StringProperty keyPropertyType = new StringProperty();
    public StringProperty titleField = new StringProperty();
    public IntegerProperty discussionSetting = new IntegerProperty(0); // DiscussionSetting.None
    public BooleanProperty allowDelete = new BooleanProperty(true);
    public BooleanProperty allowUpload = new BooleanProperty(true);
    public BooleanProperty allowExport = new BooleanProperty(true);

    public BooleanProperty entireListIndex = new BooleanProperty(true);     // Enable entire list as a single document indexing by default
    public IntegerProperty entireListIndexSetting = new IntegerProperty(2); // Index data and metadata
    public IntegerProperty entireListTitleSetting = new IntegerProperty(0); // TitleSetting.Standard
    public StringProperty entireListTitleTemplate = new StringProperty();
    public IntegerProperty entireListBodySetting = new IntegerProperty(0); // BodySetting.TextOnly
    public StringProperty entireListBodyTemplate = new StringProperty();

    public BooleanProperty eachItemIndex = new BooleanProperty(false);
    public IntegerProperty eachItemTitleSetting = new IntegerProperty(0); // TitleSetting.Standard
    public StringProperty eachItemTitleTemplate = new StringProperty();
    public IntegerProperty eachItemBodySetting = new IntegerProperty(0); // BodySetting.TextOnly
    public StringProperty eachItemBodyTemplate = new StringProperty();

    public BooleanProperty fileAttachmentIndex = new BooleanProperty(false);

    // client should only read these
    private String _typeURI;
    private String _defaultTitleField;
    private IntegerProperty _listId = new IntegerProperty(0);


    public GWTList()
    {
    }

    public GWTList(int id)
    {
        _listId.set(id);
    }

    public int getListId()
    {
        return _listId.getInt();
    }

    public void _listId(int listId)
    {
        _listId.setInt(listId);
    }

    public String getKeyPropertyName()
    {
        return keyPropertyName.getString();
    }

    public void setKeyPropertyName(String keyPropertyName)
    {
        this.keyPropertyName.set(keyPropertyName);
    }

    public String getName()
    {
        return name.getString();
    }

    public void setName(String name)
    {
        this.name.set(name);
    }

//    public String getTypeURI()
//    {
//        return _typeURI.getString();
//    }
//
//    public void setTypeURI(String typeURI)
//    {
//        this._typeURI.set(typeURI);
//    }

    public String getDescription()
    {
        return description.getString();
    }

    public void setDescription(String description)
    {
        this.description.set(description);
    }

    public String getKeyPropertyType()
    {
        return keyPropertyType.getString();
    }

    public void setKeyPropertyType(String keyPropertyType)
    {
        if ("Integer".equals(keyPropertyType) || "AutoIncrementInteger".equals(keyPropertyType) || "Varchar".equals(keyPropertyType))
        {
            this.keyPropertyType.set(keyPropertyType);
            return;
        }
        throw new IllegalArgumentException(keyPropertyType);
    }

    public String getTitleField()
    {
        return titleField.getString();
    }

    public void setTitleField(String titleField)
    {
        this.titleField.set(titleField);
    }

    public int getDiscussionSetting()
    {
        return discussionSetting.intValue();
    }

    public void setDiscussionSetting(int setting)
    {
        discussionSetting.set(setting);
    }

    public boolean getAllowDelete()
    {
        return allowDelete.booleanValue();
    }

    public void setAllowDelete(boolean allowDelete)
    {
        this.allowDelete.set(allowDelete);
    }

    public boolean getAllowUpload()
    {
        return allowUpload.getBool();
    }

    public void setAllowUpload(boolean allowUpload)
    {
        this.allowUpload.set(allowUpload);
    }

    public boolean getAllowExport()
    {
        return allowExport.getBool();
    }

    public void setAllowExport(boolean allowExport)
    {
        this.allowExport.set(allowExport);
    }

    public boolean getEntireListIndex()
    {
        return entireListIndex.getBool();
    }

    public void setEntireListIndex(boolean index)
    {
        this.entireListIndex.set(index);
    }

    public int getEntireListIndexSetting()
    {
        return entireListIndexSetting.intValue();
    }

    public void setEntireListIndexSetting(int setting)
    {
        this.entireListIndexSetting.set(setting);
    }

    public int getEntireListTitleSetting()
    {
        return entireListTitleSetting.intValue();
    }

    public void setEntireListTitleSetting(int setting)
    {
        this.entireListTitleSetting.set(setting);
    }

    public String getEntireListTitleTemplate()
    {
        return entireListTitleTemplate.getString();
    }

    public void setEntireListTitleTemplate(String template)
    {
        this.entireListTitleTemplate.set(template);
    }

    public int getEntireListBodySetting()
    {
        return entireListBodySetting.intValue();
    }

    public void setEntireListBodySetting(int setting)
    {
        this.entireListBodySetting.set(setting);
    }

    public String getEntireListBodyTemplate()
    {
        return entireListBodyTemplate.getString();
    }

    public void setEntireListBodyTemplate(String template)
    {
        this.entireListBodyTemplate.set(template);
    }

    public boolean getEachItemIndex()
    {
        return eachItemIndex.getBool();
    }

    public void setEachItemIndex(boolean index)
    {
        this.eachItemIndex.set(index);
    }

    public int getEachItemTitleSetting()
    {
        return eachItemTitleSetting.intValue();
    }

    public void setEachItemTitleSetting(int setting)
    {
        this.eachItemTitleSetting.set(setting);
    }

    public String getEachItemTitleTemplate()
    {
        return eachItemTitleTemplate.getString();
    }

    public void setEachItemTitleTemplate(String template)
    {
        this.eachItemTitleTemplate.set(template);
    }

    public int getEachItemBodySetting()
    {
        return eachItemBodySetting.intValue();
    }

    public void setEachItemBodySetting(int setting)
    {
        this.eachItemBodySetting.set(setting);
    }

    public String getEachItemBodyTemplate()
    {
        return eachItemBodyTemplate.getString();
    }

    public void setEachItemBodyTemplate(String template)
    {
        this.eachItemBodyTemplate.set(template);
    }

    public boolean getFileAttachmentIndex()
    {
        return fileAttachmentIndex.getBoolean();
    }

    public void setFileAttachmentIndex(boolean index)
    {
        this.fileAttachmentIndex.set(index);
    }

    public void _defaultTitleField(String title)
    {
        _defaultTitleField = title;
    }

    public String getDefaultTitleField()
    {
        return _defaultTitleField;
    }

    public String getTypeURI()
    {
        return _typeURI;
    }

    public void _typeURI(String typeURI)
    {
        _typeURI = typeURI;
    }
}

