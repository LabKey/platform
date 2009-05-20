/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.qc;

import org.labkey.api.exp.api.DataType;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 7, 2009
 */
public class DefaultTransformResult implements TransformResult
{
    private Map<DataType, File> _dataMap = new HashMap<DataType, File>();

    public DefaultTransformResult(Map<DataType, File> dataMap)
    {
        _dataMap = dataMap;
    }

    public boolean isEmpty()
    {
        return _dataMap.isEmpty();
    }

    public Map<DataType, File> getTransformedData()
    {
        return _dataMap;
    }

    public static TransformResult createEmptyResult()
    {
        return new TransformResult()
        {
            public Map<DataType, File> getTransformedData()
            {
                return Collections.emptyMap();
            }
            public boolean isEmpty() {return true;}
        };
    }
}
