/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.view.DisplayElement;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Apr 7, 2010
 * Time: 10:15:25 AM
 */

/**
 * Represents configuration information for a specific button in a button bar configuration.
 * Currently this is used only with the QueryWebPart
 */
public interface ButtonConfig
{
    /**
     * Insert position: 0 for head, -1 for tail, or the index at which to insert.
     */
    public Integer getInsertPosition();
    public String getInsertBefore();
    public String getInsertAfter();
    public DisplayElement createButton(RenderContext ctx, List<DisplayElement> originalButtons);
}
