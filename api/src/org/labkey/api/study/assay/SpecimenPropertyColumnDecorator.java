/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.labkey.api.query.PropertyColumnDecorator;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.data.ColumnInfo;

/**
 * User: jeckels
* Date: May 27, 2009
*/
public class SpecimenPropertyColumnDecorator implements PropertyColumnDecorator
{
    private Boolean _hasTargetStudy;
    private final AssayProvider _provider;
    private final ExpProtocol _protocol;
    private final AssaySchema _schema;

    public SpecimenPropertyColumnDecorator(AssayProvider provider, ExpProtocol protocol, AssaySchema schema)
    {
        _provider = provider;
        _protocol = protocol;
        _schema = schema;
    }

    public void decorateColumn(ColumnInfo columnInfo, PropertyDescriptor pd)
    {
        if (AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equals(pd.getName()) && pd.getLookupQuery() == null && pd.getLookupSchema() == null)
        {
            if (_hasTargetStudy == null)
            {
                // Cache this so that we don't have to figure it out for every single property
                _hasTargetStudy = _provider.findTargetStudyProperty(_protocol) != null;
            }

            if (Boolean.TRUE.equals(_hasTargetStudy))
            {
                columnInfo.setFk(new SpecimenForeignKey(_schema, _provider, _protocol));
                columnInfo.setURL(columnInfo.getFk().getURL(columnInfo));
                columnInfo.setDisplayColumnFactory(ColumnInfo.NOLOOKUP_FACTORY);
            }
        }
    }
}
