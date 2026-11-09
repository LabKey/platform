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

import org.labkey.api.jsp.LabKeyJspWriter;

/**
 * Marker interface that asserts that this class's {@code toString()} is safe to render in a browser. In other words, it
 * returns valid HTML with properly encoded text or well-formed JavaScript/JSON. This is used by {@link LabKeyJspWriter}
 * to validate attempts to render {@code Object}s.
 */
public interface SafeToRender
{
    /**
     * Must return well-formed HTML or JavaScript. This method definition is a no-op (Object implements toString()), but
     * it's included here as a reminder of this requirement and to provide easy inspection to verify well-formedness.
     */
    @Override
    String toString();
}
