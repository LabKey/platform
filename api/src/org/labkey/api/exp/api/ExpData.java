/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.labkey.api.security.User;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentDataHandler;
import org.apache.activemq.broker.region.cursors.PendingMessageCursor;

import java.net.URI;
import java.util.Date;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

public interface ExpData extends ExpProtocolOutput
{
    ExpProtocolApplication[] getTargetApplications();
    ExpRun[] getTargetRuns();
    DataType getDataType();
    URI getDataFileURI();
    File getDataFile();
    void delete(User user) throws Exception;

    void setDataFileURI(URI uri);
    void save(User user);
    Date getCreated();

    ExperimentDataHandler findDataHandler();

    String getDataFileUrl();

    File getFile();

    boolean isInlineImage();

    boolean isFileOnDisk();

    void setDataFileUrl(String s);
}
