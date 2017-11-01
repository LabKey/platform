/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Manages state for a tree where nodes may be collapsed or expanded, like a {@link org.labkey.api.data.Container}
 * hierarchy or a set of wiki documents.
 * User: brittp
 * Date: Apr 10, 2007
 */
public interface Collapsible
{
    void setCollapsed(boolean collapsed);
    boolean isCollapsed();
    @NotNull List<? extends Collapsible> getChildren();
    Collapsible findSubtree(@Nullable String path);
    String getId();
}
