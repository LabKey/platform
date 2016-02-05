/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.MinorConfigurationException;
import org.labkey.data.xml.CustomizerType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Hook point for customizing a user schema and the tables and queries that it contains.
 * User: kevink
 * Date: 5/10/13
 */
public interface UserSchemaCustomizer
{
    public void configure(CustomizerType schemaCustomizer);

    public void afterConstruct(UserSchema schema);

    public void afterConstruct(UserSchema schema, TableInfo table);

    public void afterConstruct(UserSchema schema, QueryDefinition def);

    public static class Factory
    {
        public static Collection<UserSchemaCustomizer> create(CustomizerType[] xmlSchemaCustomizers)
        {
            if (xmlSchemaCustomizers == null || xmlSchemaCustomizers.length == 0)
                return Collections.emptyList();

            ArrayList<UserSchemaCustomizer> customizers = new ArrayList<>(xmlSchemaCustomizers.length);
            for (CustomizerType xmlSchemaCustomizer : xmlSchemaCustomizers)
            {
                UserSchemaCustomizer customizer = UserSchemaCustomizer.Factory.create(xmlSchemaCustomizer);
                if (customizer != null)
                    customizers.add(customizer);
            }
            return customizers;
        }

        public static UserSchemaCustomizer create(CustomizerType xmlSchemaCustomizer)
        {
            if (xmlSchemaCustomizer == null)
                return null;

            String className = xmlSchemaCustomizer.getClass1();
            if (className == null || className.length() == 0)
                throw new MinorConfigurationException("Schema customizer requires class attribute");

            try
            {
                Class c = Class.forName(className);
                if (!(UserSchemaCustomizer.class.isAssignableFrom(c)))
                {
                    Logger.getLogger(UserSchemaCustomizer.class).warn("Class '" + c.getName() + "' is not an implementation of " + UserSchemaCustomizer.class.getName());
                }
                else
                {
                    Class<UserSchemaCustomizer> customizerClass = (Class<UserSchemaCustomizer>)c;
                    UserSchemaCustomizer customizer = customizerClass.newInstance();
                    customizer.configure(xmlSchemaCustomizer);
                    return customizer;
                }
            }
            catch (ClassNotFoundException | InstantiationException | IllegalAccessException e)
            {
                Logger.getLogger(UserSchemaCustomizer.class).warn(e.toString());
            }

            return null;
        }
    }
}
