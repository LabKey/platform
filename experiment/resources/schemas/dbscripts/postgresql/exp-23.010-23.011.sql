UPDATE exp.materialsource
    SET aliquotnameexpression = '${${AliquotedFrom}-:withCounter}'
    WHERE aliquotnameexpression IS NULL;
