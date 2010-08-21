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
package org.labkey.api.reader;

import java.io.IOException;
import java.util.List;

/**
 * User: adam
 * Date: Aug 8, 2010
 * Time: 9:55:29 AM
 */

// A loader of columnar data.   
public interface Loader<T>
{
    //public CloseableIterator<Map<String, Object>> iterator();
    public ColumnDescriptor[] getColumns() throws IOException;
    public List<T> load() throws IOException;
}
