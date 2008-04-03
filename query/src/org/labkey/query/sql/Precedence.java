package org.labkey.query.sql;

public enum Precedence
{
    primary,
    unary, // + (Positive), - (Negative), ~ (Bitwise NOT)
    multiplication, // * (Multiply), / (Division), % (Modulo)
    addition, // + (Add), (+ Concatenate), - (Subtract), & (Bitwise AND)
    comparison, // =,  >,  <,  >=,  <=,  <>,  !=,  !>,  !< (Comparison operators)
    bitwiseor, // ^ (Bitwise Exlusive OR), | (Bitwise OR)
    not,
    and,
    like, // ALL, ANY, BETWEEN, IN, LIKE, OR, SOME
    assignment,
}
