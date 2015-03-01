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
package org.labkey.api.sequenceanalysis.model;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.UnauthorizedException;

import java.io.Serializable;
import java.util.Date;

/**
 * User: bimber
 * Date: 11/24/12
 * Time: 11:08 PM
 */
public interface Readset extends Serializable
{
    public Integer getSampleId();

    public String getSubjectId();

    public Date getSampleDate();

    public String getPlatform();

    public String getApplication();

    public String getSampleType();

    public String getLibraryType();

    public String getName();

    public Integer getInstrumentRunId();

    public Integer getReadsetId();

    public String getBarcode5();

    public String getBarcode3();

    public int getRowId();

    public String getContainer();

    public Date getCreated();

    public Integer getCreatedBy();

    public Date getModified();

    public Integer getModifiedBy();

    public String getComments();

    public Integer getRunId();

    public boolean hasPairedData();
}
