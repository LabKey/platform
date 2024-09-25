/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.collections4.iterators.ArrayIterator;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * This object is used for parsing and manipulating parsed paths (folders, file paths, etc.)
 * NOTE: this is object is NOT attached to a filesystem. It does NOT know the difference between a file
 * and a directory. The isDirectory() property ONLY indicates that a parsed path originally ended with "/".
 * Path could probably be subclassed, but I think wrapping is probably the way to go for added functionality.
 * CONSIDER: support "root"
 * This might be useful for handling webapp context path, or windows drive letter, etc.
 */

public class Path implements Serializable, Comparable<Path>, Iterable<String>
{
    final private int _hash;
    final private String[] _path;
    final private int _length;

    // original string ends with / or /. or /..
    // NOT used for compareTo, equals(), etc
    // it's just convenient to carry along
    final boolean _isDirectory;

    // original string started with /
    // UNDONE: not sure how this should affect compareTo() and equals()
    final boolean _isAbsolute;

    transient private AtomicReference<Path> _parent;

    final public static Path emptyPath = new Path(new String[0], 0, false, true);
    final public static Path rootPath = new Path(new String[0], 0, true, true);


    @JsonCreator
    private Path(@JsonProperty("_hash") int hash,
                 @JsonProperty("_path") String[] path,
                 @JsonProperty("_length") int length,
                 @JsonProperty("_isAbsolute") boolean abs,
                 @JsonProperty("_isDirectory") boolean dir)
    {
        _hash = hash;
        _path = path;
        _length = length;
        _isAbsolute = abs;
        _isDirectory = dir;
        _parent = new AtomicReference<>();
    }

    protected Path(String[] path, int length, boolean abs, boolean dir)
    {
        _path = path;
        _length = length;
        _parent = new AtomicReference<>();
        _isAbsolute = abs;
        _isDirectory = dir;
        _hash = computeHash(_path, _length);
    }

    // Create an instance from a java.nio.file.Path
    public Path(java.nio.file.Path nioPath)
    {
        this(getNames(nioPath));
    }

    protected Path(Path anotherPath)
    {
        _hash = anotherPath._hash;
        _path = anotherPath._path;
        _length = anotherPath._length;
        _isAbsolute = anotherPath._isAbsolute;
        _isDirectory = anotherPath._isAbsolute;
        _parent = anotherPath._parent;
    }

    private static Collection<String> getNames(Iterable<java.nio.file.Path> it)
    {
        return StreamSupport.stream(it.spliterator(), false)
            .map(java.nio.file.Path::toString)
            .collect(Collectors.toList());
    }


    public Path(Collection<String> names)
    {
        this(names.toArray(new String[0]));
    }


    public Path()
    {
        this(new String[0], 0, false, false);
    }

    public Path(String ... names)
    {
        this(names, names.length, false, false);
    }

    public Path(Path.Part ... names)
    {
        _path = new String[names.length];
        _length = names.length;
        _parent = new AtomicReference<>();
        int hash = 0;
        for (int i = 0; i < _length; i++)
            _path[i] = names[i].toString();
        _isAbsolute = false;
        _isDirectory = false;
        _hash = computeHash(_path, _length);
    }

    private static int computeHash(String[] names, int length)
    {
        int hash = 0;
        for (int i=0 ; i < length ; i++)
            hash = hash * 37 + names[i].toLowerCase().hashCode();
        return hash;
    }

    /** create a Path from an unencoded string */
    public static Path parse(@NotNull String path)
    {
        String strip = StringUtils.strip(path,"/");
        if (strip.isBlank())
            return path.startsWith("/") ? rootPath : emptyPath;
        String[] arr = strip.split("/");
        for (int i=0 ; i<arr.length ; i++)
            arr[i] = defaultDecodeName(arr[i]);
        return new Path(arr, arr.length, _abs(path), _dir(path));
    }


    /** create a path from a url encoded string */
    public static Path decode(String path)
    {
        String[] arr = StringUtils.strip(path,"/").split("/");
        for (int i=0 ; i<arr.length ; i++)
            arr[i] = PageFlowUtil.decode(arr[i]);
        return new Path(arr, arr.length, _abs(path), _dir(path));
    }

    protected Path createPath(String[] path, int length, boolean abs, boolean dir)
    {
        return new Path(path,length,abs,dir);
    }

    public Path absolute()
    {
        if (isAbsolute())
            return this;
        return new Path(_path, _length, true, _isDirectory);
    }

    public boolean isAbsolute()
    {
        return _isAbsolute;
    }

    public boolean isDirectory()
    {
        return _isDirectory;
    }

    @Override
    public int compareTo(@NotNull Path other)
    {
        if (this == other)
            return 0;
        int shorter = Math.min(_length, other._length);
        for (int i=0 ; i<shorter ; i++)
        {
            int c = compareName(_path[i],other._path[i]);
            if (0 != c)
                return c;
        }
        return _length - other._length;
    }

    public boolean endsWith(Path other)
    {
        if (other._length > _length)
            return false;
        for (int i=1 ; i<=other._length ; i++)
        {
            int c = compareName(_path[_length-i], other._path[other._length-i]);
            if (0 != c)
                return false;
        }
        return true;
    }

    public boolean equals(Object other)
    {
        if (null == other)
            return false;
        if (this == other)
            return true;
        if (this.getClass() != other.getClass())
            return false;
        Path that = (Path)other;
        if (_hash != that._hash || _length != that._length)
            return false;
        for (int i=_length-1 ; i>=0 ; i--)
        {
            int c = compareName(_path[i], that._path[i]);
            if (0 != c)
                return false;
        }
        return true;
    }

    public boolean contains(String name)
    {
        for (int i=0 ; i<_length ; i++)
            if (_path[i].equals(name))
                return true;
        return false;
    }

    public boolean contains(Path.Part name)
    {
        return contains(name.toString());
    }

    public String getName()
    {
        return _length == 0 ? "" : _path[_length-1];
    }

    public int size()
    {
        return _length;
    }

    public boolean isEmpty()
    {
        return 0 == _length;
    }

    public String get(int i)
    {
        return _path[i];
    }

    // like java.nio.Path
    public String getName(int i)
    {
        return _path[i];
    }

    // Return a part of a parsed path. This class is a marker that shows this name came from a parsed Path.
    // This does not guarantee that it is a valid name to pass to java.util.File (e.g. could contain / \ :).
    //
    // Paths are for a lot of purposes, so they can not be very restrictive.
    public Path.Part getPart(int i)
    {
        return getPart(i, false);
    }

    public Path.Part getPart(int i, boolean allowRelative)
    {
        if (!allowRelative && (_path[i].equals(".") || _path[i].equals("..")))
            throw new IllegalArgumentException("invalid path part: " + _path[i]);
        return new Path.Part(_path[i]);
    }

    // caller is responsible for calling normalize and other validation to avoid an exception when using this method
    @JsonIgnore
    public Path.Part getNamePart() // throws ArrayIndexOutOfBoundsException, IllegalArgumentException
    {
        return new Path.Part(_path[_length-1]);
    }

    // like java.nio.Path
    public int getNameCount()
    {
        return _length;
    }


    public Path getParent()
    {
        if (_length == 0)
            return null;
        Path p = _parent.get();
        if (null != p)
            return p;
        _parent.compareAndSet(null, createPath(_path, _length-1, isAbsolute(), true));
        return _parent.get();
    }

    public Path normalize()
    {
        for (int i = 0; i < _length; i++)
        {
            String s = _path[i];
            if (s.isEmpty() || ".".equals(s) || "..".equals(s))
                return _normalize();
        }
        return this;
    }

    /**
     * CONSIDER: a version that allows a return path that starts with ../
     *
     * @return null indicates illegal path,
     */
    private Path _normalize()
    {
        String[] normal = new String[_length];
        int next = 0;

        for (int i=0 ; i<_length ; i++)
        {
            String part = _path[i];
            if (part.length()==0 || ".".equals(part))
            {
                /* pass */;
            }
            else if ("..".equals(part))
            {
                if (next == 0)
                    return null;
                next--;
            }
            else
            {
                normal[next++] = part;
            }
        }
        return createPath(normal, next, isAbsolute(), isDirectory());
    }

    /**
     * Use only where 'this' is a directory
     */
    public Path relativize(Path other)
    {
        if (size()==0)
            return other;
        int shorter = Math.min(_length, other._length);
        int i;
        for (i=0 ; i<shorter && 0==compareName(_path[i],other._path[i]); i++)
        {
            /* pass */;
        }

        // used up all of _path
        if (i == _length)
        {
            if (_length == other._length)
                return emptyPath;
            String[] path = new String[other._length - _length];
            System.arraycopy(other._path, _length, path, 0, path.length);
            return createPath(path, path.length, false, other.isDirectory());
        }

        String[] path = new String[_length-i + other._length-i];
        for (int j=0 ; j<_length-i ; j++)
            path[j] = "..";
        System.arraycopy(other._path, i, path, _length-i, other._length-i);
        return createPath(path, path.length, false, other.isDirectory());
    }

    /*
     * If other.isAbsolute() then return other.  Otherwise, return a new path by appending other to this path.
     *
     * NOTE: This method is not directory/file aware.  hence
     *
     * path.resolve(".") is always the same as path and never path.getParent()
     *      "root/dir/file.txt".resolve(".") is not "dir"
     * path.resolve("..") is always path.getParent()
     *      "root/dir/file.txt".resolve("..") is "root/dir"
     *
     * So only use resolve on objects you know represent directories/collections
     */
    public Path resolve(Path other)
    {
        if (other.isAbsolute())
            return other;
        return append(other);
    }

    /** same as resolve() but ignores isAbsolute() attribute */
    public Path append(Path other)
    {
        if (other._length == 0)
            return this;
        String[] path = new String[_length + other._length];
        System.arraycopy(_path, 0, path, 0, _length);
        System.arraycopy(other._path, 0, path, _length, other._length);
        Path ret = createPath(path, _length + other._length, this.isAbsolute(), other.isDirectory());
        if (other._length == 1)
            ret._parent.set(this);
        return ret;
    }

    /** NOTE: do not pass in an unparsed path! use .append(Path.parse(path)) to append a path string */
    public Path append(String... names)
    {
        String[] path = new String[_length+names.length];
        System.arraycopy(_path, 0, path, 0, _length);
        System.arraycopy(names, 0, path, _length, names.length);
        Path ret = createPath(path, path.length, isAbsolute(), false);
        if (names.length == 1)
            ret._parent.set(this);
        return ret;
    }

    public Path append(Path.Part... names)
    {
        String[] path = new String[_length+names.length];
        System.arraycopy(_path, 0, path, 0, _length);
        for (int i=0 ; i<names.length ; i++)
            path[_length+i] = names[i].toString();
        Path ret = createPath(path, path.length, isAbsolute(), false);
        if (names.length == 1)
            ret._parent.set(this);
        return ret;
    }

    public Path append(Path.Part name, boolean isDirectory)
    {
        String[] path = new String[_length+1];
        System.arraycopy(_path, 0, path, 0, _length);
        path[_length] = name.toString();
        Path ret = createPath(path, path.length, isAbsolute(), isDirectory);
        ret._parent.set(this);
        return ret;
    }

    public Path append(String name, boolean isDirectory)
    {
        String[] path = new String[_length+1];
        System.arraycopy(_path, 0, path, 0, _length);
        path[_length] = name;
        Path ret = createPath(path, path.length, isAbsolute(), isDirectory);
        ret._parent.set(this);
        return ret;
    }

    public boolean startsWith(Path other)
    {
        if (other._length > _length)
            return false;
        for (int i=0 ; i<other._length ; i++)
        {
            int c = compareName(_path[i], other._path[i]);
            if (0 != c)
                return false;
        }
        return true;
    }

    public Path subpath(int begin, int end)
    {
        if (begin > end || end > _length)
            throw new IllegalArgumentException();
        if (begin == end)
            return emptyPath;
        String[] path = new String[end-begin];
        System.arraycopy(_path, begin, path, 0, path.length);
        return createPath(path, path.length, isAbsolute() && begin==0, end<_length||isDirectory());
    }

    public String extension()
    {
        String name = getName();
        if (name.lastIndexOf('.') != -1)
        {
            return name.substring(name.lastIndexOf('.') + 1);
        }
        return null;
    }

    public String[] toArray()
    {
        var ret = new String[_length];
        System.arraycopy(_path, 0, ret, 0, _length);
        return ret;
    }

    public String toString()
    {
        return toString(null, null);
    }

    public String toString(String start, String end)
    {
        if (start == null)
            start = isAbsolute() ? "/" : "";
        if (_length == 0)
            return start;
        if (end == null)
            end = isDirectory() ? "/" : "";

        StringBuilder sb = new StringBuilder();
        String slash = start;
        for (int i=0 ; i<_length ; i++)
        {
            sb.append(slash);
            sb.append(defaultEncodeName(_path[i]));
            slash = "/";
        }
        sb.append(end);
        return sb.toString();
    }

    @Override
    public int hashCode()
    {
        return _hash;
    }

    public String encode()
    {
        return encode(null,null);
    }

    public String encode(String start, String end)
    {
        if (start == null)
            start = isAbsolute() ? "/" : "";
        if (_length == 0)
            return start;
        if (end == null)
            end = isDirectory() ? "/" : "";

        StringBuilder sb = new StringBuilder();
        String slash = start;
        for (int i=0 ; i<_length ; i++)
        {
            sb.append(slash);
            sb.append(PageFlowUtil.encode(_path[i]));
            slash = "/";
        }
        sb.append(end);
        return sb.toString();
    }

    protected int compareName(String a, String b)
    {
        return a.compareToIgnoreCase(b);
    }

    protected static String defaultDecodeName(String a)
    {
        return a;
    }

    protected static String defaultEncodeName(String a)
    {
        return a;
    }

    @Override
    @NotNull
    public Iterator<String> iterator()
    {
        return new ArrayIterator<>(_path, 0, _length);
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        _parent = new AtomicReference<>();
    }

    private static boolean _abs(String path)
    {
        return path.startsWith("/");
    }

    private static boolean _dir(String path)
    {
        return path.endsWith("/") || path.endsWith("/.") || path.endsWith("/..");
    }

    /**
     * NOTE: Path.Part is just a wrapper for String.  It is not case-sensitive or non-case-sensitive like Path (see Davpath).
     */
    public static Path.Part toPathPart(@NotNull String name)
    {
        return new Path.Part(name);
    }

    /**
     * Path.Part is just a wrapper for String.  It only indicates that this String came from a parsed Path, and
     * that the value is intended to represent a single name in a path.  Because Path is used for so many things,
     * this can not be relied on to promise any other validation.  In particular a Path.Part may include '/'.
     * A method using a Path.Part is responsible for validating in its own context.
     * <br>
     * See FileUtil.appendName(Path.Part) and Resource.find(Path.Part) for example of where this class is
     * used to indicate that the parameter is intended to be "filename-like" and not "path-like".
     */
    public static class Part implements Serializable, CharSequence
    {
        final private @NotNull String _name;

        private Part(@NotNull String name)
        {
            _name = name;
        }

        @Override
        public int length()
        {
            return _name.length();
        }

        @Override
        public char charAt(int index)
        {
            return _name.charAt(index);
        }

        @NotNull
        @Override
        public CharSequence subSequence(int start, int end)
        {
            return _name.subSequence(start, end);
        }

        public String toString()
        {
            return _name;
        }

        @Override
        public int hashCode()
        {
            return _name.hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            // be consistent with Path.equals()
            return obj instanceof Part otherPart && this._name.equals(otherPart._name);
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            Path a = Path.parse("/a");
            assertTrue(a.isAbsolute());
            assertFalse(a.isDirectory());

            Path b = Path.parse("b/");
            assertFalse(b.isAbsolute());
            assertTrue(b.isDirectory());

            Path ab = a.resolve(b);
            assertTrue(ab.isAbsolute());
            assertTrue(ab.isDirectory());
            Path ab2 = Path.parse("a/b");
            assertFalse(ab2.isAbsolute());
            assertFalse(ab2.isDirectory());
            assertEquals(ab, ab2);

            assertEquals(a.append("b"), ab);
            assertEquals(a.append("b","c","..").normalize(), ab);
            assertEquals(a.append("b","..","b").normalize(), ab);

            assertTrue(ab.startsWith(a));
            assertTrue(ab.endsWith(b));

            Path messy = Path.parse("/a/./b/../c/d/e/../../././f");
            Path normal = Path.parse("/a/c/f");
            assertEquals(messy.normalize(), normal);

            Path r;
            Path base = Path.parse("/a/b/c");
            r = base.relativize(Path.parse("/a/b/c/d"));
            assertEquals(r, new Path("d"));
            r = base.relativize(new Path("a","b","c"));
            assertEquals(r, Path.emptyPath);
            r = base.relativize(new Path("a","b"));
            assertEquals(r, new Path(".."));
            r = base.relativize(new Path("a","b","x"));
            assertEquals(r, new Path("..","x"));
            r = base.relativize(new Path("y"));
            assertEquals(r, new Path("..","..","..","y"));
        }

        @Test
        public void test21949()
        {
            Path messy = Path.parse("/a/./b/..//c/d/e/../../././f");
            Path normalized = messy.normalize();
            Path clean = Path.parse("/a/c/f");
            assertEquals(messy.normalize(), clean);

            Iterator<String> n = normalized.iterator();
            assertTrue(n.hasNext());
            assertEquals("a",n.next());
            assertTrue(n.hasNext());
            assertEquals("c",n.next());
            assertTrue(n.hasNext());
            assertEquals("f",n.next());
            assertFalse(n.hasNext());
        }
    }
}
