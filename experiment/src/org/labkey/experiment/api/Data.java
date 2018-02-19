/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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
package org.labkey.experiment.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Bean for rows in the exp.data table.
 * User: migra
 * Date: Jun 14, 2005
 */
public class Data extends ProtocolOutput
{
    private String dataFileUrl;
    private boolean generated;
    private Integer classId;

    public Data()
    {
        setCpasType(ExpData.DEFAULT_CPAS_TYPE);
    }

    public String getDataFileUrl()
    {
        return dataFileUrl;
    }

    public void setDataFileUrl(String dataFileUrl)
    {
        this.dataFileUrl = dataFileUrl;
    }

    @Nullable
    public File getFile()
    {
        if (getDataFileUrl() == null)
        {
            return null;
        }
        
        URI uri = FileUtil.createUri(getDataFileUrl());
        if ("file".equals(uri.getScheme()))
        {
            //consider try/catch on IllegalArgumentException
            return new File(uri);
        }
        else
        {
            return null;
        }
    }

    @Nullable
    public Path getFilePath()
    {
        if (null != getDataFileUrl())
        {
            URI uri = FileUtil.createUri(getDataFileUrl());
            if ("file".equals(uri.getScheme()))
                return new File(uri).toPath();

            return FileUtil.stringToPath(getContainer(), getDataFileUrl());
        }
        return null;
    }

    public boolean isGenerated()
    {
        return generated;
    }

    public void setGenerated(boolean generated)
    {
        this.generated = generated;
    }

    public Integer getClassId()
    {
        return classId;
    }

    public void setClassId(Integer classId)
    {
        this.classId = classId;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Data data = (Data) o;

        return !(getRowId() == 0 || getRowId() != data.getRowId());
    }
}
