-- Migrate "Weak" password strength setting to "Good"
UPDATE prop.Properties SET Value = 'Good' WHERE "Set" = (SELECT "Set" FROM prop.propertysets WHERE category = 'DatabaseAuthentication') AND Name = 'Strength' AND Value = 'Weak';
