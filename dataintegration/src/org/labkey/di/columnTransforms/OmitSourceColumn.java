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
package org.labkey.di.columnTransforms;

import org.labkey.api.di.columnTransform.ColumnTransform;

/**
 * User: tgaluhn
 * Date: 9/27/2016
 *
 * Simple class to mark a source column as to be omitted from the passthrough to target output
 */
public class OmitSourceColumn extends ColumnTransform
{
    @Override
    protected void registerOutput()
    {
        // Do Nothing
    }

    @Override
    protected Object doTransform(Object inputValue)
    {
        // This should never be called, as registerOutput() is a no-op
        throw new UnsupportedOperationException();
    }
}
