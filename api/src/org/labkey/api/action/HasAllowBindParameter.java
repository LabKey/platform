/*
 * Copyright (c) 2022-2026 LabKey Corporation
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

import org.labkey.api.collections.CaseInsensitiveHashSet;

import java.util.Set;
import java.util.function.Predicate;

/**
 * This interface provides a mechanism for MVC Form objects to control which fields will be bound.  By default, only simple
 * fields (no object path following) are allowed.  Forms may implement this interface to either a) allow any of the properties that
 * are restricted by default, or b) allow complex binding scenarios.
 */
public interface HasAllowBindParameter
{
    Predicate<String> allowBindParameter();

    Set<String> disallowed = new CaseInsensitiveHashSet("class","container","containerid","request","response","user","viewcontext");
    Predicate<String> defaultPredicate = (name) ->
    {
        if (name.startsWith(SpringActionController.FIELD_MARKER))
            name = name.substring(SpringActionController.FIELD_MARKER.length());
        return !name.contains(".") && !disallowed.contains(name);
    };

    static Predicate<String>  getDefaultPredicate()
    {
        return defaultPredicate;
    }
}
