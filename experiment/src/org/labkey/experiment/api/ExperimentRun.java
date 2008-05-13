/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import org.labkey.experiment.api.IdentifiableEntity;
import org.labkey.api.exp.api.ExperimentService;

/**
 * User: migra
 * Date: Jun 14, 2005
 * Time: 2:46:21 PM
 */
public class ExperimentRun extends IdentifiableEntity
{
    private int rowId;
    private String protocolLSID;
    private String filePathRoot;
    private String comments;

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public String getProtocolLSID()
    {
        return protocolLSID;
    }

    public void setProtocolLSID(String protocolLSID)
    {
        this.protocolLSID = protocolLSID;
    }

    public String getFilePathRoot()
    {
        return filePathRoot;
    }

    public void setFilePathRoot(String filePathRoot)
    {
        this.filePathRoot = filePathRoot;
    }

    public String getComments()
    {
        return comments;
    }

    public void setComments(String comments)
    {
        this.comments = comments;
    }

    public void setName(String name)
    {
        int maxLength = ExperimentService.get().getTinfoExperimentRun().getColumn("Name").getScale();
        if (name != null && name.length() > maxLength)
        {
            name = name.substring(0, maxLength - "...".length()) + "...";
        }
        super.setName(name);
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ExperimentRun that = (ExperimentRun) o;

        if (rowId != that.rowId) return false;
        if (protocolLSID != null ? !protocolLSID.equals(that.protocolLSID) : that.protocolLSID != null) return false;

        return true;
    }

    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + rowId;
        result = 31 * result + (protocolLSID != null ? protocolLSID.hashCode() : 0);
        return result;
    }
}
