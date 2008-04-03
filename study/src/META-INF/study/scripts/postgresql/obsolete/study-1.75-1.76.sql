UPDATE exp.ObjectProperty SET
    StringValue = CAST(exp.ObjectProperty.floatValue AS INTEGER),
    TypeTag = 's',
    floatValue = NULL
WHERE
    (SELECT exp.PropertyDescriptor.PropertyURI FROM exp.PropertyDescriptor
        WHERE exp.PropertyDescriptor.PropertyId =
        exp.ObjectProperty.PropertyId) LIKE '%StudyDataset.%NAB#FileId' AND
    (SELECT exp.PropertyDescriptor.RangeURI FROM exp.PropertyDescriptor
        WHERE exp.PropertyDescriptor.PropertyId = exp.ObjectProperty.PropertyId) =
        'http://www.w3.org/2001/XMLSchema#int';

UPDATE exp.PropertyDescriptor SET
    RangeURI = 'http://www.w3.org/2001/XMLSchema#string'
WHERE
    exp.PropertyDescriptor.RangeURI = 'http://www.w3.org/2001/XMLSchema#int' AND
    exp.PropertyDescriptor.PropertyURI LIKE '%StudyDataset.%NAB#FileId';