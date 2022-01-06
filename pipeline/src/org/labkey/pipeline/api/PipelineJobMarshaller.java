/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.pipeline.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.pipeline.NoSuchJobException;
import org.labkey.api.pipeline.ObjectKeySerialization;
import org.labkey.api.pipeline.PairSerializer;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.StringKeySerialization;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <code>PipelineJobMarshaller</code> handles saving a <code>PipelineJob</code> to XML,
 * and restoring it from XML.
 *
 * todo: probably want to have 2 different interfaces here, rather than implementing
 *          JobStore and throwing UnsupportedOperationException on most of its
 *          methods.
 */
public class PipelineJobMarshaller implements PipelineStatusFile.JobStore
{
    /* CONSIDER: create a separate interface? */
    @Override
    public void storeJob(PipelineJob job) throws NoSuchJobException
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }

    @Override
    public PipelineJob getJob(String jobId)
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }

    @Override
    public PipelineJob getJob(int rowId)
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }

    @Override
    public void retry(String jobId) throws IOException, NoSuchJobException
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }

    @Override
    public void retry(PipelineStatusFile sf) throws IOException, NoSuchJobException
    {
        throw new UnsupportedOperationException("Method supported only on web server");        
    }

    @Override
    public void split(PipelineJob job) throws IOException
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }

    @Override
    public void join(PipelineJob job) throws IOException, NoSuchJobException
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }

    @Override
    public String serializeToJSON(Object job)
    {
        return serializeToJSON(job, true);
    }

    @Override
    public String serializeToJSON(Object job, boolean ensureDeserialize)
    {
        ObjectMapper mapper = PipelineJob.createObjectMapper();

        try
        {
            String serialized = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(job);
            if (ensureDeserialize)          // Some callers round trip, so we don't need to here
            {
                try
                {
                    Object unserialized = deserializeFromJSON(serialized, job.getClass());
                    if (job instanceof PipelineJob)
                    {
                        List<String> errors = ((PipelineJob)job).compareJobs((PipelineJob)unserialized);
                        if (!errors.isEmpty())
                            LOG.error("Deserialized object differs from original: " + StringUtils.join(errors, ","));
                    }
                }
                catch (Exception e)
                {
                    LOG.error("Unserializing test failed: " + job.getClass(), e);
                }
            }
            return serialized;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }

    }

    @Override
    public Object deserializeFromJSON(String json, Class<?> cls)
    {
        ObjectMapper mapper = PipelineJob.createObjectMapper();

        try
        {
            return mapper.readValue(json, cls);
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    private static final Logger LOG = LogHelper.getLogger(PipelineJobMarshaller.class, "Serializes and deserializes pipeline jobs to JSON");

    public static class TestCase extends PipelineJob.TestSerialization
    {

        public static class Vial
        {
            private final Map<String, Object> _rowMap;
            private final Container _container;

            @JsonCreator
            public Vial(@JsonProperty("_container") Container container, @JsonProperty("_rowMap") Map<String, Object> rowMap)
            {
                _container = container;
                _rowMap = new CaseInsensitiveHashMap<>(rowMap);
            }

            public Object get(String key)
            {
                return _rowMap.get(key);
            }

            public Map getRowMap()
            {
                return _rowMap;
            }
        }

        public static class Inner
        {
            private Inner _inner;
            private String _address;
            private int _zip;

            public Inner()
            {

            }
            public Inner(String address, int zip)
            {
                _address = address;
                _zip = zip;
            }

            public String getAddress()
            {
                return _address;
            }

            public int getZip()
            {
                return _zip;
            }
        }

        public static class TestJob3
        {
            private List<List<Object>> objs;

            public TestJob3()
            {

            }

            public List<List<Object>> getObjs()
            {
                return objs;
            }

            public void setObjs(List<List<Object>> objs)
            {
                this.objs = objs;
            }
        }

        public static class TestJob
        {
            public File _file;
            public File _file1;
            public Vial _vial;
            public Path _path;
            public Path _nonAbsolutePath;
            public Path _s3Path;
            public FieldKey _fieldKey;
            public SchemaKey _schemaKey;
            private Inner _inner;
            private String _name;
            private Timestamp _timestamp;
            private Time _time;
            public GUID _guid;
            public Object _uri;
            private int _migrateFilesOption;
            @JsonSerialize(keyUsing = StringKeySerialization.Serializer.class)
            @JsonDeserialize(keyUsing = StringKeySerialization.URIDeserializer.class)
            private Map<URI, Object> _map;

            @JsonSerialize(keyUsing = ObjectKeySerialization.Serializer.class)
            @JsonDeserialize(keyUsing = ObjectKeySerialization.Deserializer.class)
            private Map<PropertyDescriptor, Object> _propMap;
            private List<Inner> _list;
            private Object _obj;
            @JsonSerialize(using = PairSerializer.class)
            private Pair<Inner, Inner> _innerPair;

            public TestJob()
            {

            }
            public TestJob(String name, int option)
            {
                _file = new File(URI.create("file:///Users/daveb"));
                _file1 = new File("/Users/daveb");
                Map<String, Integer> wrapMap = new CaseInsensitiveHashMap<>();
                ArrayListMap.FindMap<String> findMap = new ArrayListMap.FindMap<>(wrapMap);
                HashMap<String, Object> mapOfVial = new HashMap<>(findMap);
                mapOfVial.put("globaluniqueId", "ABB");
                mapOfVial.put("rowid", 32);
                _vial = new Vial(ContainerManager.getSharedContainer(), mapOfVial);

                _path = new File("/Users/johnbrown/glory").toPath();
                _nonAbsolutePath = new File("glory/hallelujah").toPath();
                _fieldKey = FieldKey.fromParts("list", "vehicles", "refy");
                _schemaKey = SchemaKey.fromParts("mySchema", "subSchema");
                _name = name;
                _migrateFilesOption = option;
                _map = new HashMap<>();
                _uri = URI.create("https://labkey.com");
                _map.put(URI.create("http://google.com"), "fooey");
                _map.put(URI.create("file:///Users/daveb"), 324);
                _map.put(URI.create("http://ftp.census.gov"), new Inner("329 Wiltshire Blvd", 90210));
                _inner = new Inner("3234 Albert Ave", 98101);
                _list = new ArrayList<>();
                _list.add(new Inner("31 Thunder Ave", 64102));
                _list.add(new Inner("34 Boston St", 71101));
                _obj = new Inner("17 Boylston St", 10014);

                _propMap = new HashMap<>();
                _propMap.put(new PropertyDescriptor(null, PropertyType.BIGINT, "foobar", ContainerManager.getRoot()), "foo");
                _propMap.put(new PropertyDescriptor(null, PropertyType.STRING, "stringy", ContainerManager.getRoot()), "str"); 
//                _innerPair = new Pair<>(new Inner("31 Thunder Ave", 64102), new Inner("34 Boston St", 71101));
                _timestamp = new Timestamp(1400938833L);
                _time = new Time(1400938843L);
                _guid = new GUID();

            }

            public String getName()
            {
                return _name;
            }

            public Pair<Inner, Inner> getInnerPair()
            {
                return _innerPair;
            }

            public void setInnerPair(Pair<Inner, Inner> innerPair)
            {
                _innerPair = innerPair;
            }

            public Timestamp getTimestamp()
            {
                return _timestamp;
            }

            public void setTimestamp(Timestamp timestamp)
            {
                _timestamp = timestamp;
            }

            public Time getTime()
            {
                return _time;
            }

            public void setTime(Time time)
            {
                _time = time;
            }
        }

        @Test
        public void testSerialize()
        {
            try
            {
                Object job = new TestJob("Johnny", 5);
                testSerialize(job, LOG);
            }
            catch (Exception e)
            {
                LOG.error("Class not found", e);
            }
        }


    }
}
