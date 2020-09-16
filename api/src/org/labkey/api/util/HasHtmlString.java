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

import java.io.IOException;

/**
 * Implements {@code HtmlString getHtmlString()}. Provides no guarantee about what {@code toString()} returns (e.g., it
 * might or might not require HTML encoding); see {@link SafeToRender} for that.
 */
public interface HasHtmlString extends DOM.Renderable
{
    HtmlString getHtmlString();

    /**
     * HasHtmlString provides no guarantees about what this method returns, but it still can be convenient to inspect
     * implementations; {@code Object} appearing in the implementation list might suggest a problem, for example, a
     * builder that should be implementing {@code toString()} and {@code SafeToRender} for convenience.
     */
    String toString();

    @Override
    default Appendable appendTo(Appendable builder)
    {
        try
        {
            return builder.append(this.getHtmlString().toString());
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }
}