/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.sequenceanalysis;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;

import java.io.File;
import java.io.Serializable;
import java.util.Date;

/**
 * Created by bimber on 1/7/2015.
 */
public class SequenceOutputFile implements Serializable
{
    private Integer _rowid;
    private String _name;
    private String _description;
    private Integer _dataId;
    private Integer _library_id;
    private Integer _readset;
    private Integer _analysis_id;
    private Integer _runid;
    private String _category;
    private Boolean _intermediate;
    private String _container;
    private Integer _createdby;
    private Date _created;
    private Integer _modifiedby;
    private Date _modified;
    private File _file;

    public Integer getRowid()
    {
        return _rowid;
    }

    public void setRowid(Integer rowid)
    {
        _rowid = rowid;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public Integer getDataId()
    {
        return _dataId;
    }

    public void setDataId(Integer dataId)
    {
        _dataId = dataId;
    }

    public Integer getLibrary_id()
    {
        return _library_id;
    }

    public void setLibrary_id(Integer library_id)
    {
        _library_id = library_id;
    }

    public Integer getReadset()
    {
        return _readset;
    }

    public void setReadset(Integer readset)
    {
        _readset = readset;
    }

    public Integer getAnalysis_id()
    {
        return _analysis_id;
    }

    public void setAnalysis_id(Integer analysis_id)
    {
        _analysis_id = analysis_id;
    }

    public Integer getRunId()
    {
        return _runid;
    }

    public void setRunId(Integer runid)
    {
        _runid = runid;
    }

    public String getCategory()
    {
        return _category;
    }

    public void setCategory(String category)
    {
        _category = category;
    }

    public Boolean isIntermediate()
    {
        return _intermediate;
    }

    public void setIntermediate(Boolean intermediate)
    {
        _intermediate = intermediate;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public Integer getCreatedby()
    {
        return _createdby;
    }

    public void setCreatedby(Integer createdby)
    {
        _createdby = createdby;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public Integer getModifiedby()
    {
        return _modifiedby;
    }

    public void setModifiedby(Integer modifiedby)
    {
        _modifiedby = modifiedby;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public static SequenceOutputFile getForId(Integer rowId)
    {
        return new TableSelector(DbSchema.get("sequenceanalysis").getTable("outputfiles")).getObject(rowId, SequenceOutputFile.class);
    }

    public ExpData getExpData()
    {
        return _dataId > 0 ? ExperimentService.get().getExpData(_dataId) : null;
    }

    public File getFile()
    {
        if (_file != null)
        {
            return _file;
        }

        ExpData data = getExpData();

        return data == null ? null : data.getFile();
    }

    public void setFile(File file)
    {
        _file = file;
    }
}
