/*
 * Copyright (c) 2009-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.query;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.data.xml.queryCustomView.PropertyName;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 17, 2009
 */
public interface CustomViewInfo
{
    enum ColumnProperty
    {
        columnTitle(PropertyName.COLUMN_TITLE);

        private PropertyName.Enum _xmlEnum;

        private ColumnProperty(PropertyName.Enum xmlEnum)
        {
            _xmlEnum = xmlEnum;
        }

        public PropertyName.Enum getXmlPropertyEnum()
        {
            return _xmlEnum;
        }

        public static ColumnProperty getForXmlEnum(PropertyName.Enum xmlEnum)
        {
            // There's only one possible value right now... once we add more, turn this into a loop or map lookup
            return columnTitle.getXmlPropertyEnum() == xmlEnum ? columnTitle : null;
        }
    }

    String getName();
    User getOwner();
    /** Convenience for <code>getOwner() == null</code> */
    boolean isShared();
    User getCreatedBy();
    Date getModified();

    String getSchemaName();
    String getQueryName();
    
    Container getContainer();
    boolean canInherit();
    boolean isHidden();
    boolean isEditable();
    /** @returns true if the custom view is in session state. */
    boolean isSession();
    String getCustomIconUrl();

    List<FieldKey> getColumns();
    List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> getColumnProperties();

    String getFilterAndSort();

    String getContainerFilterName();
    
    boolean hasFilterOrSort();

}
