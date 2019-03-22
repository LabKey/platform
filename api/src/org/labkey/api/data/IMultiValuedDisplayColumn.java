/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

import java.util.List;

/**
 * User: kevink
 * Date: 6/20/16
 */
public interface IMultiValuedDisplayColumn
{
    List<String> renderURLs(RenderContext ctx);

    List<Object> getDisplayValues(RenderContext ctx);

    List<String> getTsvFormattedValues(RenderContext ctx);

    List<String> getFormattedTexts(RenderContext ctx);

    List<Object> getJsonValues(RenderContext ctx);

}
