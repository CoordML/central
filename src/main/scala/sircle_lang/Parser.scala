package sircle_lang

import OpType.{Associativity, LeftAssoc, OpType, RightAssoc}
import PrefixType.PrefixType
import TokenType.TokenType

class Parser(val tokens: List[Token]) {
  val opRules: Array[(Associativity, Map[TokenType, OpType])] = Array(
    (RightAssoc, Map(
      TokenType.DOLLAR -> OpType.DOLLAR
    )),
    //    (RightAssoc, Map(
    //      TokenType.EQ_GT -> OpType.MAPS_TO
    //    )),
    (LeftAssoc, Map(
      TokenType.AND -> OpType.AND,
      TokenType.OR -> OpType.OR
    )),
    (LeftAssoc, Map(
      TokenType.GT -> OpType.GT,
      TokenType.LT -> OpType.LT,
      TokenType.GT_EQ -> OpType.GE,
      TokenType.LT_EQ -> OpType.LE,
      TokenType.EQ_EQ -> OpType.EQ,
      TokenType.BANG_EQ -> OpType.NEQ
    )),
    (LeftAssoc, Map(
      TokenType.PLUS -> OpType.PLUS,
      TokenType.MINUS -> OpType.MINUS
    )),
    (LeftAssoc, Map(
      TokenType.ASTERISK -> OpType.MUL,
      TokenType.SLASH -> OpType.DIV
    )),
    (LeftAssoc, Map(
      TokenType.IN -> OpType.IN
    ))
  )

  var current: List[Token] = tokens

  def eof: Boolean = current match {
    case Nil => true
    case x :: _ if x.tokenType == TokenType.EOF => true
    case _ => false
  }

  def peek: Token = current.head

  def advance: Token = {
    val token = peek
    if (!eof) current = current.tail
    token
  }

  def matchAhead(tokenType: TokenType): Boolean =
    if (peek.tokenType == tokenType) {
      advance
      true
    } else {
      false
    }

  def expect[A](tokenType: TokenType, func: Token => A): A = {
    val token = peek
    if (matchAhead(tokenType))
      func(token)
    else
      throw ParseError(s"Expected $tokenType but see ${peek.tokenType}")
  }

  def lookAhead(tokenTypes: List[TokenType]): Boolean =
    tokenTypes contains peek.tokenType

  def lookForward(tokenTypes: List[TokenType], i: Int = 0): Boolean = tokenTypes match {
    case Nil => true
    case _ if i >= current.length => false
    case x :: xs => current(i).tokenType == x && lookForward(xs, i + 1)
  }


  val unaryRules: Map[TokenType, PrefixType] = Map(
    TokenType.NOT -> PrefixType.NOT,
    TokenType.MINUS -> PrefixType.NEG
  )

  def parseExpr: Expr = parseBinary(0)

  def parseBinary(level: Int): Expr = {
    if (level == opRules.length) {
      parseUnary
    } else {
      val (assoc, rules) = opRules(level)

      assoc match {
        case LeftAssoc =>
          var expr = parseBinary(level + 1)
          var found = true
          while (found) {
            rules get peek.tokenType match {
              case None => found = false
              case Some(op) =>
                advance
                expr = ExprBinary(expr, op, parseBinary(level + 1))
            }
          }
          expr
        case RightAssoc =>
          val expr = parseBinary(level + 1)
          rules get peek.tokenType match {
            case None => expr
            case Some(op) =>
              advance
              ExprBinary(expr, op, parseBinary(level))
          }
      }
    }
  }

  def parseUnary: Expr =
    unaryRules get peek.tokenType match {
      case None => parseApp
      case Some(op) =>
        advance
        ExprUnary(op, parseApp)
    }

  def parseApp: Expr = {
    val first = List(
      TokenType.LEFT_PAREN,
      TokenType.STRING,
      TokenType.INT,
      TokenType.DOUBLE,
      TokenType.UNIT,
      TokenType.BOOLEAN,
      TokenType.IDENTIFIER,
      TokenType.LEFT_BRACKET,
    )

    var expr = parseTerm
    var found = true

    while (found)
      if (lookAhead(first)) {
        expr = ExprApp(expr, parseTerm)
      } else {
        found = false
      }

    expr
  }

  def parseTerm: Expr = {
    val token = advance
    token.tokenType match {
      case TokenType.LEFT_PAREN =>
        val xs = parseExprList(TokenType.RIGHT_PAREN)
        xs match {
          case Nil => ExprValue(ValUnit)
          case expr :: Nil => expr
          case _ => ExprTuple(xs)
        }
      case TokenType.LEFT_BRACKET =>
        parseList match {
          case Nil => ExprValue(ValList(AnyType, Nil))
          case x => ExprList(x)
        }
      case TokenType.LEFT_BRACE =>
        val xs = parseBindingList(TokenType.RIGHT_BRACE, TokenType.SEMI_COLON)
        ExprBlock(xs)
      case TokenType.KW_IF => parseIf
      case TokenType.INT => ExprValue(ValInt(token.lexeme.asInstanceOf[Int]))
      case TokenType.DOUBLE => ExprValue(ValDouble(token.lexeme.asInstanceOf[Double]))
      case TokenType.STRING => ExprValue(ValString(token.lexeme.asInstanceOf[String]))
      case TokenType.UNIT => ExprValue(ValUnit)
      case TokenType.BOOLEAN => ExprValue(ValBoolean(token.lexeme.asInstanceOf[Boolean]))
      case TokenType.IDENTIFIER =>
        if (matchAhead(TokenType.EQ_GT)) {
          ExprLambda(token.content, TypeExprIdentifier("Any"), parseExpr)
        }
        else if (matchAhead(TokenType.COLON)) {
          val typeExpr = parseTypeExpr
          if (matchAhead(TokenType.EQ_GT)) {
            ExprLambda(token.content, typeExpr, parseExpr)
          } else {
            throw ParseError(s"Unexpected token $peek when parsing a lambda expression.")
          }
        }
        else {
          ExprIdentifier(token.content)
        }
      case _ =>
        throw ParseError(s"Unexpected token: $token")
    }
  }

  def parseIf: Expr = {
    val cond = parseExpr
    expect(TokenType.KW_THEN, { _ =>
      val left = parseExpr
      if (matchAhead(TokenType.KW_ELSE)) {
        val right = parseExpr
        ExprIf(cond, left, Some(right))
      } else ExprIf(cond, left, None)
    })
  }

  def parseBindingList(endToken: TokenType, sepToken: TokenType = TokenType.SEMI_COLON): List[Binding] =
    if (matchAhead(endToken)) {
      Nil
    } else {
      val binding = parseBinding
      if (matchAhead(sepToken)) {
        binding :: parseBindingList(endToken, sepToken)
      } else if (matchAhead(endToken))
        binding :: Nil
      else {
        throw ParseError(s"Expecting pairing token $endToken, but found ${peek.tokenType}")
      }
    }

  def parseExprList(endToken: TokenType, sepToken: TokenType = TokenType.COMMA): List[Expr] =
    if (matchAhead(endToken)) {
      Nil
    } else {
      val expr = parseExpr
      if (matchAhead(sepToken)) {
        expr :: parseExprList(endToken)
      } else if (matchAhead(endToken))
        expr :: Nil
      else {
        throw ParseError(s"Expecting pairing token $endToken.")
      }
    }

  def parseList: List[Expr] = parseExprList(TokenType.RIGHT_BRACKET)

  def parseTypeExpr: TypeExpr = {
    val expr = parseTypeTerm
    if (matchAhead(TokenType.RIGHT_ARROW))
      TypeExprArrow(expr, parseTypeExpr)
    else
      expr
  }

  def parseTypeTerm: TypeExpr = {
    val token = advance
    token.tokenType match {
      case TokenType.IDENTIFIER =>
        TypeExprIdentifier(token.content)
      case TokenType.LEFT_PAREN =>
        val expr = parseTypeExpr
        if (matchAhead(TokenType.RIGHT_PAREN))
          expr
        else
          throw ParseError(s"Unmatched paren when parsing type expressions.")
      case TokenType.LEFT_BRACKET =>
        val expr = parseTypeExpr
        if (matchAhead(TokenType.RIGHT_BRACKET))
          TypeExprList(expr)
        else
          throw ParseError(s"Unmatched list bracket when parsing type expressions.")
      case _ =>
        throw ParseError(s"Invalid type expr at token $peek")
    }
  }

  def parseBinding: Binding =
    if (matchAhead(TokenType.KW_DEF))
      parseValBinding
    else if (lookForward(List(TokenType.IDENTIFIER, TokenType.EQ)))
      parseRebinding
    else
      ExprBinding(parseExpr)


  def parseRebinding: Binding =
    expect(TokenType.IDENTIFIER, {token =>
      val name = token.content
      advance
      ReBinding(name, parseExpr)
    })


  def parseValBinding: Binding =
    expect(TokenType.IDENTIFIER, { token =>
      val name = token.content
      val valType = if (peek.tokenType == TokenType.COLON) {
        advance
        parseTypeExpr
      } else TypeExprIdentifier("Any")
      expect(TokenType.EQ, { _ =>
        val expr = parseExpr
        ValBinding(name, valType, expr)
      })
    })
}
