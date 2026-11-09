/*
 * Copyright (c) 2020-2026 LabKey Corporation
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

import java.util.stream.Collector;

/**
 * Useful when rendering intermingled HTML and JavaScript
 */
public class SafeToRenderBuilder implements SafeToRender
{
    private final StringBuilder _sb = new StringBuilder();

    // Use of() factory methods
    private SafeToRenderBuilder()
    {
    }

    public static SafeToRenderBuilder of()
    {
        return new SafeToRenderBuilder();
    }

    public SafeToRenderBuilder append(SafeToRender str)
    {
        _sb.append(str.toString());
        return this;
    }

    public int length()
    {
        return _sb.length();
    }

    public SafeToRender getSafeToRender()
    {
        return HtmlString.unsafe(_sb.toString());
    }

    @Override
    public String toString()
    {
        return getSafeToRender().toString();
    }

    // Collects a stream of SafeToRenders into a SafeToRenderBuilder. TODO: Test this!
    public static Collector<SafeToRender, SafeToRenderBuilder, SafeToRenderBuilder> collector()
    {
        return Collector.of(
            SafeToRenderBuilder::new,
            SafeToRenderBuilder::append,
            (jsb1, jsb2)->of().append(jsb1.getSafeToRender()).append(jsb2.getSafeToRender())
        );
    }
}
