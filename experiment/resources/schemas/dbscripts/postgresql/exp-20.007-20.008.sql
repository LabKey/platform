ALTER TABLE exp.propertyvalidator RENAME TO pv_old;

CREATE TABLE exp.PropertyValidator
(
    RowId        serial not null,
    Name         varchar(50) not null,
    Description  varchar(200),
    TypeURI      varchar(200) not null,
    Expression   text,
    ErrorMessage text,
    Properties   text,
    Container    entityid not null constraint fk_pv_container references core.containers (entityid),
    PropertyId   int not null constraint fk_pv_descriptor references exp.propertydescriptor,
    constraint pk_propertyvalidator primary key (container, propertyid, rowid)
);

INSERT INTO exp.propertyvalidator(propertyid, name, description, typeuri, expression, properties, errormessage, container)
SELECT VR.propertyid, PV.name, PV.description, PV.typeuri, PV.expression, PV.properties, PV.errormessage, PV.container
FROM exp.pv_old PV INNER JOIN exp.validatorreference VR ON PV.rowid = VR.validatorid
ORDER BY container, propertyid;

CREATE INDEX ix_propertyvalidator_propertyid on exp.PropertyValidator(PropertyId);

DROP TABLE exp.validatorreference;
DROP TABLE exp.pv_old;
