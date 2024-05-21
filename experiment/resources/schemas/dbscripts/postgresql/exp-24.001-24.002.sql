UPDATE exp.Material SET aliquotcount = 0, aliquotvolume = 0, availablealiquotcount = 0, availablealiquotvolume = 0
WHERE (aliquotcount IS NULL OR aliquotcount = 0) AND aliquotedfromlsid IS NULL;