package org.labkey.api.study.assay;

import org.labkey.api.query.PropertyColumnDecorator;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.DomainProperty;
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
                _hasTargetStudy = Boolean.FALSE;
                for (DomainProperty batchDP : _provider.getBatchDomain(_protocol).getProperties())
                {
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(batchDP.getName()))
                    {
                        _hasTargetStudy = Boolean.TRUE;
                    }
                }
            }

            if (Boolean.TRUE.equals(_hasTargetStudy))
            {
                columnInfo.setFk(new SpecimenForeignKey(_schema, _provider, _protocol));
            }
        }
    }
}
