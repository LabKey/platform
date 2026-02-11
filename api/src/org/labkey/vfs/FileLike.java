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
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.UnauthorizedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.List;
import java.util.function.Predicate;

/// A file or directory within a [FileSystemLike]. All paths are relative to the file system root,
/// ensuring that access stays within the scoped boundary.
///
/// ```java
/// FileLike root = new FileSystemLike.Builder(dir).readwrite().root();
/// FileLike child = root.resolveChild("data.tsv");
///
/// // Read/write via streams
/// try (OutputStream out = child.openOutputStream()) { ... }
/// try (InputStream in = child.openInputStream()) { ... }
///
/// // Navigate to a nested path
/// FileLike nested = root.resolveFile(Path.parse("subdir/file.txt"));
///
/// // Convert to java.nio.file.Path when needed (local file systems only)
/// java.nio.file.Path nioPath = child.toNioPathForRead();
/// ```
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

    default boolean renameTo(FileLike target)
    {
        return FileUtil.renameTo(this, target);
    }

    default boolean isDescendant(URI uri)
    {
        return getFileSystem().isDescendant(this, uri);
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

    default FileLike resolveChild(Path.Part name)
    {
        return resolveChild(name.toString());
    }

    default FileLike resolveChild(String name)
    {
        if (".".equals(name) || "..".equals(name))
            throw new InvalidPathException(name, "Cannot resolve child");
        Path path = Path.parse(name);
        if (1 != path.size())
            throw new InvalidPathException(name, "Cannot resolve child");
        return resolveFile(path);
    }

    FileLike getParent();

    @NotNull
    List<FileLike> getChildren();

    @NotNull
    default List<FileLike> getChildren(Predicate<FileLike> filter)
    {
        return getChildren().stream().filter(filter).toList();
    }

    /**
     * Does not create parent directories
     */
    void mkdir() throws IOException;

    void mkdirs() throws IOException;

    /**
     * Does not create parent directories
     */
    void createFile() throws IOException;

    boolean delete() throws IOException;

    void refresh();

    boolean exists();

    boolean isDirectory();

    boolean isFile();

    long getSize();

    long getCreated();

    long getLastModified();

    default OutputStream openOutputStream() throws IOException
    {
        return openOutputStream(false);
    }

    OutputStream openOutputStream(boolean append) throws IOException;

    InputStream openInputStream() throws IOException;

    default void move(FileLike dest) throws IOException
    {
        if (!renameTo(dest))
        {
            Files.move(this.toNioPathForRead(), dest.toNioPathForWrite());
        }
    }

    class FileLikeSerializer extends StdSerializer<AbstractFileLike>
    {
        public FileLikeSerializer()
        {
            this(null);
        }

        public FileLikeSerializer(Class<AbstractFileLike> t)
        {
            super(t);
        }

        @Override
        public void serialize(AbstractFileLike value, JsonGenerator gen, SerializerProvider provider) throws IOException
        {
            gen.writeStartObject();
            value._serialize(gen);
            gen.writeEndObject();
        }

        @Override
        public void serializeWithType(AbstractFileLike value, JsonGenerator gen, SerializerProvider provider, TypeSerializer typeSer) throws IOException
        {
            WritableTypeId typeIdDef = typeSer.writeTypePrefix(gen, typeSer.typeId(value, JsonToken.START_OBJECT));
            value._serialize(gen);
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
            // for cloud config
            if (node.has("containerId"))
                b.container(node.get("containerId").asText());
            if (node.has("configName"))
                b.config(node.get("configName").asText());
            return b.build(ctx).resolveFile(Path.parse(path));
        }
    }
}
