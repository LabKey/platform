package org.labkey.vfs;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.Path;
import org.labkey.api.view.UnauthorizedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;

@JsonSerialize(using = FileLike.FileLikeSerializer.class)
@JsonDeserialize(using = FileLike.FileLikeDeserializer.class)
public interface FileLike extends Comparable<FileLike>
{
    FileSystemLike getFileSystem();

    /*
     * This is the path within the containing FileSystemLike object.  Will always be absolute, meaning
     * getPath().toString() will always start with '/'.  Depending on how the path was created, it may
     * end with '/'. (As always be careful when resolving paths that start with ".".)
     */
    Path getPath();

    default String getName()
    {
        return getPath().getName();
    }

    default URI toURI()
    {
        return getFileSystem().getURI(this);
    }

    default java.nio.file.Path toNioPathForRead()
    {
        if (!getFileSystem().canReadFiles())
            throw new UnauthorizedException();
        return getFileSystem().getNioPath(this);
    }

    default java.nio.file.Path toNioPathForWrite()
    {
        if (!getFileSystem().canWriteFiles())
            throw new UnauthorizedException();
        return getFileSystem().getNioPath(this);
    }

    /* We use util.Path here to avoid ambiguity of String (encoded vs not encoded, path vs name, etc). */
    FileLike resolveFile(org.labkey.api.util.Path path);

    default FileLike resolveChild(String name)
    {
        if (".".equals(name) || "..".equals(name))
            throw new IllegalArgumentException("Cannot resolve child '" + name + "'");
        Path path = Path.parse(name);
        if (1 != path.size())
            throw new IllegalArgumentException("Cannot resolve child '" + name + "'");
        return resolveFile(path);
    }

    FileLike getParent();

    @NotNull
    List<FileLike> getChildren();

    /**
     * Does not create parent directories
     */
    void mkdir() throws IOException;

    void mkdirs() throws IOException;

    /**
     * Does not create parent directories
     */
    void createFile() throws IOException;

    void delete() throws IOException;

    void refresh();

    boolean exists();

    boolean isDirectory();

    boolean isFile();

    long getSize();

    OutputStream openOutputStream() throws IOException;

    InputStream openInputStream() throws IOException;


    class FileLikeSerializer extends StdSerializer<FileLike>
    {
        public FileLikeSerializer()
        {
            this(null);
        }

        public FileLikeSerializer(Class<FileLike> t)
        {
            super(t);
        }

        public void _serialize(FileLike value, JsonGenerator gen, SerializerProvider provider) throws IOException
        {
            FileSystemLike fs = value.getFileSystem();
            gen.writeStringField("rootUri", fs.getURI().toString());
            gen.writeBooleanField("canReadFiles", fs.canReadFiles());
            gen.writeBooleanField("canWriteFiles", fs.canWriteFiles());
            gen.writeStringField("path", value.getPath().toString());
            if (fs instanceof FileSystemVFS)
                gen.writeBooleanField("vfs", true);
        }

        @Override
        public void serialize(FileLike value, JsonGenerator gen, SerializerProvider provider) throws IOException
        {
            gen.writeStartObject();
            _serialize(value, gen, provider);
            gen.writeEndObject();
        }

        @Override
        public void serializeWithType(FileLike value, JsonGenerator gen, SerializerProvider provider, TypeSerializer typeSer) throws IOException
        {
            WritableTypeId typeIdDef = typeSer.writeTypePrefix(gen, typeSer.typeId(value, JsonToken.START_OBJECT));
            _serialize(value, gen, provider);
            typeSer.writeTypeSuffix(gen, typeIdDef);
        }
    }


    class FileLikeDeserializer extends StdDeserializer<FileLike>
    {
        public FileLikeDeserializer()
        {
            this(null);
        }

        public FileLikeDeserializer(Class<?> vc)
        {
            super(vc);
        }

        @Override
        public FileLike deserialize(JsonParser jp, DeserializationContext ctx) throws IOException
        {
            JsonNode node = jp.getCodec().readTree(jp);
            String rootUri = node.get("rootUri").asText();
            boolean canReadFiles = node.get("canReadFiles").asBoolean();
            boolean canWriteFiles = node.get("canWriteFiles").asBoolean();
            String path = node.get("path").asText();
            boolean vfs = null != node.get("vfs") && node.get("vfs").asBoolean();

            var b = new FileSystemLike.Builder(URI.create(rootUri));
            if (canWriteFiles)
                b.readwrite();
            else if (canReadFiles)
                b.readonly();
            if (vfs)
                b.vfs();
            return b.build().resolveFile(Path.parse(path));
        }
    }
}
