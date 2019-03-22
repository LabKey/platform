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

package org.labkey.api.action;

import org.labkey.api.view.NavTree;

/**
 * Interface for {@link Action} implementations that want to fill in a nav trail to show at the top of the page.
 * User: matthewb
 * Date: May 21, 2007
 */
public interface NavTrailAction
{
    /** NOTE: this works a little different than NavTrailConfig!
     *
     * Return the entire nav trail for this page, including the entry
     * representing this page/action with URL.
     *
     * Base class implementations may use the entry as the page title by default.
     *
     * @return Return the tree handed in for convenience
     */
    NavTree appendNavTrail(NavTree root);
}