grammar SqlBase;

options
{
    language=Java;
    output=AST;
    memoize=true;
    backtrack=true; // primaryExpression : OPEN! ( subQuery | expression ) CLOSE!
    ASTLabelType=CommonTree;
}

//
// SQL language grammar.
// This source code was modified from the Hibernate project ('hql.g').
//

tokens
{
    ASCENDING;
	AGGREGATE;		// One of the aggregate functions (e.g. min, max, avg)
	ALIAS;
    CASE2;
    DATATYPE;
	DATE_LITERAL;
    DECLARATION;
	DESCENDING;
	EXPR_LIST;
	IN_LIST;
	IS_NOT;
	METHOD_CALL;
	NOT_BETWEEN;
	NOT_IN;
	NOT_LIKE;
    PARAMETERS;
	QUERY;
	RANGE;
	ROW_STAR;
	SELECT_FROM;
    STATEMENT;
    TABLE_PATH_SUBSTITUTION;
	TIMESTAMP_LITERAL;
	UNARY_MINUS;
	UNARY_PLUS;
	UNION_ALL;
}


@header
{
	package org.labkey.query.sql.antlr;
    import org.labkey.query.sql.SupportsAnnotations;
}


@lexer::header
{
	package org.labkey.query.sql.antlr;

    import org.apache.log4j.Category;
    import org.labkey.query.sql.SqlParser;
}


@members
{
    /**
     * This method looks ahead and converts . <token> into . IDENT when
     * appropriate.
     */
    public void handleDotIdent() //throws TokenStreamException
    {
    }

	public void weakKeywords() //throws TokenStreamException
	{
	}

	public boolean isSqlType(String type)
	{
	    return true;
	}

    @Override
    public void traceOut(String ruleName, int ruleIndex, Object inputSymbol)
    {
        super.traceOut(ruleName, ruleIndex, inputSymbol);
    }

    HashMap<String,Object> _annotations;

    public void addAnnotation(String label, Object value)
    {
        if (null == _annotations)
            _annotations = new HashMap<String,Object>();
        if (label.startsWith("@"))
            label = label.substring(1);
        _annotations.put(label.toLowerCase(), value);
    }

    public Map<String,Object> getAnnotations()
    {
        HashMap<String,Object> ret = _annotations;
        _annotations = null;
        return ret;
    }
}


@lexer::members
{
    Category _log = Category.getInstance(SqlParser.class);
    
    protected void setPossibleID(boolean possibleID)
    {
    }

    @Override
    public void emitErrorMessage(String msg)
    {
        _log.debug(msg);
    }
}


//
// SQL TOKENS
//

ALL : 'all';
ANY : 'any';
AND : 'and';
AS : 'as';
AVG : 'avg';
BETWEEN : 'between';
CASE : 'case';
CAST : 'cast';
COUNT : 'count';
CROSS : 'cross';
DELETE : 'delete';
DISTINCT : 'distinct';
DOT : '.';
ELSE : 'else';
END : 'end';
ESCAPE : 'escape';
EXCEPT : 'except';
EXISTS : 'exists';
FALSE : 'false';
FROM : 'from';
FULL : 'full';
GROUP : 'group';
HAVING : 'having';
IFDEFINED : 'ifdefined';
IN : 'in';
INNER : 'inner';
INSERT : 'insert';
INTERSECT : 'intersect';
INTO : 'into';
IS : 'is';
JOIN : 'join';
LEFT : 'left';
LIKE : 'like';
LIMIT : 'limit';
MAX : 'max';
GROUP_CONCAT : 'group_concat';
MIN : 'min';
NOT : 'not';
NULL : 'null';
ON : 'on';
OR : 'or';
ORDER : 'order';
OUTER : 'outer';
PIVOT : 'pivot';
RIGHT : 'right';
SELECT : 'select';
SET : 'set';
SOME : 'some';
STDDEV : 'stddev';
STDERR : 'stderr';
SUM : 'sum';
THEN : 'then';
TRUE : 'true';
UNION : 'union';
UPDATE : 'update';
WHERE : 'where';
WHEN : 'when';
WITH : 'with';


//
// SQL GRAMMAR
//


statement
	: parameters? commonTableExpressions? ( updateStatement | deleteStatement | selectStatement | insertStatement )
	    -> ^(STATEMENT parameters? commonTableExpressions? updateStatement? deleteStatement? selectStatement? insertStatement?)
	;


parameters
    :   'parameters' OPEN declarationList CLOSE -> ^(PARAMETERS declarationList)
    ;


declarationList
	: declaration (COMMA! declaration)*
    ;


declaration
    : identifier sqltype ('default' constant)? -> ^(DECLARATION identifier sqltype constant?)
    ;


sqltype
    : type=identifier (OPEN! NUM_INT CLOSE!)?
    {
        if (!isSqlType($type.text))
            reportError(new MismatchedTokenException(DATATYPE, input));
    }
    ;
    

updateStatement
	: UPDATE^ 
		optionalFromTokenFromClause
		setClause
		(whereClause)?
	;

setClause
	: (SET^ assignment (COMMA! assignment)*)
	;

assignment
	: stateField EQ^ newValue
	;

// 'state_field' is the term used in the EJB3 sample grammar; used here for easy reference.
// it is basically a property ref
stateField
	: path
	;

// this still needs to be defined in the ejb3 spec; additiveExpression is currently just a best guess,
// although it is highly likely I would think that the spec may limit this even more tightly.
newValue
	: valueExpression
	;

deleteStatement
	: DELETE^
		(optionalFromTokenFromClause)
		(whereClause)?
	;

optionalFromTokenFromClause!
	: (FROM)? f=path (AS? a=identifier)?
	    -> ^(FROM $f $a)
	;


commonTableExpressions
    : WITH^ commonTableExpression (COMMA! commonTableExpression)*
    ;


commonTableExpression
    : identifier AS^ OPEN! selectStatement CLOSE!
    ;


selectStatement
	: q=union
		((o=orderByClause! {$q.tree.addChild($o.tree);})?)
		((l=limitClause! {$q.tree.addChild($l.tree);})?)
	;

insertStatement
	// Would be nice if we could abstract the FromClause/FromElement logic
	// out such that it could be reused here; something analogous to
	// a 'table' rule in sql-grammars
	: INSERT^ intoClause select
	;

intoClause
	: INTO^ path { weakKeywords(); } insertablePropertySpec
	;

insertablePropertySpec
	: OPEN! primaryExpression ( COMMA! primaryExpression )* CLOSE!
	;


union
  : unionTerm ((EXCEPT^|INTERSECT^|(u=UNION^ (ALL! { $u.tree.getToken().setType(UNION_ALL); })?)) unionTerm)*
  ;


unionTerm
  : select
  | OPEN! selectStatement CLOSE!
  ;


select
	: (selectFrom (whereClause)? (groupByClause (havingClause)? (pivotClause)?)?)
	    -> ^(QUERY selectFrom whereClause? groupByClause? havingClause? pivotClause?)
    ;


selectFrom!
	: (selectClause fromClause?)
	    -> ^(SELECT_FROM selectClause fromClause?)
	;


selectClause
	: SELECT^ { weakKeywords(); }	// Weak keywords can appear immediately after a SELECT token.
		(DISTINCT)? ( selectedPropertiesList )
	;


// NOTE: This *must* begin with the 'FROM' token, otherwise the sub-query rule will be ambiguous
// with the expression rule.
// Also note: after a comma weak keywords are allowed and should be treated as identifiers.

fromClause
	: FROM^ { weakKeywords(); } joinExpression (COMMA! { weakKeywords(); } joinExpression )*
	;


joinExpression
	: ((fromRange) -> fromRange)
	    (
	        ((((jt=LEFT|jt=RIGHT|jt=FULL) (OUTER)?) | jt=INNER)? JOIN fromRange onClause) -> ^(JOIN $joinExpression $jt? fromRange onClause {$jt=null})
	      | (CROSS JOIN fromRange) -> ^(JOIN $joinExpression CROSS fromRange)
      )*
    ;


fromRange
	: (tableSpecification { weakKeywords(); } (AS? identifier)?) -> ^(RANGE tableSpecification identifier?)
	| OPEN
	    ( (subQuery) => subQuery CLOSE (AS? identifier)? -> ^(RANGE subQuery identifier?)
	    | joinExpression CLOSE -> joinExpression
	    )
	;


// Usually a simple dotted identifer 'path' such as "core.users".
// however we support an 'escape' syntax as well such as "Folder.{moduleProperty('ehr','sharedFolder')}.specieslookup"
tableSpecification
    :  ( { weakKeywords(); } tableSpecificationPart DOT^ )* identifier
    ;


tableSpecificationPart
    : identifier
    | '{' specType=identifier specFn=identifier OPEN constantExprList CLOSE '}' -> ^(TABLE_PATH_SUBSTITUTION $specType $specFn constantExprList)
    ;


onClause
	: ON^ logicalExpression
	;


groupByClause
	: GROUP^ 'by'! expression annotations ( COMMA! expression annotations)*
	;


pivotClause
    : PIVOT^ identifierList 'by'! identifier (IN! OPEN! (selectStatement | constantAliasList) CLOSE!)?
    ;
    

orderByClause
	: ORDER^ 'by'! orderElement annotations ( COMMA! orderElement annotations)*
	;


limitClause
    : LIMIT^ NUM_INT;


orderElement
	: expression ( ascendingOrDescending )?
	;


ascendingOrDescending
	: ( 'asc' | 'ascending' )   -> ^(ASCENDING)
	| ( 'desc' | 'descending') 	-> ^(DESCENDING)
	;


havingClause
	: HAVING^ logicalExpression
	;


whereClause
	: WHERE^ logicalExpression
	;


selectedPropertiesList
    // weird trailing comma for backward compatibility, leave trailing comma in tree so we can create warnung (see SqlParser)
	: selectedProperty (COMMA! selectedProperty)* (COMMA)?
	;


selectedProperty
	: e=aliasedSelectExpression^ (annotations!)? { ((SupportsAnnotations)e.getTree()).setAnnotations(getAnnotations()); }
	| starAtom
	;


aliasedSelectExpression
    : (expression (AS? identifier)?) -> ^(ALIAS expression identifier?)
    ;


identifierList
	: identifier (COMMA identifier)* -> ^(EXPR_LIST identifier*)
	;


constantAliasList
	: constantAlias (COMMA constantAlias)* -> ^(SELECT constantAlias*)
	;


constantAlias
    : constant (AS? identifier)? -> ^(ALIAS constant identifier?)
    ;


annotations
    : (annotation!)*
    ;


annotation
    :   (label=ANNOTATION_LABEL (EQ! value=constant)? {addAnnotation(label.getText(),null==value?null:value.getTree());})!
    ;


annotation_label
    :   ANNOTATION_LABEL
    ;


// expressions
// Note that most of these expressions follow the pattern
//   thisLevelExpression :
//       nextHigherPrecedenceExpression
//           (OPERATOR nextHigherPrecedenceExpression)*
// which is a standard recursive definition for a parsing an expression.
//
// Operator precedence in HQL
// lowest  --> ( 7)  OR
//             ( 6)  AND, NOT
//             ( 5)  equality: ==, <>, !=, is
//             ( 4)  relational: <, <=, >, >=,
//                   LIKE, NOT LIKE, BETWEEN, NOT BETWEEN, IN, NOT IN
//             ( 3)  addition and subtraction: +(binary) -(binary)
//             ( 2)  multiplication: * / %, concatenate: ||
// highest --> ( 1)  +(unary) -(unary)
//                   []   () (method call)  . (dot -- identifier qualification)
//                   aggregate function
//                   ()  (explicit parenthesis)
//
// Note that the above precedence levels map to the rules below...
// Once you have a precedence chart, writing the appropriate rules as below
// is usually very straightforward

logicalExpression
	: expression
	;

// Main expression rule
expression
	: logicalOrExpression
	;

// level 7 - OR
logicalOrExpression
	: logicalAndExpression ( OR^ logicalAndExpression )*
	;

// level 6 - AND, NOT
logicalAndExpression
	: negatedExpression ( AND^ negatedExpression )*
	;

negatedExpression
	: NOT^ negatedExpression
	| { weakKeywords(); } equalityExpression
	;


//## OP: EQ | LT | GT | LE | GE | NE | SQL_NE | LIKE;

// level 5 - EQ, NE
equalityExpression
	: EXISTS^ OPEN! subQuery CLOSE!
	| relationalExpression (
		( EQ^
		| is=IS^ (NOT!  { $is.setType(IS_NOT); } )?
		| NE^
		| ne=SQL_NE^ { $ne.setType(NE); }
		)
      relationalExpression)?
	;

// level 4 - LT, GT, LE, GE, LIKE, NOT LIKE, BETWEEN, NOT BETWEEN
// NOTE: The NOT prefix for LIKE and BETWEEN will be represented in the
// token type.  When traversing the AST, use the token type, and not the
// token text to interpret the semantics of these nodes.


relationalExpression
	: valueExpression (
		( ( (LT|GT|LE|GE)^ additiveExpression )? )
		| (n=NOT!)? (
			// Represent the optional NOT prefix using the token type by
			// testing 'n' and setting the token type accordingly.
			(i=IN^ {
					$i.setType( ($n == null) ? IN : NOT_IN);
					$i.setText( ($n == null) ? "in" : "not in");
				}
				inList)
			| (b=BETWEEN^ {
					$b.setType( ($n == null) ? BETWEEN : NOT_BETWEEN);
					$b.setText( ($n == null) ? "between" : "not between");
				}
				betweenList )
			| (l=LIKE^ {
					$l.setType( ($n == null) ? LIKE : NOT_LIKE);
					$l.setText( ($n == null) ? "like" : "not like");
				}
				valueExpression likeEscape)
            )
		)
	;


likeEscape
	: (ESCAPE^ valueExpression)?
	;


inList
	: compoundExpr -> ^(IN_LIST compoundExpr)
	;


betweenList
	: valueExpression AND! valueExpression
	;

valueExpression : concatenation ;

concatenation
	: bitwiseOrExpression ( CONCAT^ bitwiseOrExpression )*
	;

bitwiseOrExpression
        : bitwiseXorExpression ( BIT_OR^ bitwiseXorExpression ) *
        ;

bitwiseXorExpression
        : bitwiseAndExpression ( BIT_XOR^ bitwiseAndExpression ) *
        ;

bitwiseAndExpression
        : additiveExpression ( BIT_AND^ additiveExpression ) *
        ;

additiveExpression
	: multiplyExpression ( ( PLUS^ | MINUS^ ) multiplyExpression )*
	;

multiplyExpression
	: unaryExpression ( ( STAR^ | DIV^ | MODULO^) unaryExpression )*
	;
	
unaryExpression
	: (m=MINUS^ {$m.setType(UNARY_MINUS);}) unaryExpression
	| (p=PLUS^ {$p.setType(UNARY_PLUS);}) unaryExpression
	| caseExpression                                                           
	| quantifiedExpression
	| atom
	;
	
caseExpression
	: CASE^ (whenClause)+ (elseClause)? END! 
	| (c=CASE^ { $c.setType(CASE2); }) unaryExpression (altWhenClause)+ (elseClause)? END!
	;
	
whenClause
	: (WHEN^ logicalExpression THEN! unaryExpression)
	;
	
altWhenClause
	: (WHEN^ unaryExpression THEN! unaryExpression)
	;
	
elseClause
	: (ELSE^ unaryExpression)
	;
	

quantifiedExpression
	: ( SOME^ | ALL^ | ANY^ ) (OPEN! ( subQuery ) CLOSE!)
	;


// level 0 - expression atom
// ident qualifier ('.' ident ), array index ( [ expr ] ),
atom
    : primaryExpression ( DOT^ (identifier | starAtom) )*
	;

fragment
starAtom
    : STAR -> ROW_STAR
    ;

// level 0 - the basic element of an expression
primaryExpression
	:   identPrimary
	|   constant
	|   OPEN! ( expression | subQuery) CLOSE!
	|   PARAM^ (NUM_INT)?
	;


// identifier, followed by member refs (dot ident), or method calls.
// NOTE: handleDotIdent() is called immediately after the first IDENT is recognized because
// the method looks ahead to find keywords after DOT and turns them into identifiers.
identPrimary
	: dottedIdentifier
        ( options { greedy=true; } : op=OPEN^ {$op.setType(METHOD_CALL);} exprList CLOSE! )?
    | escapeFn
	| aggregate
	| cast
// UNDONE: figure out the weakKeywords thing
	| l=LEFT  {$l.setType(IDENT);} op=OPEN^ {$op.setType(METHOD_CALL);} exprList CLOSE!
	| r=RIGHT {$r.setType(IDENT);} op=OPEN^ {$op.setType(METHOD_CALL);} exprList CLOSE!
	| IFDEFINED^ OPEN! dottedIdentifier CLOSE!
	;


dottedIdentifier
    : identifier { handleDotIdent(); }
        ( options { greedy=true; } : (DOT^ identifier) )*;


escapeFn
    : '{fn'! identifier op=OPEN^ {$op.setType(METHOD_CALL);} exprList CLOSE! '}'!
    ;


aggregate
	: (a=( SUM^ | AVG^ | MAX^ | MIN^ | STDDEV^ | STDERR^ ) {$a.setType(AGGREGATE);} OPEN! (additiveExpression) CLOSE!)
	| (a=COUNT^ {$a.setType(AGGREGATE);} OPEN! d=DISTINCT? (additiveExpression | starAtom) CLOSE!)
	| (a=GROUP_CONCAT^ {$a.setType(AGGREGATE);} OPEN! d=DISTINCT? (additiveExpression) (COMMA! primaryExpression)? CLOSE!)
	;


cast
    : (c=CAST open=OPEN expression as=AS sqltype CLOSE)
        -> ^(METHOD_CALL IDENT[$c] ^(EXPR_LIST expression sqltype))
	;


compoundExpr
	: path
	| OPEN! ( subQuery| expression (COMMA! expression)* ) CLOSE!
	;


subQuery
	: selectStatement
	;


exprList
	: exprListFragment -> ^(EXPR_LIST exprListFragment?)
 	;

exprListFragment
    : (expression (COMMA! expression)*)?
    ; 


constantExprList
	: constantExprListFragment -> ^(EXPR_LIST constantExprListFragment?)
 	;

constantExprListFragment
    : (constant (COMMA! constant)*)?
    ;


constant
	: number
	| QUOTED_STRING
	| NULL
	| TRUE
	| FALSE
    | '{d ' QUOTED_STRING '}' -> ^(DATE_LITERAL QUOTED_STRING)
    | '{ts ' QUOTED_STRING '}' -> ^(TIMESTAMP_LITERAL QUOTED_STRING)
	;


number
    : NUM_INT
    | NUM_LONG
    | NUM_DOUBLE
    | NUM_FLOAT
    ;


path
	: identifier ( DOT^ { weakKeywords(); } identifier )*
	;


// NOTE: left() and right() are functions as well as keywords
// NOTE: would be nice to get weakKeywords() working in general
identifier
	: IDENT | QUOTED_IDENTIFIER
	;


// **** LEXER ******************************************************************

/**
 * Hibernate Query Language Lexer
 * <br>
 * This lexer provides the HQL parser with tokens.
 * @author Joshua Davis (pgmjsd@sourceforge.net)
 */


EQ: '=';
LT: '<';
GT: '>';
SQL_NE: '<>';
NE: '!=';
LE: '<=';
GE: '>=';

COMMA: ',';

OPEN: '(';
CLOSE: ')';

CONCAT: '||';
PLUS: '+';
MINUS: '-';
STAR: '*';
DIV: '/';
MODULO: '%';
COLON: ':';
PARAM: '?';
BIT_OR: '|';
BIT_XOR: '^';
BIT_AND: '&';


ANNOTATION_LABEL
    : '@' ID_START_LETTER ( ID_LETTER )*
    ;


IDENT
	: ID_START_LETTER ( ID_LETTER )*
		{
    		// Setting this flag allows the grammar to use keywords as identifiers, if necessary.
			setPossibleID(true);
		}
	;

fragment
ID_START_LETTER
    :    '_'
    |    '$'
    |    'a'..'z'
    |    'A'..'Z'
    |    '\u0080'..'\ufffe'       // HHH-558 : Allow unicode chars in identifiers
    ;


fragment
ID_LETTER
    :    ID_START_LETTER
    |    '0'..'9'
    ;


QUOTED_STRING
	: '\'' (~'\'' | '\'\'')* '\''
	;


QUOTED_IDENTIFIER 
	: '"' (~'"' | '""')* '"'
	;


WS 
	: (' ' | '\t' | '\r' | '\n')+ {skip();}
	;

//--- From the Java example grammar ---

fragment NUM_LONG : ;
fragment NUM_DOUBLE : ;
fragment NUM_FLOAT : ;

// a numeric literal
NUM_INT
	@init {boolean isDecimal=false; Token t=null;}
	:   '.' {_type = DOT;}
			(	('0'..'9')+ (EXPONENT)? (f1=FLOAT_SUFFIX {t=f1;})?
				{
					if (t != null && t.getText().toUpperCase().indexOf('F')>=0)
					{
						_type = NUM_FLOAT;
					}
					else
					{
						_type = NUM_DOUBLE; // assume double
					}
				}
			)?
	|	(	'0' {isDecimal = true;} // special case for just '0'
			(	('x')
				(											// hex
				:	HEX_DIGIT
				)+
			|	('0'..'7')+									// octal
			)?
		|	('1'..'9') ('0'..'9')*  {isDecimal=true;}		// non-zero decimal
		)
		(	('l') { _type = NUM_LONG; }

		// only check to see if it's a float if looks like decimal so far
		|	{isDecimal}?
			(   '.' ('0'..'9')* (EXPONENT)? (f2=FLOAT_SUFFIX {t=f2;})?
			|   EXPONENT (f3=FLOAT_SUFFIX {t=f3;})?
			|   f4=FLOAT_SUFFIX {t=f4;}
			)
			{
				if (t != null && t.getText().toUpperCase() .indexOf('F') >= 0)
				{
					_type = NUM_FLOAT;
				}
				else
				{
					_type = NUM_DOUBLE; // assume double
				}
			}
		)?
	;

// hexadecimal digit (again, note it's protected!)
fragment
HEX_DIGIT
	:	('0'..'9'|'a'..'f'|'A'..'F')
	;

// a couple protected methods to assist in matching floating point numbers
fragment
EXPONENT
	:	('e') ('+'|'-')? ('0'..'9')+
	;

fragment
FLOAT_SUFFIX
	:	'f'|'d'
	;

COMMENT
    :   '/*' (options {greedy=false;} : . )* '*/' {skip();}
    ;
    
LINE_COMMENT
    : '--' (~('\r'|'\n'))* ('\r\n' | '\n' | '\r')? {skip();}
    ;
