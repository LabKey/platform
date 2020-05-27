/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.pipeline.PairSerializer;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.writer.MemoryVirtualFile;
import org.labkey.api.writer.VirtualFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;

@JsonSerialize(using = PairSerializer.class)
/** Simple wrapper around two other objects */
public class Pair<Type1, Type2> implements Map.Entry<Type1, Type2>, java.io.Serializable
{
    // For deserialization
    private Pair()
    {}

    public Pair(Type1 first, Type2 second)
    {
        this.first = first;
        this.second = second;
    }

    public Type1 first;
    public Type2 second;

    @Override
    public Type1 getKey()
    {
        return first;
    }

    public Type1 setKey(Type1 arg0)
    {
        Type1 v = first;
        first = arg0;
        return v;
    }

    @Override
    public Type2 getValue()
    {
        return second;
    }

    @Override
    public Type2 setValue(Type2 arg0)
    {
        Type2 v = second;
        second = arg0;
        return v;
    }

    public boolean equals(Object o)
    {
        if (! (o instanceof Map.Entry))
            return false;
        Map.Entry that = (Map.Entry) o;
        return (this.getKey() == null ? that.getKey() == null : this.getKey().equals(that.getKey()))
                && (this.getValue() == null ? that.getValue() == null : this.getValue().equals(that.getValue()));
    }

    public int hashCode()
    {
        return (getKey() == null ? 0 : getKey().hashCode()) ^ (getValue() == null ? 0 : getValue().hashCode());
    }

    public String toString()
    {
        return "(" + String.valueOf(first) + ", " + String.valueOf(second) + ")";
    }

    public Pair<Type1, Type2> copy()
    {
        return new Pair<>(first, second);
    }

    static public <T1, T2> Pair<T1, T2> of(T1 first, T2 second)
    {
        return new Pair<>(first, second);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testStringPairSerialize() throws IOException
        {
            Pair<String, Integer> pair = Pair.of("Val1", 99);
            ObjectMapper objectMapper = PipelineJob.createObjectMapper();
            try (ByteArrayOutputStream os = new ByteArrayOutputStream())
            {
                objectMapper.writeValue(os, pair);

                Pair<File, File> pair2 = objectMapper.readValue(new ByteArrayInputStream(os.toByteArray()), Pair.class);
                assertEquals("Pair not serialized correctly", pair.first, pair2.first);
                assertEquals("Pair not serialized correctly", pair.second, pair2.second);
            }
        }

        @Test
        public void testFilePairSerialize() throws IOException
        {
            File file1 = new File("file1.txt").getAbsoluteFile();
            File file2 = new File("file2.txt").getAbsoluteFile();

            Pair<File, File> pair = Pair.of(file1, file2);

            VirtualFile vf = new MemoryVirtualFile();

            ObjectMapper objectMapper = PipelineJob.createObjectMapper();
            try (ByteArrayOutputStream os = new ByteArrayOutputStream())
            {
                objectMapper.writeValue(os, pair);

                Pair<File, File> pair2 = objectMapper.readValue(new ByteArrayInputStream(os.toByteArray()), Pair.class);
                assertEquals("Pair not serialized correctly", pair.first, pair2.first);
                assertEquals("Pair not serialized correctly", pair.second, pair2.second);
            }
        }
    }
}
