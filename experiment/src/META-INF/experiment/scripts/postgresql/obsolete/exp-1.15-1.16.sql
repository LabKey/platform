ALTER TABLE exp.ProtocolParameter RENAME COLUMN StringValue TO ShortStringValue;
ALTER TABLE exp.ProtocolParameter ADD COLUMN StringValue varchar(4000) NULL;
UPDATE exp.ProtocolParameter SET StringValue = ShortStringValue;
ALTER TABLE exp.ProtocolParameter DROP COLUMN ShortStringValue;

UPDATE exp.ProtocolParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink';

ALTER TABLE exp.ProtocolParameter DROP COLUMN FileLinkValue;

ALTER TABLE exp.ProtocolParameter DROP COLUMN XmlTextValue;

ALTER TABLE exp.ProtocolApplicationParameter RENAME COLUMN StringValue TO ShortStringValue;
ALTER TABLE exp.ProtocolApplicationParameter ADD COLUMN StringValue varchar(4000) NULL;
UPDATE exp.ProtocolApplicationParameter SET StringValue = ShortStringValue;
ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN ShortStringValue;

UPDATE exp.ProtocolApplicationParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink';

ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN FileLinkValue;

ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN XmlTextValue;
