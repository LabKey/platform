/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.jetbrains.annotations.Nullable;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: May 29, 2007
 * Time: 10:42:42 AM
 */
public class FTPUtil
{
    // Return a list of all filenames in the specified directory
    public static List<String> listFiles(String user, String password, String url, String directory) throws IOException, ServletException
    {
        return listFiles(user, password, url, directory, null);
    }


    // Return a list of filenames in the specified directory that match the specified regex pattern
    public static List<String> listFiles(String user, String password, String url, String directory, @Nullable String filterPattern) throws IOException, ServletException
    {
        FTPClient ftp = null;

        try
        {
            ftp = getConnectedClient(user, password, url, directory);

            FTPFile[] files = ftp.listFiles();
            List<String> filenames = new ArrayList<>(files.length);

            Pattern p = (null != filterPattern ? Pattern.compile(filterPattern) : null);

            for (FTPFile file : files)
                if (file.isFile() && (p == null || p.matcher(file.getName()).matches()))
                    filenames.add(file.getName());

            return filenames;
        }
        finally
        {
            close(ftp);
        }
    }


    // Download the specified file to a temporary file
    public static File downloadFile(String user, String password, String url, String directory, String filename) throws IOException, ServletException
    {
        FTPClient ftp = null;
        FileOutputStream fos = null;

        try
        {
            ftp = getConnectedClient(user, password, url, directory);
            ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
            File outputFile = File.createTempFile(filename, null);
            fos = new FileOutputStream(outputFile);
            ftp.retrieveFile(filename, fos);
            return outputFile;
        }
        finally
        {
            if (null != fos)
                fos.close();

            close(ftp);
        }
    }


    private static void close(FTPClient ftp) throws IOException
    {
        if (null != ftp)
        {
            ftp.logout();
            ftp.disconnect();
        }
    }


    private static FTPClient getConnectedClient(String user, String password, String url, String directory) throws IOException, ServletException
    {
        FTPClient f = new FTPClient();
        f.connect(url);
        f.enterLocalPassiveMode();
        f.login(user, password);

        if (!FTPReply.isPositiveCompletion(f.getReplyCode()))
            throw new ServletException("Could not connect to FTP server " + url + ".  Message returned was " + f.getReplyString());

        f.changeWorkingDirectory(directory);

        return f;
    }
}
