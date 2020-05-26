/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.data;

/**
 * Interface for data-object classes, normally backed by a row in the database, that track who created and last modified
 * a piece of data, and when.
 * User: jeckels
 * Date: Dec 20, 2005
 */
public interface Ownable
{
    int getModifiedBy();
    void setModifiedBy(int modifiedBy);
    int getCreatedBy();
    void setCreatedBy(int createdBy);

    String getContainerId();
}
