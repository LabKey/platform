/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.pipeline;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.util.FileUtil;

import java.io.IOException;
import java.nio.file.Path;

public class PathSerialization
{
    public static class Serializer extends StdSerializer<Path>
    {
        public Serializer()
        {
            this(null);
        }

        public Serializer(Class<Path> typ)
        {
            super(typ);
        }

        @Override
        public void serialize(Path path, JsonGenerator gen, SerializerProvider provider) throws IOException
        {
            String str = path.toString();
            gen.writeString(str);
        }

        @Override
        public void serializeWithType(Path path, JsonGenerator gen, SerializerProvider provider, TypeSerializer typeSer) throws IOException
        {
            WritableTypeId typeIdDef = typeSer.writeTypePrefix(gen, typeSer.typeId(path, Path.class, JsonToken.VALUE_STRING));    // Use Path.class as typeid, so deserialization comes here
            serialize(path, gen, provider);
            typeSer.writeTypeSuffix(gen, typeIdDef);
        }
    }

    public static class Deserializer extends StdDeserializer<Path>
    {
        protected Deserializer()
        {
            super(Path.class);
        }

        @Override
        public @Nullable Path deserialize(JsonParser parser, DeserializationContext context) throws IOException
        {
            String str = parser.getValueAsString();
            if (FileUtil.hasCloudScheme(str))
            {
                // TODO: problem is that we need a container to map a URL string to an S3 path, because we have a prefix derived form the container (#35865)
                // TODO: one possibility is to tease out what config/container matches the bucket/prefix we find, but there could be more than 1 match
                CloudStoreService css = CloudStoreService.get();
                if (css != null)
                {
                    //TODO this will likely work only for cloud paths that include access ids eg: s3://<pub-access-key>@s3.amazonaws.com/<my bucket>/...
                    return css.getPathFromUrl(str);
                    //TODO need LKS container to pull the appropriate S3 credentials/FileSystem representation (in the event of interrupted job)
                }

            }
            return Path.of(str);
        }
    }
}

