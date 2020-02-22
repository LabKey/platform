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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

public final class HtmlString implements DOM.Renderable
{
    // Helpful constants for convenience (and efficiency)
    public static HtmlString EMPTY_STRING = HtmlString.of("");
    public static HtmlString NBSP = HtmlString.unsafe("&nbsp;");
    public static HtmlString NDASH = HtmlString.unsafe("&ndash;");

    private final String _s;

    /**
     * Returns an HtmlString that wraps an HTML encoded version of the passed in String.
     * @param s A String. A null value results in an empty HtmlString (equivalent of HtmlString.of("")).
     * @return An HtmlString that encodes and wraps the String.
     */
    public static @NotNull HtmlString of(@Nullable String s)
    {
        return new HtmlString(PageFlowUtil.filter(s));
    }

    /**
     * Returns an HtmlString that wraps an HTML encoded version of the passed in String, with the option to preserve
     * whitespace.
     * @param s A String. A null value results in an empty HtmlString (equivalent of HtmlString.of("")).
     * @param translateWhiteSpace A flag that determines whether whitespace should be encoded or not
     * @return An HtmlString that encodes and wraps the String, respecting the translateWhiteSpace flag.
     */
    public static @NotNull HtmlString of(@Nullable String s, boolean translateWhiteSpace)
    {
        return new HtmlString(PageFlowUtil.filter(s, translateWhiteSpace));
    }

    /**
     * Returns an HtmlString that wraps the passed in String <b>without applying any HTML encoding.</b> Use of this method
     * is dangerous and can lead to security vulnerabilities and broken HTML pages. You are responsible for ensuring that
     * all parts of the String are correctly encoded.
     * @param s A String. A null value results in an empty HtmlString (equivalent of HtmlString.of("")).
     * @return An HtmlString that wraps the String without encoding.
     */
    public static @NotNull HtmlString unsafe(@Nullable String s)
    {
        return new HtmlString(null == s ? "" : s);
    }

    // Callers use factory methods of() and unsafe() instead
    private HtmlString(String s)
    {
        _s = s;
    }

    @Override
    public String toString()
    {
        return _s;
    }

    @Override
    public Appendable appendTo(Appendable sb)
    {
        try
        {
            return sb.append(_s);
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HtmlString that = (HtmlString) o;
        return Objects.equals(_s, that._s);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(_s);
    }
}
