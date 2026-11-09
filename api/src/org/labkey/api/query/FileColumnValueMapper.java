/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.vfs.FileLike;
import org.labkey.vfs.FileSystemLike;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Map for file column values to their file system path so that it can be shared across multiple rows during insert/update rows.
 */
public class FileColumnValueMapper
{
    Map<String, Map<String, Object>> valueMap = new HashMap<>();

    // TODO Path->FileObject
    public Object saveFileColumnValue(User user, Container c, @Nullable Path fileLinkDirPath, String columnName, Object value) throws ValidationException, QueryUpdateServiceException
    {
        if (!(value instanceof MultipartFile || value instanceof AttachmentFile))
            return value;

        valueMap.putIfAbsent(columnName, new HashMap<>());
        String key = value instanceof MultipartFile ? ((MultipartFile) value).getOriginalFilename() : ((AttachmentFile) value).getFilename();

        if (!valueMap.get(columnName).containsKey(key))
        {
            FileLike dirPath = null;
            // TODO convert fileLinkDirPath to FileObject
            if (null != fileLinkDirPath)
                dirPath = new FileSystemLike.Builder(fileLinkDirPath).readwrite().root();
            value = AbstractQueryUpdateService.saveFile(user, c, columnName, value, dirPath);
            valueMap.get(columnName).putIfAbsent(key, value);
        }

        return valueMap.get(columnName).get(key);
    }
}
