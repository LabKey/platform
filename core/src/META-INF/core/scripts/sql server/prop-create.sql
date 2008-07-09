CREATE VIEW prop.PropertyEntries AS
    SELECT ObjectId, Category, UserId, Name, Value FROM prop.Properties JOIN prop.PropertySets ON prop.PropertySets."Set" = prop.Properties."Set"
GO


