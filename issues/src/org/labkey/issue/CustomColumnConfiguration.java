/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.issue;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.issue.model.CustomColumn;

import java.util.Collection;
import java.util.Map;

/**
 * Created by klum on 4/19/2016.
 */
public interface CustomColumnConfiguration
{
    CustomColumn getCustomColumn(String name);

    Map<String, DomainProperty> getPropertyMap();
    Collection<DomainProperty> getCustomProperties();

    @Deprecated
    Collection<CustomColumn> getCustomColumns();
    Collection<CustomColumn> getCustomColumns(User user);

    @Deprecated
    boolean shouldDisplay(String name);
    boolean shouldDisplay(User user, String name);

    @Nullable
    String getCaption(String name);
}
