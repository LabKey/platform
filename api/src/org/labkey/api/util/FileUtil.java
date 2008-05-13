/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.nio.channels.FileChannel;

/**
 * User: jeckels
 * Date: Dec 5, 2005
 */
public class FileUtil
{
    public static boolean deleteDirectoryContents(File dir)
    {
        if (dir.isDirectory())
        {
            String[] children = dir.list();
            for (String aChildren : children)
            {
                boolean success = deleteDir(new File(dir, aChildren));
                if (!success)
                {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean deleteSubDirs(File dir)
    {
        if (dir.isDirectory())
        {
            String[] children = dir.list();
            for (String aChildren : children)
            {
                boolean success = true;
                File child = new File(dir, aChildren);
                if (child.isDirectory())
                    success = deleteDir(child);
                if (!success)
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** File.delete() will only delete a directory if it's empty, but this will
     * delete all the contents and the directory */
    public static boolean deleteDir(File dir)
    {
        boolean success = deleteDirectoryContents(dir);
        if (!success)
        {
            return false;
        }

        // The directory is now empty so delete it
        return dir.delete();
    }

    /**
     * Remove text right of a specific number of periods, including the periods, from a file's name.
     * <ul>
     *  <li>C:\dir\name.ext, 1 => name</li>
     *  <li>C:\dir\name.ext1.ext2, 2 => name</li>
     *  <li>C:\dir\name.ext1.ext2, 1 => name.ext1</li>
     * </ul>
     *
     * @param file file from which to get the name
     * @param dots number of dots to remove
     * @return base name
     */
    public static String getBaseName(File file, int dots)
    {
        String baseName = file.getName();
        while (dots-- > 0)
            baseName = baseName.substring(0, baseName.lastIndexOf('.'));
        return baseName;
    }

    /**
     * Remove text right of and including the last period in a file's name.
     * @param file file from which to get the name
     * @return base name
     */
    public static String getBaseName(File file)
    {
        return getBaseName(file, 1);
    }

    /**
     * Get relative path of File 'file' with respect to 'home' directory
     * <p><pre>
     * example : home = /a/b/c
     *           file    = /a/d/e/x.txt
     *           return = ../../d/e/x.txt
     * </pre><p>
     * The path returned has system specific directory separators.
     * <p>
     * It is equivalent to:<br>
     * <pre>home.toURI().relativize(f.toURI).toString().replace('/', File.separatorChar)</pre>
     *
     * @param home base path, should be a directory, not a file, or it doesn't make sense
     * @param file    file to generate path for
     * @return path from home to file as a string
     */
    public static String relativize(File home, File file) throws IOException
    {
        return matchPathLists(getPathList(home), getPathList(file));
    }

    /**
     * Get a relative path of File 'file' with respect to 'home' directory,
     * forcing Unix (i.e. URI) forward slashes for directory separators.
     * <p>
     * This is a lot like <cod>URIUtil.relativize()</code> without requiring
     * that the file be a descendant of the base.
     * <p>
     * It is equivalent to:<br>
     * <pre>home.toURI().relativize(f.toURI).toString()</pre>
     */
    public static String relativizeUnix(File home, File f) throws IOException
    {
        return relativize(home, f).replace('\\', '/');
    }


    /**
     * Break a path down into individual elements and add to a list.
     * <p/>
     * example : if a path is /a/b/c/d.txt, the breakdown will be [d.txt,c,b,a]
     *
     * @param file input file
     * @return a List collection with the individual elements of the path in reverse order
     */
    public static List<String> getPathList(File file) throws IOException
    {
        List<String> parts = new ArrayList<String>();
        File fileAll = file.getCanonicalFile();
        while (fileAll != null)
        {
            parts.add(fileAll.getName());
            fileAll = fileAll.getParentFile();
        }

        return parts;
    }


    /**
     * Figure out a string representing the relative path of
     * 'file' with respect to 'home'
     *
     * @param home home path
     * @param file path of file
     * @return relative path from home to file
     */
    public static String matchPathLists(List<String> home, List<String> file)
    {
        // start at the beginning of the lists
        // iterate while both lists are equal
        StringBuffer path = new StringBuffer();
        int i = home.size() - 1;
        int j = file.size() - 1;

        // first eliminate common root
        while ((i >= 0) && (j >= 0) && (home.get(i).equals(file.get(j))))
        {
            i--;
            j--;
        }

        // for each remaining level in the home path, add a ..
        for (; i >= 0; i--)
            path.append("..").append(File.separator);

        // for each level in the file path, add the path
        for (; j >= 1; j--)
            path.append(file.get(j)).append(File.separator);

        // if nothing left of the file, then it was a directory
        // of which home is a subdirectory.
        if (j < 0)
        {
            if (path.length() == 0)
                path.append(".");
            else
                path.delete(path.length() - 1, path.length());  // remove trailing sep
        }
        else
            path.append(file.get(j));   // add file name

        return path.toString();
    }


    public static void copyFile(File src, File dst) throws IOException
    {
        dst.createNewFile();
        FileChannel in = null;
        FileChannel out = null;
        try
        {
            in = new FileInputStream(src).getChannel();
            out = new FileOutputStream(dst).getChannel();
            in.transferTo(0, in.size(), out);
        }
        finally
        {
            if (null != in)
                in.close();
            if (null != out)
                out.close();
        }
    }


    /**
     * always returns path starting with /.  Tries to leave trailing '/' as is
     * (unless ends with /. or /..)
     * 
     * @param path path to normalize
     * @return cleaned path or null if path goes outside of 'root'
     */
    public static String normalize(String path)
    {
        if (path == null || equals(path,'/'))
            return path;

        String str = path;
        if (str.indexOf('\\') >= 0)
            str = str.replace('\\', '/');
        if (!startsWith(str,'/'))
            str = "/" + str;
        int len = str.length();

        // quick scan, look for /. or //
quickScan:
        {
            for (int i=0 ; i<len-1 ; i++)
            {
                char c0 = str.charAt(i);
                if (c0 != '/') continue;
                char c1 = str.charAt(i+1);
                if (c1 == '.' || c1 == '/')
                    break quickScan;
                i++;    //already looked at c1
            }
            return str;
        }

        ArrayList<String> list = normalizeSplit(str);
        if (null == list)
            return null;
        if (list.isEmpty())
            return "/";
        StringBuilder sb = new StringBuilder(str.length()+2);
        for (String name : list)
        {
            sb.append('/');
            sb.append(name);
        }
        return sb.toString();
    }


    public static ArrayList<String> normalizeSplit(String str)
    {
        int len = str.length();
        ArrayList<String> list = new ArrayList<String>();
        int start = 0;
        for (int i=0 ; i<=len ; i++)
        {
            if (i==len || str.charAt(i) == '/')
            {
                if (start < i)
                {
                    String part = str.substring(start, i);
                    if (part.length()==0 || equals(part,'.'))
                    {
                    }
                    else if (part.equals(".."))
                    {
                        if (list.isEmpty())
                            return null;
                        list.remove(list.size()-1);
                    }
                    else
                    {
                        list.add(part);
                    }
                }
                start=i+1;
            }
        }
        return list;
    }


    static boolean startsWith(String s, char ch)
    {
        return s.length() > 0 && s.charAt(0) == ch;
    }

    static boolean endsWith(String s, char ch)
    {
        return s.length() > 0 && s.charAt(s.length()-1) == ch;
    }

    static boolean equals(String s, char ch)
    {
        return s.length() == 1 && s.charAt(0) == ch;
    }

    public static String relativePath(String dir, String filePath)
    {
        dir = normalize(dir);
        filePath = normalize(filePath);
        if (dir.endsWith("/"))
            dir = dir.substring(0,dir.length()-1);
        if (!filePath.toLowerCase().startsWith(dir.toLowerCase()))
            return null;
        String relPath = filePath.substring(dir.length());
        if (relPath.length() == 0)
            return relPath;
        if (relPath.startsWith("/"))
            return relPath.substring(1);
        return null;
    }
}
