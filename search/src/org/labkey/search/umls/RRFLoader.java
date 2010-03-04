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
    }

    public static class ConceptName  // MRCONSO
    {
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
    }

    public static class SemanticType // MRSTY
    {
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

    private static class RRFReader<T> implements Iterator
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
            return null != _next;
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
                    args[0] = StringUtils.split(line,'|');
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
    }


    static class MergeIterator implements Iterator<ArrayList>
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
            return null != name || null != def || null != type;
        }

        public ArrayList next()
        {
            String nextCUI = null;
            if (name != null)
                nextCUI = name.CUI;
            if (def != null)
                nextCUI = null==nextCUI || def.CUI.compareTo(nextCUI)<0 ? def.CUI : null;
            if (type != null)
                nextCUI = null==nextCUI || type.CUI.compareTo(nextCUI)<0 ? type.CUI : null;
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
                RRFReader<Definition> def = new RRFReader<Definition>(Definition.class, l.mrdef, null);
                RRFReader<SemanticType> sty = new RRFReader<SemanticType>(SemanticType.class, l.mrsty, null);
                RRFReader<ConceptName> name = new RRFReader<ConceptName>(ConceptName.class, l.mrconso[0], new Filter<ConceptName>()
                {
                    public boolean accept(ConceptName c)
                    {
                        return "ENG".equals(c.LAT);
                    }
                });
                MergeIterator concept = new MergeIterator(name,def,sty);
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
