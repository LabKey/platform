/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
package org.labkey.api.action;

import org.labkey.api.data.ObjectFactory;

import java.util.Map;
import java.util.HashMap;
/*
 * User: brittp
 * Date: Dec 18, 2008
 * Time: 12:07:24 PM
 */

public class ApiBeanResponse<BeanClass> implements ApiResponse
{
    private BeanClass _bean;
    private Class<BeanClass> _clss;

    public ApiBeanResponse(BeanClass bean, Class<BeanClass> clss)
    {
        _clss = clss;
        _bean = bean;
    }

    public Map<String, ?> getProperties()
    {
        ObjectFactory<BeanClass> f = ObjectFactory.Registry.getFactory(_clss);
        Map<String, Object> map = new HashMap<>();
        f.toMap(_bean, map);
        return map;
    }
}