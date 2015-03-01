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

import java.io.Serializable;
import java.util.Date;

/**
 * Created by bimber on 2/17/2015.
 */
public interface ReadData extends Serializable
{
    public Integer getRowid();

    public Integer getReadset();

    public String getPlatformUnit();

    public String getCenterName();

    public Date getDate();

    public Integer getFileId1();

    public Integer getFileId2();

    public String getDescription();

    public String getContainer();

    public Date getCreated();

    public Integer getCreatedBy();

    public Date getModified();

    public Integer getModifiedBy();
}
