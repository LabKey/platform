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

package org.labkey.api.action;

import org.apache.log4j.Logger;
import org.labkey.api.view.NavTree;

/**
 * Interface for {@link Action} implementations that want to fill in a nav trail to show at the top of the page.
 * User: matthewb
 * Date: May 21, 2007
 */
public interface NavTrailAction
{
    Logger LOG = Logger.getLogger(NavTrailAction.class);

    @Deprecated()  // Implement addNavTrail() instead
    default NavTree appendNavTrail(NavTree root)
    {
        throw new IllegalStateException(getClass().getName() + " must implement addNavTrail()!");
    }

    default void addNavTrail(NavTree root)
    {
        appendNavTrail(root);
        // The last method didn't throw IllegalStateException, so we know this action implements appendNavTrail()
        LOG.warn(getClass().getName() + " should implement addNavTrail() instead of appendNavTrail()! The appendNavTrail() method is deprecated and will be removed shortly.");
    }
}