ALTER TABLE exp.BioSource DROP CONSTRAINT FK_BioSource_Material;
ALTER TABLE exp.Fraction DROP CONSTRAINT FK_Fraction_Material;

DROP TABLE exp.BioSource;
DROP TABLE exp.Fraction;

ALTER TABLE exp.PropertyDescriptor ADD Container ENTITYID NULL;

START TRANSACTION;
-- First delete orphaned PD's
DELETE FROM exp.PropertyDescriptor WHERE PropertyID IN
	(SELECT PD.PropertyID 
	FROM exp.PropertyDescriptor PD LEFT JOIN exp.ObjectProperty OP 	ON (PD.PropertyId = OP.PropertyID)
	WHERE OP.PropertyID IS NULL)
;
-- Move PDs to the container where they are referenced.  
-- if multiple, pick one via MAX
UPDATE exp.PropertyDescriptor
	SET Container =
			(SELECT  MAX(CAST (O.Container AS VARCHAR(100)))
				FROM exp.PropertyDescriptor PD
					INNER JOIN exp.ObjectProperty OP ON (PD.PropertyId = OP.PropertyID)
					INNER JOIN exp.Object O ON (O.ObjectId = OP.ObjectID)
				WHERE PDU.PropertyID = PD.PropertyID
				GROUP BY PD.PropertyID
				)
	FROM exp.PropertyDescriptor PDU
	WHERE PDU.Container IS NULL AND
	      PDU.PropertyID IN
			(SELECT PD.PropertyID
			FROM exp.PropertyDescriptor PD
				INNER JOIN exp.ObjectProperty OP ON (PD.PropertyId = OP.PropertyID)
				INNER JOIN exp.Object O ON (O.ObjectId = OP.ObjectID)
			GROUP BY PD.PropertyID
			)
;
COMMIT TRANSACTION;

ALTER TABLE exp.PropertyDescriptor ALTER COLUMN Container SET NOT NULL;

ALTER TABLE exp.Object DROP CONSTRAINT UQ_Object;

ALTER TABLE exp.Object ADD CONSTRAINT UQ_Object UNIQUE (ObjectURI);

DROP INDEX exp.IDX_Object_OwnerObjectId;

-- cluster candidate
CREATE INDEX IDX_Object_ContainerOwnerObjectId ON exp.Object (Container, OwnerObjectId, ObjectId);

DROP FUNCTION exp.setProperty(INTEGER, LSIDType, LSIDType, CHAR(1), FLOAT, varchar(400), timestamp, TEXT) ;