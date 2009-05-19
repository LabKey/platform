/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.util.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 4:17:59 PM
 */
public interface Writer<T>
{
    @Nullable
    public String getSelectionText();
    public void write(T object, ExportContext ctx, VirtualFile fs) throws Exception;
}
