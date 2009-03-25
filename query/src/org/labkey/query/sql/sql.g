header
{
package org.labkey.query.sql.antlr;
}

/**
 * SQL language grammar.
 * This source code was taken from the Hibernate project ("hql.g").
 *
 */
class SqlBaseParser extends Parser;

options
{
	exportVocab=SqlBase;
	buildAST=true;
	k=3;    // For 'not like', 'not in', etc.
}

tokens
{
	// -- SQL Keyword tokens --
	ALL="all";
	ANY="any";
	AND="and";
	AS="as";
	ASCENDING="asc";
	AVG="avg";
	BETWEEN="between";
	CLASS="class";
	COUNT="count";
	DELETE="delete";
	DESCENDING="desc";
	DOT;
	DISTINCT="distinct";
	ELEMENTS="elements";
	ESCAPE="escape";
	EXISTS="exists";
	FALSE="false";
	FETCH="fetch";
	FROM="from";
	FULL="full";
	GROUP="group";
	HAVING="having";
	IN="in";
	INDICES="indices";
	INNER="inner";
	INSERT="insert";
	INTO="into";
	IS="is";
	JOIN="join";
	LEFT="left";
	LIKE="like";
	LIMIT="limit";
	MAX="max";
	MIN="min";
	NEW="new";
	NOT="not";
	NULL="null";
	OR="or";
	ORDER="order";
	OUTER="outer";
	RIGHT="right";
	SELECT="select";
	SET="set";
	SOME="some";
	SUM="sum";
	TRUE="true";
	UNION="union";
	UPDATE="update";
	VERSIONED="versioned";
	WHERE="where";

	// -- SQL tokens --
	CASE="case";
	END="end";
	ELSE="else";
	THEN="then";
	WHEN="when";
	ON="on";

	// -- EJBQL tokens --
	BOTH="both";
	EMPTY="empty";
	LEADING="leading";
	MEMBER="member";
	OF="of";
	TRAILING="trailing";
	STDDEV="stddev";

	// -- Synthetic token types --
	AGGREGATE;		// One of the aggregate functions (e.g. min, max, avg)
	ALIAS;
	CASE2;
	EXPR_LIST;
	FILTER_ENTITY;		// FROM element injected because of a filter expression (happens during compilation phase 2)
	IN_LIST;
	IS_NOT;
	METHOD_CALL;
	NOT_BETWEEN;
	NOT_IN;
	NOT_LIKE;
	ORDER_ELEMENT;
	QUERY;
	RANGE;
	ROW_STAR;
	SELECT_FROM;
	UNARY_MINUS;
	UNARY_PLUS;
	UNION_ALL;
	VECTOR_EXPR;		// ( x, y, z )
	WEIRD_IDENT;		// Identifiers that were keywords when they came in.

	// Literal tokens.
	CONSTANT;
	NUM_DOUBLE;
	NUM_FLOAT;
	NUM_LONG;
}

{
    /** True if this is a filter query (allow no FROM clause). **/
	private boolean filter = false;

	/**
	 * Sets the filter flag.
	 * @param f True for a filter query, false for a normal query.
	 */
	public void setFilter(boolean f) {
		filter = f;
	}

	/**
	 * Returns true if this is a filter query, false if not.
	 * @return true if this is a filter query, false if not.
	 */
	public boolean isFilter() {
		return filter;
	}

	/**
	 * This method is overriden in the sub class in order to provide the
	 * 'keyword as identifier' hack.
	 * @param token The token to retry as an identifier.
	 * @param ex The exception to throw if it cannot be retried as an identifier.
	 */
	public AST handleIdentifierError(Token token,RecognitionException ex) throws RecognitionException, TokenStreamException {
		// Base implementation: Just re-throw the exception.
		throw ex;
	}

    /**
     * This method looks ahead and converts . <token> into . IDENT when
     * appropriate.
     */
    public void handleDotIdent() throws TokenStreamException {
    }

	/**
	 * Returns the 'cleaned up' version of a comparison operator sub-tree.
	 * @param x The comparison operator to clean up.
	 */
	public AST processEqualityExpression(AST x) throws RecognitionException {
		return x;
	}

	public void weakKeywords() throws TokenStreamException { }

	public void processMemberOf(Token n,AST p,ASTPair currentAST) { }
}

statement
	: ( updateStatement | deleteStatement | selectStatement | insertStatement )
	;

updateStatement
	: UPDATE^ (VERSIONED)?
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

// "state_field" is the term used in the EJB3 sample grammar; used here for easy reference.
// it is basically a property ref
stateField
	: path
	;

// this still needs to be defined in the ejb3 spec; additiveExpression is currently just a best guess,
// although it is highly likely I would think that the spec may limit this even more tightly.
newValue
	: concatenation
	;

deleteStatement
	: DELETE^
		(optionalFromTokenFromClause)
		(whereClause)?
	;

optionalFromTokenFromClause!
	: (FROM!)? f:path (a:asAlias)? {
		AST #range = #([RANGE, "RANGE"], #f, #a);
		#optionalFromTokenFromClause = #([FROM, "FROM"], #range);
	}
	;

selectStatement
	: q:union
		((o:orderByClause! {#q.addChild(#o);})?)
		((l:limitClause! {#q.addChild(#l);})?)
	;

insertStatement
	// Would be nice if we could abstract the FromClause/FromElement logic
	// out such that it could be reused here; something analogous to
	// a "table" rule in sql-grammars
	: INSERT^ intoClause select
	;

intoClause
	: INTO^ path { weakKeywords(); } insertablePropertySpec
	;

insertablePropertySpec
	: OPEN! primaryExpression ( COMMA! primaryExpression )* CLOSE! {
		// Just need *something* to distinguish this on the hql-sql.g side
		#insertablePropertySpec = #([RANGE, "column-spec"], #insertablePropertySpec);
	}
	;


union
  : unionTerm (u:UNION^ (ALL! { #u.setType(UNION_ALL); } )? unionTerm)*
  ;


unionTerm
  : select
  | OPEN! union CLOSE!
  ;


select
	: selectFrom (whereClause)? (groupByClause (havingClause)?)? 
		{
			#select = #([QUERY,"query"], #select);
		}
    ;


selectFrom!
	:  (s:selectClause)? (f:fromClause)? {
		// If there was no FROM clause and this is a filter query, create a from clause.  Otherwise, throw
		// an exception because non-filter queries must have a FROM clause.
		if (#f == null) {
			if (filter) {
				#f = #([FROM,"{filter-implied FROM}"]);
			}
			else
				throw new SemanticException("FROM expected (non-filter queries must contain a FROM clause)");
		}

		// Create an artificial token so the 'FROM' can be placed
		// before the SELECT in the tree to make tree processing
		// simpler.
		#selectFrom = #([SELECT_FROM,"SELECT_FROM"],f,s);
	}
	;


selectClause
	: SELECT^ { weakKeywords(); }	// Weak keywords can appear immediately after a SELECT token.
		(DISTINCT)? ( selectedPropertiesList )
	;


// NOTE: This *must* begin with the "FROM" token, otherwise the sub-query rule will be ambiguous
// with the expression rule.
// Also note: after a comma weak keywords are allowed and should be treated as identifiers.

fromClause
	: FROM^ { weakKeywords(); } fromRange ( fromJoin | COMMA! { weakKeywords(); } fromRange )*
	;

fromJoin
	: ( ( (LEFT|RIGHT|FULL) (OUTER!)? )  | INNER )? JOIN^ (FETCH)?
	(( path (asAlias)?) |
	((OPEN! ( q:subQuery ) CLOSE!) (a:asAlias))) (onClause)?
	;

onClause
	: ON^ logicalExpression
	;

fromRange
	: fromClassOrOuterQueryPath
	| inClassDeclaration
	| inCollectionDeclaration
	| inCollectionElementsDeclaration
	| aliasedSubQuery
	;

fromClassOrOuterQueryPath!
	: c:path { weakKeywords(); } (a:asAlias)? {
		#fromClassOrOuterQueryPath = #([RANGE, "RANGE"], #c, #a);
	}
	;

aliasedSubQuery!
    :
    (OPEN! ( q:subQuery ) CLOSE!) (a:asAlias)
    {
        #aliasedSubQuery = #([RANGE, "RANGE"], #q, #a);
    }
    ;


inClassDeclaration!
	: a:alias IN! CLASS! c:path {
		#inClassDeclaration = #([RANGE, "RANGE"], #c, #a);
	}
	;

inCollectionDeclaration!
    : IN! OPEN! p:path CLOSE! a:alias {
        #inCollectionDeclaration = #([JOIN, "join"], [INNER, "inner"], #p, #a);
	}
    ;

inCollectionElementsDeclaration!
	: a:alias IN! ELEMENTS! OPEN! p:path CLOSE! {
        #inCollectionElementsDeclaration = #([JOIN, "join"], [INNER, "inner"], #p, #a);
	}
    ;

// Alias rule - Parses the optional 'as' token and forces an AST identifier node.
asAlias
	: (AS!)? alias
	;

alias
	: identifier
    ;

//## groupByClause:
//##     GROUP_BY path ( COMMA path )*;

groupByClause
	: GROUP^ "by"! expression ( COMMA! expression )*
	;

//## orderByClause:
//##     ORDER_BY selectedPropertiesList;

orderByClause
	: ORDER^ "by"! orderElement ( COMMA! orderElement )*
	;

limitClause
    : LIMIT^ NUM_INT;

orderElement
	: expression ( ascendingOrDescending )?
	;

ascendingOrDescending
	: ( "asc" | "ascending" )	{ #ascendingOrDescending.setType(ASCENDING); }
	| ( "desc" | "descending") 	{ #ascendingOrDescending.setType(DESCENDING); }
	;

havingClause
	: HAVING^ logicalExpression
	;

whereClause
	: WHERE^ logicalExpression
	;

selectedPropertiesList
	: aliasedExpression ( COMMA! aliasedExpression )*
	;

aliasedExpression
	: (expression ( AS^ identifier )?) |
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
// is usually very straightfoward

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

// NOT nodes aren't generated.  Instead, the operator in the sub-tree will be
// negated, if possible.   Expressions without a NOT parent are passed through.
negatedExpression
{ weakKeywords(); } // Weak keywords can appear in an expression, so look ahead.
	: NOT^ negatedExpression
	| bitwiseOrExpression
	;

bitwiseOrExpression
    : equalityExpression ( (BIT_OR^ | BIT_XOR) equalityExpression ) *
    ;

//## OP: EQ | LT | GT | LE | GE | NE | SQL_NE | LIKE;

// level 5 - EQ, NE
equalityExpression
	: x:relationalExpression (
		( EQ^
		| is:IS^ (NOT! { #is.setType(IS_NOT); } )?
		| NE^
		| ne:SQL_NE^	{ #ne.setType(NE); }
		) y:relationalExpression)* {
			// Post process the equality expression to clean up 'is null', etc.
			#equalityExpression = processEqualityExpression(#equalityExpression);
		}
	;

// level 4 - LT, GT, LE, GE, LIKE, NOT LIKE, BETWEEN, NOT BETWEEN
// NOTE: The NOT prefix for LIKE and BETWEEN will be represented in the
// token type.  When traversing the AST, use the token type, and not the
// token text to interpret the semantics of these nodes.
relationalExpression
	: concatenation (
		( ( ( LT^ | GT^ | LE^ | GE^ ) additiveExpression )* )
		// Disable node production for the optional 'not'.
		| (n:NOT!)? (
			// Represent the optional NOT prefix using the token type by
			// testing 'n' and setting the token type accordingly.
			(i:IN^ {
					#i.setType( (n == null) ? IN : NOT_IN);
					#i.setText( (n == null) ? "in" : "not in");
				}
				inList)
			| (b:BETWEEN^ {
					#b.setType( (n == null) ? BETWEEN : NOT_BETWEEN);
					#b.setText( (n == null) ? "between" : "not between");
				}
				betweenList )
			| (l:LIKE^ {
					#l.setType( (n == null) ? LIKE : NOT_LIKE);
					#l.setText( (n == null) ? "like" : "not like");
				}
				concatenation likeEscape)
			| (MEMBER! OF! p:path! {
				processMemberOf(n,#p,currentAST);
			  } ) )
		)
	;

likeEscape
	: (ESCAPE^ concatenation)?
	;

inList
	: OPEN CLOSE {if (1==1) throw new RecognitionException("IN expression is empty");}
	| x:compoundExpr { #inList = #([IN_LIST,"inList"], #inList); }
	;

betweenList
	: concatenation AND! concatenation
	;

//level 4 - string concatenation
concatenation
	: additiveExpression ( CONCAT^ additiveExpression )*
	;

// level 3 - binary plus and minus
additiveExpression
	: multiplyExpression ( ( PLUS^ | MINUS^ | BIT_AND^ ) multiplyExpression )*
	;

// level 2 - binary multiply and divide
multiplyExpression
	: unaryExpression ( ( STAR^ | DIV^ ) unaryExpression )*
	;
	
// level 1 - unary minus, unary plus, not
unaryExpression
	: MINUS^ {#MINUS.setType(UNARY_MINUS);} unaryExpression
	| PLUS^ {#PLUS.setType(UNARY_PLUS);} unaryExpression
	| caseExpression                                                           
	| quantifiedExpression
	| atom
	;
	
caseExpression
	: CASE^ (whenClause)+ (elseClause)? END! 
	| CASE^ { #CASE.setType(CASE2); } unaryExpression (altWhenClause)+ (elseClause)? END!
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
	: ( SOME^ | EXISTS^ | ALL^ | ANY^ ) 
	( identifier | collectionExpr | (OPEN! ( subQuery ) CLOSE!) )
	;


// level 0 - expression atom
// ident qualifier ('.' ident ), array index ( [ expr ] ),
atom
	 : primaryExpression
		(
			DOT^ (identifier | STAR { #STAR.setType(ROW_STAR); } ) 
		)*
	;

// level 0 - the basic element of an expression
primaryExpression
	:   identPrimary ( options {greedy=true;} : DOT^ "class" )?
	|   constant
	|   COLON^ identifier
	// TODO: Add parens to the tree so the user can control the operator evaluation order.
	// TODO LABKEY: 'expressionOrVector' is not supported, just use 'expression'
	|   OPEN! (expression | subQuery) CLOSE!
	|   PARAM^ (NUM_INT)?
	;

// This parses normal expression and a list of expressions separated by commas.  If a comma is encountered
// a parent VECTOR_EXPR node will be created for the list.
expressionOrVector!
	: e:expression ( v:vectorExpr )? {
		// If this is a vector expression, create a parent node for it.
		if (#v != null)
			#expressionOrVector = #([VECTOR_EXPR,"{vector}"], #e, #v);
		else
			#expressionOrVector = #e;
	}
	;

vectorExpr
	: COMMA! expression (COMMA! expression)*
	;

// identifier, followed by member refs (dot ident), or method calls.
// NOTE: handleDotIdent() is called immediately after the first IDENT is recognized because
// the method looks a head to find keywords after DOT and turns them into identifiers.
identPrimary
	: identifier { handleDotIdent(); }
			( options { greedy=true; } : DOT^ ( identifier | ELEMENTS | o:OBJECT { #o.setType(IDENT); } ) )*
			( options { greedy=true; } :
				( op:OPEN^ { #op.setType(METHOD_CALL);} exprList CLOSE! )
			)?
	// Also allow special 'aggregate functions' such as count(), avg(), etc.
	| aggregate
	;


aggregate
	: ( SUM^ | AVG^ | MAX^ | MIN^ | STDDEV^ | COUNT^) OPEN! additiveExpression CLOSE! { #aggregate.setType(AGGREGATE); }
	;


collectionExpr
	: (ELEMENTS^ | INDICES^) OPEN! path CLOSE!
	;
                                           

// NOTE: compoundExpr can be a 'path' where the last token in the path is '.elements' or '.indicies'
compoundExpr
	: collectionExpr
	| path
	| (OPEN! ( (expression (COMMA! expression)*) | subQuery ) CLOSE!)
	;


subQuery
	: union
	;


exprList
{
   AST trimSpec = null;
}
	: (t:TRAILING {#trimSpec = #t;} | l:LEADING {#trimSpec = #l;} | b:BOTH {#trimSpec = #b;})?
	  		{ if(#trimSpec != null) #trimSpec.setType(IDENT); }
	  ( 
	  		expression ( (COMMA! expression)+ | FROM { #FROM.setType(IDENT); } expression | AS! identifier )? 
	  		| FROM { #FROM.setType(IDENT); } expression
	  )?
			{ #exprList = #([EXPR_LIST,"exprList"], #exprList); }
	;

constant
	: NUM_INT
	| NUM_FLOAT
	| NUM_LONG
	| NUM_DOUBLE
	| QUOTED_STRING
	| NULL
	| TRUE
	| FALSE
	| EMPTY
	;


path
	: identifier ( DOT^ { weakKeywords(); } identifier )* |


	;

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
class SqlBaseLexer extends Lexer;

options {
	exportVocab=SqlBase;      // call the vocabulary "Sql"
	testLiterals = false;
	k=2; // needed for newline, and to distinguish '>' from '>='.
	// HHH-241 : Quoted strings don't allow unicode chars - This should fix it.
	charVocabulary='\u0000'..'\uFFFE';	// Allow any char but \uFFFF (16 bit -1, ANTLR's EOF character)
	caseSensitive = false;
	caseSensitiveLiterals = false;
}

// -- Declarations --
{
	// NOTE: The real implementations are in the subclass.
	protected void setPossibleID(boolean possibleID) {}
}

// -- Keywords --

EQ: '=';
LT: '<';
GT: '>';
SQL_NE: "<>";
NE: "!=";
LE: "<=";
GE: ">=";

COMMA: ',';

OPEN: '(';
CLOSE: ')';

CONCAT: "||";
PLUS: '+';
MINUS: '-';
STAR: '*';
DIV: '/';
COLON: ':';
PARAM: '?';
BIT_OR: "|";
BIT_XOR: "^";
BIT_AND: "&";

IDENT options { testLiterals=true; }
	: ID_START_LETTER ( ID_LETTER )*
		{
    		// Setting this flag allows the grammar to use keywords as identifiers, if necessary.
			setPossibleID(true);
		}
	;

protected
ID_START_LETTER
    :    '_'
    |    '$'
    |    'a'..'z'
    |    '\u0080'..'\ufffe'       // HHH-558 : Allow unicode chars in identifiers
    ;

protected
ID_LETTER
    :    ID_START_LETTER
    |    '0'..'9'
    ;

QUOTED_STRING
	  : '\'' ( (ESCqs)=> ESCqs | ~'\'' )* '\''
	;

QUOTED_IDENTIFIER
      : '\"' ( (ESCqi)=> ESCqi | ~'\"')* '\"'
    ;

protected
ESCqs
	:
		'\'' '\''
	;

protected
ESCqi
    :
        '\"' '\"'
    ;


WS  :   (   ' '
		|   '\t'
		|   NL { newline(); }
		)
		{$setType(Token.SKIP);} //ignore this token
	;


NL
    :   '\r' '\n'
    |   '\n'
    |   '\r'
    ;


//--- From the Java example grammar ---
// a numeric literal
NUM_INT
	{boolean isDecimal=false; Token t=null;}
	:   '.' {_ttype = DOT;}
			(	('0'..'9')+ (EXPONENT)? (f1:FLOAT_SUFFIX {t=f1;})?
				{
					if (t != null && t.getText().toUpperCase().indexOf('F')>=0)
					{
						_ttype = NUM_FLOAT;
					}
					else
					{
						_ttype = NUM_DOUBLE; // assume double
					}
				}
			)?
	|	(	'0' {isDecimal = true;} // special case for just '0'
			(	('x')
				(											// hex
					// the 'e'|'E' and float suffix stuff look
					// like hex digits, hence the (...)+ doesn't
					// know when to stop: ambig.  ANTLR resolves
					// it correctly by matching immediately.  It
					// is therefore ok to hush warning.
					options { warnWhenFollowAmbig=false; }
				:	HEX_DIGIT
				)+
			|	('0'..'7')+									// octal
			)?
		|	('1'..'9') ('0'..'9')*  {isDecimal=true;}		// non-zero decimal
		)
		(	('l') { _ttype = NUM_LONG; }

		// only check to see if it's a float if looks like decimal so far
		|	{isDecimal}?
			(   '.' ('0'..'9')* (EXPONENT)? (f2:FLOAT_SUFFIX {t=f2;})?
			|   EXPONENT (f3:FLOAT_SUFFIX {t=f3;})?
			|   f4:FLOAT_SUFFIX {t=f4;}
			)
			{
				if (t != null && t.getText().toUpperCase() .indexOf('F') >= 0)
				{
					_ttype = NUM_FLOAT;
				}
				else
				{
					_ttype = NUM_DOUBLE; // assume double
				}
			}
		)?
	;

// hexadecimal digit (again, note it's protected!)
protected
HEX_DIGIT
	:	('0'..'9'|'a'..'f')
	;

// a couple protected methods to assist in matching floating point numbers
protected
EXPONENT
	:	('e') ('+'|'-')? ('0'..'9')+
	;

protected
FLOAT_SUFFIX
	:	'f'|'d'
	;

COMMENT
    :   "/*" (options {greedy=false;} : . )* "*/" {$setType(Token.SKIP);}
    ;
    
LINE_COMMENT
    : "--" (~('\r'|'\n'))* (NL)? {$setType(Token.SKIP);}
    ;