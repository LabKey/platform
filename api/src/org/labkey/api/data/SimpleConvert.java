/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.beanutils.ConversionException;

/** Implementing classes must implement convert() or getConvertFn() */
public interface SimpleConvert
{
    Object convert(Object val) throws ConversionException;

    /**
     * Some implementations may be able to pass-through or precompute a SimpleConvert, and it may
     * be faster to cache and use that implementation.  In a loop or dataiterator it may be faster to
     * use getConvertFn() e.g.
     *      var fn = col.getConvertFn();
     *      while (...)
     *          var converted = fn.convert(val);
     * <p></p>
     * instead of
     *      while(...)
     *          var converted = col.convert(val);
     */
    default SimpleConvert getConvertFn()
    {
        return this;
    }

    SimpleConvert identity = (v) -> v;
}