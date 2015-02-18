/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.sequenceanalysis.model;

import org.json.JSONObject;
import org.labkey.api.exp.api.ExpData;

import java.io.File;
import java.io.Serializable;
import java.util.Date;

/**
 * User: bimber
 * Date: 9/22/12
 * Time: 5:01 PM
 */
public interface AnalysisModel extends Serializable
{
    public Integer getAnalysisId();

    public Integer getRunId();

    public String getContainer();

    public Integer getReadset();

    public Integer getAlignmentFile();

    public File getAlignmentFileObject();

    public ExpData getAlignmentData();

    @Deprecated
    public Integer getReferenceLibrary();

    public void setReferenceLibrary(Integer libraryId);

    public ExpData getReferenceLibraryData();

    public File getReferenceLibraryFile();

    public String getType();

    public Date getModified();

    public Integer getModifiedby();

    public Date getCreated();

    public Integer getCreatedby();

    public Integer getRowId();

    public String getDescription();

    public String getSynopsis();

    public void setSynopsis(String synopsis);

    public Integer getLibraryId();

    public void setLibraryId(Integer libraryId);

    public JSONObject toJSON();
}
