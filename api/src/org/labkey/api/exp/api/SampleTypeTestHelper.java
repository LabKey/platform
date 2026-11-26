/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.QueryUpdateServiceTestUtil;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Test helpers for creating a sample type and writing to its samples.* table */
public class SampleTypeTestHelper
{
    public static GWTPropertyDescriptor nameProperty()
    {
        return new GWTPropertyDescriptor("Name", "string");
    }

    /** Creates a sample type with a Name property followed by extraProperties */
    public static @NotNull ExpSampleType create(Container c, User user, String name, GWTPropertyDescriptor... extraProperties) throws ExperimentException
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(nameProperty());
        props.addAll(Arrays.asList(extraProperties));
        return create(c, user, name, props);
    }

    /** Creates a sample type with exactly these properties; include {@link #nameProperty()} unless naming by expression */
    public static @NotNull ExpSampleType create(Container c, User user, String name, List<GWTPropertyDescriptor> properties) throws ExperimentException
    {
        return SampleTypeService.get().createSampleType(c, user, name, null, properties, Collections.emptyList(), -1, -1, -1, -1, null);
    }

    public static @NotNull TableInfo getTable(Container c, User user, ExpSampleType st)
    {
        return QueryUpdateServiceTestUtil.getTable(user, c, SamplesSchema.SCHEMA_SAMPLES, st.getName());
    }

    public static List<Map<String, Object>> insert(Container c, User user, ExpSampleType st, List<Map<String, Object>> rows) throws Exception
    {
        return QueryUpdateServiceTestUtil.insert(getTable(c, user, st), user, c, rows);
    }

    public static List<Map<String, Object>> update(Container c, User user, ExpSampleType st, List<Map<String, Object>> rows) throws Exception
    {
        return QueryUpdateServiceTestUtil.update(getTable(c, user, st), user, c, rows);
    }
}
