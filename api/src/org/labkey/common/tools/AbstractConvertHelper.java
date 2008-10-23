/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
package org.labkey.common.tools;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.beanutils.converters.*;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * User: arauch
 * Date: Mar 14, 2006
 * Time: 8:56:26 AM
 */
public abstract class AbstractConvertHelper
{
    /*
    * Register converters that are shared between cpas and msInspect. Replace default registered converters
    * which return default values (e.g. 0) for errors.
    */
    protected void register()
    {
        _register(new NullSafeConverter(new BigDecimalConverter()), BigDecimal.class);
        _register(new NullSafeConverter(new BigIntegerConverter()), BigInteger.class);
        _register(new NullSafeConverter(new BooleanArrayConverter()), boolean[].class);
        _register(new ByteConverter(), Byte.TYPE);
        _register(new NullSafeConverter(new ByteConverter()), Byte.class);
        _register(new CharacterConverter(), Character.TYPE);
        _register(new NullSafeConverter(new CharacterConverter()), Character.class);
        _register(new NullSafeConverter(new CharacterArrayConverter()), char[].class);
        _register(new NullSafeConverter(new ClassConverter()), Class.class);
        _register(new NullSafeConverter(new DoubleArrayConverter()), double[].class);
        _register(new FloatArrayConverter(), float[].class);
        _register(new NullSafeConverter(new IntegerArrayConverter()), int[].class);
        _register(new LongConverter(), Long.TYPE);
        _register(new NullSafeConverter(new LongConverter()), Long.class);
        _register(new NullSafeConverter(new LongArrayConverter()), long[].class);
        _register(new ShortConverter(), Short.TYPE);
        _register(new NullSafeConverter(new ShortConverter()), Short.class);
        _register(new NullSafeConverter(new ShortArrayConverter()), short[].class);
        _register(new NullSafeConverter(new StringArrayConverter()), String[].class);
        _register(new NullSafeConverter(new SqlDateConverter()), java.sql.Date.class);
        _register(new NullSafeConverter(new SqlTimeConverter()), java.sql.Time.class);
        _register(new NullSafeConverter(new SqlTimestampConverter()), java.sql.Time.class);
    }


    protected void _register(Converter conv, Class cl)
    {
        ConvertUtils.register(conv, cl);
    }


    public static class NullSafeConverter implements Converter
    {
        Converter _converter;

        public NullSafeConverter(Converter converter)
        {
            _converter = converter;
        }

        public Object convert(Class clss, Object o)
        {
            if (o instanceof String)
            {
                o = ((String) o).trim();
                if (((String) o).length() == 0)
                    return null;
            }

            return null == o || "".equals(o) ? null : _converter.convert(clss, o);
        }
    }
}
