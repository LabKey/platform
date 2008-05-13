/*
 * Copyright (c) 2007 LabKey Corporation
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
package org.labkey.pipeline.mule.transformers;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * AbstractMuleSingleValueConverter class
 * Helper class because Mule does not support
 * com.thoughtworks.xstream.converters.SingleValueConverter (MULE-2482)<p/>
 * Created: Oct 4, 2007
 *
 * @author bmaclean
 */
abstract public class AbstractMuleSingleValueConverter implements Converter
{
    public abstract boolean canConvert(Class type);

    public String toString(Object obj)
    {
        return obj == null ? null : obj.toString();
    }

    public abstract Object fromString(String str);

    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context)
    {
        writer.setValue(toString(source));
    }

    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context)
    {
        return fromString(reader.getValue());
    }
}
