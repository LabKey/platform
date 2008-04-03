BEGIN TRANSACTION
DELETE FROM exp.DataInput WHERE 
	(dataid IN (SELECT rowid FROM exp._orphanDataView )) OR 
	(targetapplicationid IN	(SELECT rowid FROM exp._orphanProtocolApplicationView))

DELETE FROM exp.MaterialInput WHERE 
	(materialid IN (SELECT rowid FROM exp._orphanMaterialView)) OR 
	(targetapplicationid IN	(SELECT rowid FROM exp._orphanProtocolApplicationView))

DELETE FROM exp.Data WHERE rowid IN (SELECT rowid FROM exp._orphanDataView)

DELETE FROM exp.Material WHERE rowid IN (SELECT rowid FROM exp._orphanMaterialView)

DELETE FROM exp.protocolapplicationparameter WHERE 
	(ProtocolApplicationId IN (SELECT rowid FROM exp._orphanProtocolApplicationView))

DELETE FROM exp.ProtocolApplication WHERE rowid IN (SELECT rowid FROM exp._orphanProtocolApplicationView)

DELETE FROM exp.MaterialSource WHERE rowid IN (SELECT rowid FROM exp._orphanMaterialSourceView)

DELETE FROM exp.ExperimentRun WHERE rowid IN (SELECT rowid FROM exp._orphanExperimentRunView)

DELETE FROM exp.protocolactionpredecessor WHERE 
	(actionid IN (SELECT rowid FROM exp.protocolaction WHERE parentprotocolid IN (SELECT rowid FROM exp._orphanProtocolView)))

DELETE FROM exp.protocolaction WHERE 
	(parentprotocolid IN (SELECT rowid FROM exp._orphanProtocolView))

DELETE FROM exp.protocolparameter WHERE 
	(protocolid IN (SELECT rowid FROM exp._orphanProtocolView))

DELETE FROM exp.Protocol WHERE rowid IN (SELECT rowid FROM exp._orphanProtocolView)

DELETE FROM exp.Experiment WHERE rowid IN (SELECT rowid FROM exp._orphanExperimentView)

DELETE FROM exp.ObjectProperty WHERE
	(objectid IN (SELECT objectid FROM exp._orphanObjectView))

DELETE FROM exp.Object WHERE objectid IN (SELECT objectid FROM exp._orphanObjectView)

DELETE FROM exp.PropertyDescriptor WHERE propertyid IN (SELECT propertyid FROM exp._orphanPropDescView)

COMMIT TRANSACTION
go