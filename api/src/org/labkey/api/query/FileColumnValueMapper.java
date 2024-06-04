package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
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

    public Object saveFileColumnValue(User user, Container c, @Nullable Path fileLinkDirPath, String columnName, Object value) throws ValidationException, QueryUpdateServiceException
    {
        if (!(value instanceof MultipartFile || value instanceof AttachmentFile))
            return value;

        valueMap.putIfAbsent(columnName, new HashMap<>());
        String key = value instanceof MultipartFile ? ((MultipartFile) value).getOriginalFilename() : ((AttachmentFile) value).getFilename();

        if (!valueMap.get(columnName).containsKey(key))
        {
            value = AbstractQueryUpdateService.saveFile(user, c, columnName, value, fileLinkDirPath);
            valueMap.get(columnName).putIfAbsent(key, value);
        }

        return valueMap.get(columnName).get(key);
    }
}
