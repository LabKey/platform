/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.api.ehr.history;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 3/6/13
 * Time: 11:12 AM
 */
public interface LabworkType
{
    public String getName();

    public boolean isEnabled(Container c);

    public List<String> getResults(Container c, User u, String runId, boolean redacted);

    public Map<String, List<String>> getResults(Container c, User u, List<String> runIds, boolean redacted);

    public Map<String, List<String>> getResults(Container c, User u, String id, Date minDate, Date maxDate, boolean redacted);
}
