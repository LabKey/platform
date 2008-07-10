CREATE VIEW prop.PropertyEntries AS
	SELECT ObjectId, Category, UserId, Name, Value FROM prop.Properties JOIN prop.PropertySets ON PropertySets.Set = Properties.Set;



