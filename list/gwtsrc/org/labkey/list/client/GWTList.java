/*
 * Copyright (c) 2010 LabKey Corporation
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
 * Created by IntelliJ IDEA.
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
    public BooleanProperty indexMetaData = new BooleanProperty(true);

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
        this._listId.setInt(listId);
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

    public boolean getIndexMetaData()
    {
        return indexMetaData.getBool();
    }

    public void setIndexMetaData(boolean shouldIndexMetaData)
    {
        this.indexMetaData.set(shouldIndexMetaData);
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

