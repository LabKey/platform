UPDATE  prop.Properties
SET     value = 'ON'
WHERE   [Set] IN
        (SELECT [Set]
        FROM prop.PropertySets
        WHERE Category = 'SiteConfig')
AND     Name = 'usageReportingLevel';