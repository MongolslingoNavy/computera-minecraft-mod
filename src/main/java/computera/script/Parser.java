package computera.script;

import java.util.ArrayList;
import java.util.List;

final class Parser {
    private final List<Token> tokens;
    private final String filename;
    private int index;

    Parser(List<Token> tokens, String filename) {
        this.tokens = tokens;
        this.filename = filename;
    }

    Ast.Program parse() throws ScriptCompileException {
        List<Ast.Stmt> body = new ArrayList<>();
        while (!check(TokenType.EOF)) {
            if (match(TokenType.NEWLINE)) {
                continue;
            }
            if (check(TokenType.INDENT)) {
                throw error(peek(), "unexpected indent");
            }
            if (check(TokenType.DEDENT)) {
                throw error(peek(), "unexpected dedent");
            }
            body.add(parseStatement());
        }
        return new Ast.Program(body, 1);
    }

    private Ast.Stmt parseStatement() throws ScriptCompileException {
        Token token = peek();
        if (token.type == TokenType.NAME) {
            switch (token.text) {
                case "if":
                    return parseIf();
                case "while":
                    return parseWhile();
                case "for":
                    return parseFor();
                case "def":
                    return parseDef();
                case "return":
                    return parseReturn();
                case "break": {
                    advance();
                    expect(TokenType.NEWLINE, "end of line after 'break'");
                    return new Ast.BreakStmt(token.line, token.column);
                }
                case "continue": {
                    advance();
                    expect(TokenType.NEWLINE, "end of line after 'continue'");
                    return new Ast.ContinueStmt(token.line, token.column);
                }
                case "import": {
                    advance();
                    if (check(TokenType.NAME)) {
                        advance();
                    }
                    throw error(token, "import is not supported in script v0.1");
                }
                case "elif":
                case "else":
                    throw error(token, "'" + token.text + "' without a matching 'if'");
                default:
                    break;
            }
        }
        return parseSimpleStatement();
    }

    private Ast.Stmt parseSimpleStatement() throws ScriptCompileException {
        Token start = peek();
        Ast.Expr first = parseExpression();

        Token assignToken = peek();
        if (match(TokenType.ASSIGN)) {
            Ast.Expr value = parseExpression();
            if (check(TokenType.ASSIGN) || check(TokenType.PLUS_ASSIGN)
                    || check(TokenType.MINUS_ASSIGN) || check(TokenType.STAR_ASSIGN)
                    || check(TokenType.SLASH_ASSIGN)) {
                throw error(peek(), "chained assignment is not supported");
            }
            validateTarget(first, assignToken, false);
            expect(TokenType.NEWLINE, "end of line");
            return new Ast.Assign(first, value, start.line, start.column);
        }

        Ast.BinOp augOp = null;
        if (match(TokenType.PLUS_ASSIGN)) {
            augOp = Ast.BinOp.ADD;
        } else if (match(TokenType.MINUS_ASSIGN)) {
            augOp = Ast.BinOp.SUB;
        } else if (match(TokenType.STAR_ASSIGN)) {
            augOp = Ast.BinOp.MUL;
        } else if (match(TokenType.SLASH_ASSIGN)) {
            augOp = Ast.BinOp.DIV;
        }

        if (augOp != null) {
            if (!(first instanceof Ast.Name)) {
                throw error(assignToken, "augmented assignment target must be a variable");
            }
            Ast.Expr value = parseExpression();
            expect(TokenType.NEWLINE, "end of line");
            return new Ast.AugAssign(first, augOp, value, start.line, start.column);
        }

        expect(TokenType.NEWLINE, "end of line");
        return new Ast.ExprStmt(first, start.line, start.column);
    }

    private void validateTarget(Ast.Expr target, Token token, boolean augmented)
            throws ScriptCompileException {
        if (target instanceof Ast.Name) {
            return;
        }
        if (!augmented && target instanceof Ast.Index) {
            return;
        }
        if (augmented) {
            throw error(token, "augmented assignment target must be a variable");
        }
        throw error(token, "cannot assign to this expression");
    }

    private Ast.Stmt parseIf() throws ScriptCompileException {
        Token start = advance();
        List<Ast.Branch> branches = new ArrayList<>();

        Ast.Expr condition = parseExpression();
        expect(TokenType.COLON, "':'");
        List<Ast.Stmt> body = parseSuite();
        branches.add(new Ast.Branch(condition, body, start.line, start.column));

        while (checkName("elif")) {
            Token elif = advance();
            Ast.Expr elifCondition = parseExpression();
            expect(TokenType.COLON, "':'");
            List<Ast.Stmt> elifBody = parseSuite();
            branches.add(new Ast.Branch(elifCondition, elifBody, elif.line, elif.column));
        }

        List<Ast.Stmt> elseBody = null;
        if (checkName("else")) {
            advance();
            expect(TokenType.COLON, "':'");
            elseBody = parseSuite();
        }
        return new Ast.IfStmt(branches, elseBody, start.line, start.column);
    }

    private Ast.Stmt parseWhile() throws ScriptCompileException {
        Token start = advance();
        Ast.Expr condition = parseExpression();
        expect(TokenType.COLON, "':'");
        List<Ast.Stmt> body = parseSuite();
        return new Ast.WhileStmt(condition, body, start.line, start.column);
    }

    private Ast.Stmt parseFor() throws ScriptCompileException {
        Token start = advance();
        Token variable = expect(TokenType.NAME, "a loop variable name");
        if (!checkName("in")) {
            throw error(peek(), "expected 'in' in for loop");
        }
        advance();
        Ast.Expr iterable = parseExpression();
        expect(TokenType.COLON, "':'");
        List<Ast.Stmt> body = parseSuite();
        return new Ast.ForStmt(variable.text, iterable, body, start.line, start.column);
    }

    private Ast.Stmt parseDef() throws ScriptCompileException {
        Token start = advance();
        Token name = expect(TokenType.NAME, "a function name");
        expect(TokenType.LPAREN, "'(' after function name");
        List<String> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            while (true) {
                params.add(expect(TokenType.NAME, "a parameter name").text);
                if (!match(TokenType.COMMA)) {
                    break;
                }
                if (check(TokenType.RPAREN)) {
                    break;
                }
            }
        }
        expect(TokenType.RPAREN, "')' after parameters");
        expect(TokenType.COLON, "':'");
        List<Ast.Stmt> body = parseSuite();
        return new Ast.FunctionDef(name.text, params, body, start.line, start.column);
    }

    private Ast.Stmt parseReturn() throws ScriptCompileException {
        Token start = advance();
        Ast.Expr value = null;
        if (!check(TokenType.NEWLINE) && !check(TokenType.EOF) && !check(TokenType.DEDENT)) {
            value = parseExpression();
        }
        expect(TokenType.NEWLINE, "end of line after return");
        return new Ast.ReturnStmt(value, start.line, start.column);
    }

    private List<Ast.Stmt> parseSuite() throws ScriptCompileException {
        expect(TokenType.NEWLINE, "end of line after ':'");
        expect(TokenType.INDENT, "an indented block");
        List<Ast.Stmt> body = new ArrayList<>();
        while (!check(TokenType.DEDENT) && !check(TokenType.EOF)) {
            if (match(TokenType.NEWLINE)) {
                continue;
            }
            body.add(parseStatement());
        }
        expect(TokenType.DEDENT, "end of indented block");
        return body;
    }

    private Ast.Expr parseExpression() throws ScriptCompileException {
        return parseOr();
    }

    private Ast.Expr parseOr() throws ScriptCompileException {
        Ast.Expr left = parseAnd();
        while (checkName("or")) {
            advance();
            Ast.Expr right = parseAnd();
            left = new Ast.Binary(Ast.BinOp.OR, left, right, left.line(), left.column());
        }
        return left;
    }

    private Ast.Expr parseAnd() throws ScriptCompileException {
        Ast.Expr left = parseNot();
        while (checkName("and")) {
            advance();
            Ast.Expr right = parseNot();
            left = new Ast.Binary(Ast.BinOp.AND, left, right, left.line(), left.column());
        }
        return left;
    }

    private Ast.Expr parseNot() throws ScriptCompileException {
        if (checkName("not")) {
            Token token = advance();
            Ast.Expr operand = parseNot();
            return new Ast.Unary(Ast.UnaryOp.NOT, operand, token.line, token.column);
        }
        return parseComparison();
    }

    private Ast.Expr parseComparison() throws ScriptCompileException {
        Ast.Expr left = parseArithmetic();
        Ast.BinOp op = peekComparisonOp();
        if (op == null) {
            return left;
        }
        Token operator = advance();
        Ast.Expr right = parseArithmetic();
        Ast.Expr result = new Ast.Binary(op, left, right, operator.line, operator.column);
        if (peekComparisonOp() != null) {
            throw error(peek(), "comparison chaining is not supported");
        }
        return result;
    }

    private Ast.BinOp peekComparisonOp() {
        return switch (peek().type) {
            case EQ -> Ast.BinOp.EQ;
            case NE -> Ast.BinOp.NE;
            case LT -> Ast.BinOp.LT;
            case LE -> Ast.BinOp.LE;
            case GT -> Ast.BinOp.GT;
            case GE -> Ast.BinOp.GE;
            case NAME -> "in".equals(peek().text) ? Ast.BinOp.IN : null;
            default -> null;
        };
    }

    private Ast.Expr parseArithmetic() throws ScriptCompileException {
        Ast.Expr left = parseTerm();
        while (true) {
            if (match(TokenType.PLUS)) {
                Ast.Expr right = parseTerm();
                left = new Ast.Binary(Ast.BinOp.ADD, left, right, left.line(), left.column());
            } else if (match(TokenType.MINUS)) {
                Ast.Expr right = parseTerm();
                left = new Ast.Binary(Ast.BinOp.SUB, left, right, left.line(), left.column());
            } else {
                break;
            }
        }
        return left;
    }

    private Ast.Expr parseTerm() throws ScriptCompileException {
        Ast.Expr left = parseFactor();
        while (true) {
            if (match(TokenType.STAR)) {
                Ast.Expr right = parseFactor();
                left = new Ast.Binary(Ast.BinOp.MUL, left, right, left.line(), left.column());
            } else if (match(TokenType.SLASH)) {
                Ast.Expr right = parseFactor();
                left = new Ast.Binary(Ast.BinOp.DIV, left, right, left.line(), left.column());
            } else if (match(TokenType.DOUBLE_SLASH)) {
                Ast.Expr right = parseFactor();
                left = new Ast.Binary(Ast.BinOp.FLOORDIV, left, right, left.line(), left.column());
            } else if (match(TokenType.PERCENT)) {
                Ast.Expr right = parseFactor();
                left = new Ast.Binary(Ast.BinOp.MOD, left, right, left.line(), left.column());
            } else {
                break;
            }
        }
        return left;
    }

    private Ast.Expr parseFactor() throws ScriptCompileException {
        Token token = peek();
        if (match(TokenType.MINUS)) {
            return new Ast.Unary(Ast.UnaryOp.NEG, parseFactor(), token.line, token.column);
        }
        if (match(TokenType.PLUS)) {
            return new Ast.Unary(Ast.UnaryOp.POS, parseFactor(), token.line, token.column);
        }
        return parsePower();
    }

    private Ast.Expr parsePower() throws ScriptCompileException {
        Ast.Expr base = parsePostfix();
        if (match(TokenType.DOUBLE_STAR)) {
            Ast.Expr exponent = parseFactor();
            return new Ast.Binary(Ast.BinOp.POW, base, exponent, base.line(), base.column());
        }
        return base;
    }

    private Ast.Expr parsePostfix() throws ScriptCompileException {
        Ast.Expr expr = parsePrimary();
        while (true) {
            if (match(TokenType.LPAREN)) {
                List<Ast.Expr> args = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    while (true) {
                        args.add(parseExpression());
                        if (!match(TokenType.COMMA)) {
                            break;
                        }
                        if (check(TokenType.RPAREN)) {
                            break;
                        }
                    }
                }
                expect(TokenType.RPAREN, "')' after arguments");
                expr = new Ast.Call(expr, args, expr.line(), expr.column());
            } else if (match(TokenType.LBRACKET)) {
                Ast.Expr indexExpr = parseExpression();
                expect(TokenType.RBRACKET, "']' after index");
                expr = new Ast.Index(expr, indexExpr, expr.line(), expr.column());
            } else if (match(TokenType.DOT)) {
                Token name = expect(TokenType.NAME, "an attribute name");
                expr = new Ast.Attr(expr, name.text, expr.line(), expr.column());
            } else {
                break;
            }
        }
        return expr;
    }

    private Ast.Expr parsePrimary() throws ScriptCompileException {
        Token token = peek();
        switch (token.type) {
            case NUMBER_INT: {
                advance();
                return new Ast.Literal(Value.of((Long) token.literal), token.line, token.column);
            }
            case NUMBER_FLOAT: {
                advance();
                return new Ast.Literal(Value.of((Double) token.literal), token.line, token.column);
            }
            case STRING: {
                advance();
                return new Ast.Literal(Value.of((String) token.literal), token.line, token.column);
            }
            case NAME: {
                advance();
                return switch (token.text) {
                    case "True" -> new Ast.Literal(Value.of(true), token.line, token.column);
                    case "False" -> new Ast.Literal(Value.of(false), token.line, token.column);
                    case "None" -> new Ast.Literal(Value.none(), token.line, token.column);
                    default -> new Ast.Name(token.text, token.line, token.column);
                };
            }
            case LPAREN: {
                advance();
                Ast.Expr expr = parseExpression();
                expect(TokenType.RPAREN, "')'");
                return expr;
            }
            case LBRACKET:
                return parseListLiteral();
            case LBRACE:
                return parseDictLiteral();
            default:
                throw error(token, "expected expression, found " + token.describe());
        }
    }

    private Ast.Expr parseListLiteral() throws ScriptCompileException {
        Token start = advance();
        List<Ast.Expr> elements = new ArrayList<>();
        if (!check(TokenType.RBRACKET)) {
            while (true) {
                elements.add(parseExpression());
                if (!match(TokenType.COMMA)) {
                    break;
                }
                if (check(TokenType.RBRACKET)) {
                    break;
                }
            }
        }
        expect(TokenType.RBRACKET, "']' after list literal");
        return new Ast.ListExpr(elements, start.line, start.column);
    }

    private Ast.Expr parseDictLiteral() throws ScriptCompileException {
        Token start = advance();
        List<Ast.Expr> keys = new ArrayList<>();
        List<Ast.Expr> values = new ArrayList<>();
        if (!check(TokenType.RBRACE)) {
            while (true) {
                keys.add(parseExpression());
                expect(TokenType.COLON, "':' after dictionary key");
                values.add(parseExpression());
                if (!match(TokenType.COMMA)) {
                    break;
                }
                if (check(TokenType.RBRACE)) {
                    break;
                }
            }
        }
        expect(TokenType.RBRACE, "'}' after dictionary literal");
        return new Ast.DictExpr(keys, values, start.line, start.column);
    }

    private boolean check(TokenType type) {
        return peek().type == type;
    }

    private boolean checkName(String name) {
        Token token = peek();
        return token.type == TokenType.NAME && token.text.equals(name);
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private Token expect(TokenType type, String expectation) throws ScriptCompileException {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), "expected " + expectation + ", found " + peek().describe());
    }

    private Token advance() {
        return tokens.get(index++);
    }

    private Token peek() {
        return tokens.get(index);
    }

    private ScriptCompileException error(Token token, String message) {
        return ScriptErrors.compile(filename, token.line, token.column, message);
    }
}
