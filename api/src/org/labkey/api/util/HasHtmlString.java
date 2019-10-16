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

public interface HasHtmlString extends DOM.Renderable
{
    /**
     * Must be consistent with {@code toString()}! JSP rendering of objects will call {@code obj.getHtmlString().toString()}
     * in dev mode but {@code obj.toString()} in production mode. Most implementations will either implement {@code toString()}
     * as {@code return getHtmlString.toString()} or implement {@code getHtmlString()} as {@code HtmlString.unsafe(toString);}.
     */
    HtmlString getHtmlString();

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