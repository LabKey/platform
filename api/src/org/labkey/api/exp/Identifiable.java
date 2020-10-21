/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.exp;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.view.ActionURL;

/**
 * Base functionality for objects that have an LSID.
 * User: migra
 * Date: Jun 14, 2005
 */
public interface Identifiable
{
    String getLSID();

    default String getLSIDNamespacePrefix()
    {
        return new Lsid(getLSID()).getNamespacePrefix();
    }

    String getName();

    Container getContainer();

    default @Nullable ActionURL detailsURL()
    {
        return null;
    }

    default @Nullable QueryRowReference getQueryRowReference()
    {
        return null;
    }

    /**
     * Get the corresponding ExpObject for this Identifiable, if there is one.
     */
    default @Nullable ExpObject getExpObject()
    {
        return null;
    }
}
