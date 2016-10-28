/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.BoundMap;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * User: mbellew
 * Date: Apr 1, 2004
 * Time: 10:27:13 AM
 */
public interface ObjectFactory<K>
{
    K fromMap(Map<String, ?> m);

    K fromMap(K bean, Map<String, ?> m);

    Map<String, Object> toMap(K bean, @Nullable Map<String, Object> m);

    K handle(ResultSet rs) throws SQLException;

    ArrayList<K> handleArrayList(ResultSet rs) throws SQLException;

    K[] handleArray(ResultSet rs) throws SQLException;


    public static class Registry
    {
        private static final Logger _log = Logger.getLogger(Registry.class);
        private static final Map<Class, ObjectFactory> _registry = new ConcurrentHashMap<>(64);

        public static <K> void register(Class<K> clss, ObjectFactory<K> f)
        {
            _registry.put(clss, f);
        }

        public static <K> ObjectFactory<K> getFactory(Class<K> clss)
        {
            ObjectFactory<K> f = (ObjectFactory<K>) _registry.get(clss);

            if (f == null && (BoundMap.class.isAssignableFrom(clss) || !java.util.Map.class.isAssignableFrom(clss)))
            {
                try
                {
                    // Make sure the class is loaded in case it statically registers a custom factory 
                    Class.forName(clss.getName());
                    f = (ObjectFactory<K>) _registry.get(clss);

                    if (f == null)
                    {
                        f = new BeanObjectFactory<>(clss);
                        _registry.put(clss, f);
                    }
                }
                catch (RuntimeException x)
                {
                    _log.error("getFactory: failed to create bean factory", x);
                    throw x;
                }
                catch (Exception x)
                {
                    _log.error("getFactory: failed to create bean factory", x);
                    throw new IllegalStateException(x);
                }
            }

            return f;
        }
    }
}
