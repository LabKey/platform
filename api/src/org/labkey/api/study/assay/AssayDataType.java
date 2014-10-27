/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.api.study.assay;

import com.google.common.base.Objects;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.util.FileType;

/**
 * User: jeckels
 * Date: Dec 22, 2009
 */
public class AssayDataType extends DataType
{
    private final String _role;
    private final FileType _fileType;

    public AssayDataType(String namespacePrefix, FileType fileType)
    {
        this(namespacePrefix, fileType, ExpDataRunInput.DEFAULT_ROLE);
    }

    public AssayDataType(String namespacePrefix, FileType fileType, String role)
    {
        super(namespacePrefix);
        _fileType = fileType;
        _role = role;
    }

    public FileType getFileType()
    {
        return _fileType;
    }

    public String getRole()
    {
        return _role;
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .omitNullValues()
                .addValue(_namespacePrefix)
                .add("role", _role)
                .add("fileType", _fileType)
                .toString();
    }


}
