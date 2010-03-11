/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.search.umls;

import org.labkey.api.arrays.IntegerArray;
import org.labkey.api.util.Filter;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.util.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Mar 3, 2010
 * Time: 4:59:46 PM
 */
public class RRFLoader
{
    public static class Definition // MRDEF
    {
        final String _type="DEF";
        String CUI;
        String AUI;
        String ATUI;
        String SATUI;
        String SAB;
        String DEF;
        String SUPPRESS; // O,E,Y,N
        String CVF;

        Definition(){}
        
        public Definition(String[] args)
        {
            try
            {
                int i=0;
                CUI=args[i++];
                AUI=args[i++];
                ATUI=args[i++];
                SATUI=args[i++];
                SAB=args[i++];
                DEF=args[i++];
                if (i==args.length) return;
                SUPPRESS=args[i++];
                if (i==args.length) return;
                CVF=args[i++];
            }
            catch (ArrayIndexOutOfBoundsException x)
            {
            }
        }

        @Override
        public String toString()
        {
            return _type + ": " + CUI + " " + DEF;
        }
    }

    public static class ConceptName  // MRCONSO
    {
        final String _type="CONSO";
        String CUI;
        String LAT;
        String TS;
        String LUI;
        String STT;
        String SUI;
        String ISPREF;  // Y,N
        String AUI;
        String SCUI;
        String SDUI;
        String SAB;
        String TTY;
        String CODE;
        String STR;
        String SRL;
        String SUPPRESS;
        String CVF;

        ConceptName(){}

        public ConceptName(String[] args)
        {
            try
            {
                int i=0;
                CUI=args[i++];
                LAT=args[i++];
                TS=args[i++];
                LUI=args[i++];
                STT=args[i++];
                SUI=args[i++];
                ISPREF=args[i++];
                AUI=args[i++];
                SCUI=args[i++];
                SDUI=args[i++];
                i++; //TODO what is this field?  RSAB/VSAB instead of SAB??
                SAB=args[i++];
                TTY=args[i++];
                CODE=args[i++];
                STR=args[i++];
                if (i==args.length) return;
                SRL=args[i++];
                if (i==args.length) return;
                SUPPRESS=args[i++];
                if (i==args.length) return;
                CVF=args[i++];
            }
            catch (ArrayIndexOutOfBoundsException x)
            {
            }
        }

        @Override
        public String toString()
        {
            return _type + ": " + CUI + " " + STR;
        }
    }

    public static class SemanticType // MRSTY
    {
        final String _type="STY"; 
        String CUI;
        String TUI;
        String STN;
        String STY;
        String ATUI;
        String CVF;

        SemanticType()
        {
        }

        public SemanticType(String[] args)
        {
            try
            {
                int i=0;
                CUI=args[i++];
                TUI=args[i++];
                STN=args[i++];
                if (i==args.length) return;
                STY=args[i++];
                if (i==args.length) return;
                ATUI=args[i++];
                if (i==args.length) return;
                CVF=args[i++];
            }
            catch (ArrayIndexOutOfBoundsException x)
            {
            }
        }

        @Override
        public String toString()
        {
            return _type + ": " + CUI + " " + STN + " " + STY;
        }
    }


    File metaDirectory;
    File[] mrconso;
    File mrdef;
    File mrsty;


    public RRFLoader(File meta) throws IOException
    {
        if (!meta.exists())
            throw new FileNotFoundException(meta.getPath());
        if (!meta.isDirectory())
            throw new IllegalArgumentException(meta.getPath());

        metaDirectory = meta;
        mrdef = new File(meta,"MRDEF.RRF");
        mrsty = new File(meta,"MRSTY.RRF");
        mrconso = new File[]{
                new File(meta,"MRCONSO.RRF.aa"), new File(meta,"MRCONSO.RRF.ab")
        };

        
    }

    // there are 1.6m concepts (C0000005 to C2112121)
    private class Index
    {
        int last = Integer.MIN_VALUE;
        int size;
        IntegerArray keysA;
        IntegerArray valuesA;
        int[] keys;
        int[] values;

        void add(int key, int value)
        {
            if (key <= last)
                throw new IllegalStateException();
            last = key;
            keysA.add(key);
            valuesA.add(value);
        }
        void done()
        {
            keys = keysA.toArray(null);
            keysA = null;
            values = valuesA.toArray(null);
            valuesA = null;
            last = -1;
            size = keys.length;
        }
        int getValue(int key)
        {
            int i=last+1;
            if (!(i>= 0 && i < size && keys[i] == key))
                i = Arrays.binarySearch(keys, key);
            if (i < 0)
                return i;
            last = i;
            return values[i];
        }
    }

    static String[] stringArray=new String[0];

    private static class RRFReader<T> implements Iterator, Closeable
    {
        final Class _class;
        final Constructor<T> _constructor;
        final Filter<T> _filter;
        final BufferedReader _in;
        final long _size;
        T _next = null;

        RRFReader(Class c, File f, Filter<T> filter)
        {
            try
            {
                _class = c;
                _constructor = c.getConstructor(stringArray.getClass());
                _size = f.length();
                _in = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
                _filter = filter;
                _next = readNext();
            }
            catch (IOException x)
            {
                throw new RuntimeException(x);
            }
            catch (NoSuchMethodException x)
            {
                throw new RuntimeException(x);
            }
        }

        public boolean hasNext()
        {
            boolean ret = null != _next;
            if (!ret)
                closeQuietly(this);
            return ret;
        }

        public T next()
        {
            try
            {
                T ret = _next;
                _next = readNext();
                return ret;
            }
            catch (IOException x)
            {
                throw new RuntimeException(x);
            }
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        private T readNext() throws IOException
        {
            Object args[] = new Object[1];
            try
            {
                String line;
                while (null != (line = _in.readLine()))
                {
                    args[0] = StringUtils.splitPreserveAllTokens(line,'|');
                    T t = _constructor.newInstance(args);
                    if (null == _filter || _filter.accept(t))
                        return t;
                }
                return null;
            }
            catch (InvocationTargetException e)
            {
                throw new RuntimeException(e);
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException(e);
            }
            catch (InstantiationException e)
            {
                throw new RuntimeException(e);
            }
        }

        public void close() throws IOException
        {
            closeQuietly(_in);
        }

        @Override
        protected void finalize() throws Throwable
        {
            close();
        }
    }


    static class ConcatIterator<T> implements Iterator<T>, Closeable
    {
        final LinkedList<Iterator<T>> _iterators;
        Iterator<T> _current;
        T _next;
        
        ConcatIterator(Iterator<T>...iters)
        {
            _iterators = new LinkedList<Iterator<T>>(Arrays.asList(iters));
            _current = _iterators.isEmpty() ? null : _iterators.removeFirst();
            _next = readNext();
        }
        
        public boolean hasNext()
        {
            return null != _next;
        }

        public T next()
        {
            T ret = _next;
            _next = readNext();
            return ret;
        }

        private T readNext()
        {
            while (null != _current)
            {
                if (_current.hasNext())
                    return _current.next();
                if (_current instanceof Closeable)
                    closeQuietly((Closeable)_current);
                _current = _iterators.isEmpty() ? null : _iterators.removeFirst();
            }
            return null;
        }
        
        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        public void close() throws IOException
        {
            while (null != _current)
            {
                if (_current instanceof Closeable)
                    closeQuietly((Closeable)_current);
                _current = _iterators.isEmpty() ? null : _iterators.removeFirst();
            }
        }

        @Override
        protected void finalize() throws Throwable
        {
            close();
        }
        
    }


    private static String _min(String...strs)
    {
        String min = null;
        for (String s : strs)
        {
            if (min==null || (s!=null && min.compareTo(s)>0))
                min = s;
        }
        return min;
    }
    
    
    public static class MergeIterator implements Iterator<ArrayList>, Closeable
    {
        final Iterator<ConceptName> _names;
        final Iterator<Definition> _defs;
        final Iterator<SemanticType> _types;
        ConceptName name;
        Definition def;
        SemanticType type;

        MergeIterator(Iterator<ConceptName> names, Iterator<Definition> defs, Iterator<SemanticType> types)
        {
            _names = names;
            _defs = defs;
            _types = types;
            name = _names.hasNext() ? _names.next() : null;
            def = _defs.hasNext() ? _defs.next() : null;
            type = _types.hasNext() ? _types.next() : null;
        }

        public boolean hasNext()
        {
            boolean ret = null != name || null != def || null != type;
            if (!ret)
                closeQuietly(this);
            return ret;
        }

        public ArrayList next()
        {
            String nextCUI = _min(
                    null==name ? null : name.CUI,
                    null==def ? null : def.CUI,
                    null==type ? null : type.CUI);
            assert null != nextCUI;

            ArrayList ret = new ArrayList();
            int c;
            while (null != name && 0 <= (c=nextCUI.compareTo(name.CUI)))
            {
                if (c==0)
                    ret.add(name);
                name = _names.hasNext() ? _names.next() : null;
            }
            while (null != def && 0 <= (c=nextCUI.compareTo(def.CUI)))
            {
                if (c==0)
                    ret.add(def);
                def = _defs.hasNext() ? _defs.next() : null;
            }
            while (null != type && 0 <= (c=nextCUI.compareTo(type.CUI)))
            {
                if (c==0)
                    ret.add(type);
                type = _types.hasNext() ? _types.next() : null;
            }
            return ret;
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        public void close() throws IOException
        {
            if (_names instanceof Closeable)
                closeQuietly((Closeable)_names);
            if (_defs instanceof Closeable)
                closeQuietly((Closeable)_defs);
            if (_names instanceof Closeable)
                closeQuietly((Closeable)_names);
        }
    }


    Iterator<Definition> getDefinitions(Filter filter)
    {
        return new RRFReader<Definition>(Definition.class, mrdef, filter);
    }

    
    Iterator<SemanticType> getTypes(Filter filter)
    {
        return new RRFReader<SemanticType>(SemanticType.class, mrsty, filter);
    }


    Iterator<ConceptName> getNames(Filter filter)
    {
        RRFReader<ConceptName> name0 = new RRFReader<ConceptName>(ConceptName.class, mrconso[0], filter);
        RRFReader<ConceptName> name1 = new RRFReader<ConceptName>(ConceptName.class, mrconso[1], filter);
        return new ConcatIterator(name0, name1);
    }
    

    private static void closeQuietly(Closeable c)
    {
        if (null == c) return;
        try
        {
            c.close();
        }
        catch (IOException e)
        {
        }
    }


    public static void main(String[] args)
    {
        if (args.length == 0)
            args = new String[] {"."};

        if (false)
        {
            try
            {
                RRFLoader l = new RRFLoader(new File(args[0]));
                TreeSet<String> set = new TreeSet<String>();

                RRFReader<Definition> def = new RRFReader<Definition>(Definition.class, l.mrdef, null);
                while (def.hasNext())
                {
                    Definition d = def.next();
                    set.add(d.CUI);
                }


                RRFReader<SemanticType> sty = new RRFReader<SemanticType>(SemanticType.class, l.mrsty, null);
                while (sty.hasNext())
                {
                    SemanticType t = sty.next();
                    set.add(t.CUI);
                }


                RRFReader<ConceptName> con = new RRFReader<ConceptName>(ConceptName.class, l.mrconso[0], new Filter<ConceptName>()
                {
                    public boolean accept(ConceptName c)
                    {
                        return "ENG".equals(c.LAT);
                    }
                });
                while (con.hasNext())
                {
                    ConceptName c = con.next();
                    set.add(c.CUI);
                }
                System.out.println(set.size());
            }
            catch (IOException x)
            {
                x.printStackTrace(System.out);
            }
        }

        
        if (true)
        {
            try
            {
                RRFLoader l = new RRFLoader(new File(args[0]));
                Iterator<Definition> defs    = l.getDefinitions(null);
                Iterator<SemanticType> types = l.getTypes(null);
                Iterator<ConceptName> names  = l.getNames(new Filter<ConceptName>()
                    {
                        public boolean accept(ConceptName c)
                        {
                            return "ENG".equals(c.LAT);
                        }
                    });   
                
                MergeIterator concept = new MergeIterator(names,defs,types);
                while (concept.hasNext())
                {
                    ArrayList list = concept.next();
                }
            }
            catch (IOException x)
            {
                x.printStackTrace(System.out);
            }
        }
    }
}
