/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.wiki;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * User: kevink
 * Date: 6/26/13
 */
public interface WikiChangeListener
{
    /**
     * Called when a wiki is created.
     *
     * @param user The user that initiated the change.
     * @param c The container the wiki is saved in.
     * @param name The name of the wiki.
     */
    void wikiCreated(User user, Container c, String name);

    /**
     * Called when a wiki is updated.
     *
     * @param user The user that initiated the change.
     * @param c The container the wiki is saved in.
     * @param name The name of the wiki.
     */
    void wikiChanged(User user, Container c, String name);

    /**
     * Called when a wiki is deleted.
     *
     * @param user The user that initiated the change.
     * @param c The container the wiki is saved in.
     * @param name The name of the wiki.
     */
    void wikiDeleted(User user, Container c, String name);
}
