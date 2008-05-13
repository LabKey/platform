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

package org.labkey.biotrue.objectmodel;

import org.labkey.biotrue.datamodel.Entity;
import org.labkey.biotrue.datamodel.BtManager;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class BtEntity
{
    BtServer _server;
    Entity _data;

    static public BtEntity fromId(int id)
    {
        Entity entity = BtManager.get().getEntity(id);
        if (entity == null)
        {
            return null;
        }
        return new BtEntity(entity);
    }
    public BtEntity(Entity data)
    {
        _data = data;
        _server = BtServer.fromId(data.getServerId());
    }

    public BtEntity(BtServer server)
    {
        _data = new Entity();
        _data.setServerId(server.getRowId());
    }

    public String getBioTrue_Ent()
    {
        return _data.getBioTrue_Type();
    }

    public String getBioTrue_Id()
    {
        return _data.getBioTrue_Id();
    }

    public void setBioTrue_Ent(String ent)
    {
        _data.setBioTrue_Type(ent);
    }

    public void setBioTrue_Id(String id)
    {
        _data.setBioTrue_Id(id);
    }

    public BtServer getServer()
    {
        return _server;
    }

    public int getRowId()
    {
        return _data.getRowId();
    }

    public BtEntity getParent()
    {
        if (_data.getParentId() == 0)
        {
            return null;
        }
        Entity parent = BtManager.get().getEntity(_data.getParentId());
        if (parent != null)
        {
            return new BtEntity(parent);
        }
        return null;
    }

    public File getPhysicalFile()
    {
        if (_data.getPhysicalName() == null)
            return null;
        File parentDirectory;
        if (_data.getParentId() == 0)
        {
            parentDirectory = _server.getPhysicalRoot();
        }
        else
        {
            BtEntity parent = getParent();
            parentDirectory = parent.getPhysicalFile();
        }
        return new File(parentDirectory, _data.getPhysicalName());
    }

    public boolean hasPhysicalName()
    {
        return _data.getPhysicalName() != null;
    }
    static private File findUniqueFilename(File parentDirectory, String name)
    {
        File fileTry = new File(parentDirectory, name);
        if (!fileTry.exists())
            return fileTry;
        for (int i = 0; i < 1000; i ++)
        {
            fileTry = new File(parentDirectory, name + i);
            if (!fileTry.exists())
            {
                return fileTry;
            }
        }
        throw new IllegalStateException("Too many files named " + name + " in " + parentDirectory);
    }

    public File ensurePhysicalDirectory() throws Exception
    {
        if (_data.getPhysicalName() != null)
        {
            return getPhysicalFile();
        }
        File parentDirectory;
        if (_data.getParentId() == 0)
        {
            parentDirectory = _server.getPhysicalRoot();
        }
        else
        {
            BtEntity parent = getParent();
            parentDirectory = parent.ensurePhysicalDirectory();
        }
        String fileName = _data.getBioTrue_Name();
        File file = findUniqueFilename(parentDirectory, fileName);
        if (!file.mkdir())
        {
            throw new IllegalStateException("Unable to create directory " + file);
        }
        _data.setPhysicalName(file.getName());
        _data = BtManager.get().updateEntity(_data);
        return getPhysicalFile();
    }

    public File ensurePhysicalFiles(List<File> sourceFiles) throws Exception
    {
        if (_data.getPhysicalName() != null)
        {
            throw new IllegalStateException("File already exists.");
        }
        File dirParent = getParent() == null ? _server.getPhysicalRoot() : getParent().ensurePhysicalDirectory();
        for (File source : sourceFiles)
        {
            File destFile = findUniqueFilename(dirParent, source.getName());
            if (!source.renameTo(destFile))
            {
                throw new IOException("Error renaming file " + destFile);
            }

            if (_data.getBioTrue_Name().equals(source.getName()))
                _data.setPhysicalName(destFile.getName());
        }
        _data = BtManager.get().updateEntity(_data);
        return getPhysicalFile();
    }

    public boolean canBeIncomplete()
    {
        return "run".equals(getBioTrue_Ent());
    }

    public BtEntity ensureChild(String biotrue_id, String ent, String name) throws Exception
    {
        return ensureChild(_server, this, biotrue_id, ent, name);
    }

    public BtEntity getChild(String biotrue_id) throws Exception
    {
        Entity ret = BtManager.get().getEntity(_server._server, _data, biotrue_id);
        if (ret == null)
            return null;
        return new BtEntity(ret);
    }

    static public BtEntity ensureChild(BtServer server, BtEntity parent, String biotrue_id, String ent, String name) throws Exception
    {
        Entity ret = BtManager.get().ensureEntity(server._server, parent == null ? null : parent._data, biotrue_id, ent, name);
        if (ret == null)
            return null;
        return new BtEntity(ret);
    }
}
