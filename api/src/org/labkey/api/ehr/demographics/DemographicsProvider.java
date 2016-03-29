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
package org.labkey.api.ehr.demographics;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 7/9/13
 * Time: 9:24 PM
 */
public interface DemographicsProvider
{
    public static final int MAXIMUM_BATCH_SIZE = 1000;

    public String getName();

    public boolean isAvailable(Container c);

    public Map<String, Map<String, Object>> getProperties(Container c, User u, Collection<String> ids);

    // report whether this provider requires calculation of cached data when a row in this passed table has changed
    // this is a somewhat blunt approach, but it errs on the side of re-calculating
    public boolean requiresRecalc(String schema, String query);

    // returns the top-level keys used by this provider
    public Set<String> getKeys();

    public Collection<String> getKeysToTest();

    public Set<String> getIdsToUpdate(Container c, String id, Map<String, Object> originalProps, Map<String, Object> newProps);
}
