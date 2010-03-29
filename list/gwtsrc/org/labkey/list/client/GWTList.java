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
    private IntegerProperty _listId = new IntegerProperty(0);
    public StringProperty name = new StringProperty();
    private StringProperty _description = new StringProperty();
//    private StringProperty _getTypeURI = new StringProperty();
    public StringProperty keyPropertyName = new StringProperty();
    public StringProperty keyPropertyType = new StringProperty();
    public StringProperty titleField = new StringProperty();
    private IntegerProperty _discussionSetting = new IntegerProperty(0); // DiscussionSetting.None
    private BooleanProperty _allowDelete = new BooleanProperty();
    private BooleanProperty _allowUpload = new BooleanProperty();
    private BooleanProperty _allowExport = new BooleanProperty();

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
        return _description.getString();
    }

    public void setDescription(String description)
    {
        _description.set(description);
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
        return _discussionSetting.intValue();
    }

    public void setDiscussionSetting(int setting)
    {
        _discussionSetting.set(setting);
    }

    public boolean getAllowDelete()
    {
        return _allowDelete.booleanValue();
    }

    public void setAllowDelete(boolean allowDelete)
    {
        _allowDelete.set(allowDelete);
    }

    public boolean getAllowUpload()
    {
        return _allowUpload.getBool();
    }

    public void setAllowUpload(boolean allowImport)
    {
        _allowUpload.set(allowImport);
    }

    public boolean getAllowExport()
    {
        return _allowExport.getBool();
    }

    public void setAllowExport(boolean allowExport)
    {
        _allowExport.set(allowExport);
    }
}

