package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.collections.iterators.ArrayIterator;

import java.util.concurrent.atomic.AtomicReference;
import java.util.Iterator;
import java.io.Serializable;
import java.io.IOException;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 20, 2009
 * Time: 1:03:45 PM
 *
 * This object is used for parsing and manipulating parsed paths (folders, file paths, etc)
 *
 * NOTE: this is object is NOT attached to a filesystem.  It does NOT know the difference between a file
 * and a directory.  The isDirectory() property ONLY indicates that a parsed path originally ended with "/"
 *
 * Path could probably be subclassed, but I think wrapping is probably the way to go for added functionality
 *
 * CONSIDER: support "root"
 * This might be useful for handling webapp context path, or windows drive letter, etc.
 */

public class Path implements Serializable, Iterable<String>
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

    final public static Path emptyPath = new Path(new String[0], 0, false, false);
    final public static Path rootPath = new Path(new String[0], 0, true, true);
    

    protected Path(String[] path, int length, boolean abs, boolean dir)
    {
        this._path = path;
        this._length = length;
        this._parent = new AtomicReference<Path>();
        int hash = 0;
        for (int i=0 ; i<length ; i++)
            hash = hash*37 + _path[i].hashCode();
        this._hash = hash;
        this._isAbsolute = abs;
        this._isDirectory = dir;
    }


    public Path(String ... names)
    {
        this(names, names.length, false, false);
    }


    // create a Path from an unencode string
    public static Path parse(String path)
    {
        String strip = StringUtils.strip(path,"/");
        if (strip.length() == 0)
            return path.startsWith("/") ? rootPath : emptyPath;
        String[] arr = strip.split("/");
        for (int i=0 ; i<arr.length ; i++)
            arr[i] = defaultDecodeName(arr[i]);
        return new Path(arr, arr.length, _abs(path), _dir(path));
    }

    
    // create a path from a url encode string
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
    

    public boolean isAbsolute()
    {
        return _isAbsolute;
    }


    public boolean isDirectory()
    {
        return _isDirectory;
    }


    public int compareTo(Path other)
    {
        int shorter = Math.min(this._length, other._length);
        for (int i=0 ; i<shorter ; i++)
        {
            int c = compareName(_path[i],other._path[i]);
            if (0 != c)
                return c;
        }
        return this._length - other._length;
    }


    public boolean endsWith(Path other)
    {
        if (other._length > this._length)
            return false;
        for (int i=1 ; i<=other._length ; i++)
        {
            int c = compareName(this._path[this._length-i], other._path[other._length-i]);
            if (0 != c)
                return false;
        }
        return true;
    }


    public boolean equals(Object other)
    {
        if (this == other)
            return true;
        if (this.getClass() != other.getClass())
            return false;
        Path that = (Path)other;
        if (this._length != that._length)
            return false;
        for (int i=this._length-1 ; i>=0 ; i--)
        {
            int c = compareName(this._path[i], that._path[i]);
            if (0 != c)
                return false;
        }
        return true;
    }


    public String getName()
    {
        return _path[_length-1];    
    }


    public int size()
    {
        return _length;
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


    // like java.nio.Path
    public int getNameCount()
    {
        return _length;
    }

    
    @NotNull
    public Path getParent()
    {
        Path p = _parent.get();
        if (null != p)
            return p;
            _parent.compareAndSet(null, createPath(_path, _length-1, isAbsolute(), true));
        return _parent.get();
    }
    

    public Path normalize()
    {
        for (String s : _path)
        {
            if (s.length() == 0 || ".".equals(s) || "..".equals(s))
                return _normalize();
        }
        return this;
    }

    
    private Path _normalize()
    {
        String[] normal = new String[_length];
        int next = 0;
        
        for (int i=0 ; i<_length ; i++)
        {
            String part = _path[i];
            if (part.length()==0 || ".".equals(part))
            {
            }
            else if ("..".equals(part))
            {
                if (next == 0)
                    return emptyPath;   // CONSIDER: throw exception?
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
        int shorter = Math.min(this._length, other._length);
        int i;
        for (i=0 ; i<shorter && 0==compareName(_path[i],other._path[i]); i++)
            ;

        // used up all of this._path
        if (i == this._length)
        {
            if (this._length == other._length)
                return emptyPath;
            String[] path = new String[other._length - this._length];
            System.arraycopy(other._path, this._length, path, 0, path.length);
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
        String[] path = new String[this._length + other._length];
        System.arraycopy(this._path, 0, path, 0, _length);
        System.arraycopy(other._path, 0, path, _length, other._length);
        Path ret = createPath(path, this._length + other._length, this.isAbsolute(), other.isDirectory());
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
    

    public boolean startsWith(Path other)
    {
        if (other._length > this._length)
            return false;
        for (int i=0 ; i<other._length ; i++)
        {
            int c = compareName(this._path[i], other._path[i]);
            if (0 != c)
                return false;
        }
        return true;
    }


    public Path subpath(int begin, int end)
    {
        if (begin > end || end > _length) throw new IllegalArgumentException();
        if (begin == end)
            return emptyPath;
        String[] path = new String[end-begin];
        System.arraycopy(_path, begin, path, 0, path.length);
        return createPath(path, path.length, isAbsolute() && begin==0, end<_length||isDirectory());
    }


    public String toString()
    {
        if (_length == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        String slash = isAbsolute() ? "/" : "";
        for (int i=0 ; i<_length ; i++)
        {
            sb.append(slash);
            sb.append(defaultEncodeName(_path[i]));
            slash = "/";
        }
        return sb.toString();
    }


    @Override
    public int hashCode()
    {
        return _hash;
    }


    public String encode()
    {
        if (_length == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i=0 ; i<_length ; i++)
        {
            sb.append("/");
            sb.append(PageFlowUtil.encode(_path[i]));
        }
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


    public Iterator<String> iterator()
    {
        return new ArrayIterator(_path);
    }
    

    private void readObject(java.io.ObjectInputStream in)
        throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        _parent = new AtomicReference<Path>();
    }


    private static boolean _abs(String path)
    {
        return path.startsWith("/");
    }


    private static boolean _dir(String path)
    {
        return path.endsWith("/") || path.endsWith("/.") || path.endsWith("/..");
    }


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void test() throws Exception
        {
            Path a = Path.parse("/a");
            assertTrue(a.isAbsolute());
            assertTrue(!a.isDirectory());

            Path b = Path.parse("b/");
            assertTrue(!b.isAbsolute());
            assertTrue(b.isDirectory());

            Path ab = a.resolve(b);
            assertTrue(ab.isAbsolute());
            assertTrue(ab.isDirectory());
            Path ab2 = Path.parse("a/b");
            assertTrue(!ab2.isAbsolute());
            assertTrue(!ab2.isDirectory());
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


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
