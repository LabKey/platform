/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
package org.labkey.api.reports.model;

import org.labkey.api.security.User;

/**
 * User: klum
 * Date: Oct 18, 2011
 * Time: 5:00:58 PM
 */
public interface ViewCategoryListener
{
    void categoryDeleted(User user, ViewCategory category) throws Exception;
    void categoryCreated(User user, ViewCategory category) throws Exception;
    void categoryUpdated(User user, ViewCategory category) throws Exception;
}
