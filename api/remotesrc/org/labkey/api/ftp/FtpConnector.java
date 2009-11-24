/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.ftp;

import java.io.Serializable;
import java.io.File;

/**
 * User: matthewb
 * Date: Oct 3, 2007
 * Time: 3:56:16 PM
 */
public interface FtpConnector
{
    public static final String PIPELINE_LINK = "@pipeline";
    
    // UNDONE are calls done as guest,user,special user
    // UNDONE: for now pass in user to every method

    // -1 means fail, 0 guest, >0 userid
    int userid(String username, String password) throws Exception;

    // return detailed information about this folder
    WebFolderInfo getFolderInfo(int userid, String folder);

    // return a list of all folders below this folder (list includes path)
    String[] getAllChildren(String path);

    void audit(int userid, String path, String msg);

    // This class describes the labkey web server state
    // it is not meant to indicate the existance or non-existance of file system directories
    // NOTE: fsRoot and pipelineRoot are non-null only if explictly set for this folder
    public static class WebFolderInfo implements Serializable
    {
        public String url;
        public String path;
        public String name;
        public long created;
        public int perm;
        public FileSystemRoot  fsRoot;
        public String[] subfolders;
    }

    public static class FileSystemRoot implements Serializable
    {
        public String drivePath;
        public String driveUser;
        public String drivePassword;
        public String path;

        public FileSystemRoot(File f)
        {
            path = f.getAbsolutePath();
        }
    }
}