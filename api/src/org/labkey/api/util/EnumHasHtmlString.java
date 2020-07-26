/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.util;

/**
 * Turns any Enum into a HasHtmlString, where each constant returns an HtmlString containing an encoded version of its
 * toString(). Enums that implement this interface can be safely rendered from a JSP.
 */

// TODO: Remove type parameter. This requires a multi-repo commit, so we'll change all the implementors first, then circle back to remove it here.
public interface EnumHasHtmlString<E extends Enum<?>> extends HasHtmlString
{
    @Override
    default HtmlString getHtmlString()
    {
        return HtmlString.of(toString());
    }
}
