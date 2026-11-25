package computera.script;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class Lexer {
    private final String source;
    private final String filename;
    private final List<Token> tokens = new ArrayList<>();
    private final Deque<String> indents = new ArrayDeque<>();

    private int pos;
    private int line = 1;
    private int column = 1;
    private int parenDepth;
    private boolean atLineStart = true;
    private boolean lineHasToken;

    Lexer(String source, String filename) {
        this.source = source;
        this.filename = filename;
    }

    List<Token> tokenize() throws ScriptCompileException {
        indents.push("");
        while (true) {
            if (atLineStart && parenDepth == 0) {
                int startLine = line;
                int startColumn = column;
                String indent = readIndent();
                if (isAtEnd()) {
                    break;
                }
                char ch = peek();
                if (ch == '\n') {
                    advance();
                    continue;
                }
                if (ch == '#') {
                    skipComment();
                    if (!isAtEnd()) {
                        advance();
                    }
                    continue;
                }
                applyIndent(indent, startLine, startColumn);
                atLineStart = false;
                continue;
            }

            skipHorizontalWhitespace();
            if (isAtEnd()) {
                break;
            }
            char ch = peek();
            if (ch == '#') {
                skipComment();
                continue;
            }
            if (ch == '\n') {
                if (parenDepth == 0) {
                    if (lineHasToken) {
                        emit(TokenType.NEWLINE, "newline", null, line, column);
                    }
                    lineHasToken = false;
                    advance();
                    atLineStart = true;
                } else {
                    advance();
                }
                continue;
            }
            if (ch == '\\' && peekAt(1) == '\n') {
                advance();
                advance();
                continue;
            }
            readToken();
            lineHasToken = true;
        }

        if (lineHasToken) {
            emit(TokenType.NEWLINE, "newline", null, line, column);
        }
        while (indents.size() > 1) {
            indents.pop();
            emit(TokenType.DEDENT, "dedent", null, line, column);
        }
        emit(TokenType.EOF, "end of file", null, line, column);
        return tokens;
    }

    private void applyIndent(String indent, int indentLine, int indentColumn) throws ScriptCompileException {
        if (indent.indexOf(' ') >= 0 && indent.indexOf('\t') >= 0) {
            throw error(indentLine, indentColumn, "inconsistent use of tabs and spaces in indentation");
        }
        String top = indents.peek();
        if (indent.equals(top)) {
            return;
        }
        if (indent.startsWith(top)) {
            indents.push(indent);
            emit(TokenType.INDENT, indent, null, indentLine, indentColumn);
            return;
        }
        while (indents.size() > 1 && !indents.peek().equals(indent)) {
            indents.pop();
            emit(TokenType.DEDENT, "dedent", null, indentLine, indentColumn);
        }
        if (!indents.peek().equals(indent)) {
            throw error(indentLine, indentColumn, "unindent does not match any outer indentation level");
        }
    }

    private void readToken() throws ScriptCompileException {
        int tokenLine = line;
        int tokenColumn = column;
        char ch = peek();

        if (isNameStart(ch)) {
            readName(tokenLine, tokenColumn);
            return;
        }
        if (ch >= '0' && ch <= '9') {
            readNumber(tokenLine, tokenColumn);
            return;
        }
        if (ch == '\'' || ch == '"') {
            readString(tokenLine, tokenColumn, ch);
            return;
        }

        switch (ch) {
            case '(' -> {
                advance();
                parenDepth++;
                emit(TokenType.LPAREN, "(", null, tokenLine, tokenColumn);
            }
            case ')' -> {
                advance();
                if (parenDepth > 0) {
                    parenDepth--;
                }
                emit(TokenType.RPAREN, ")", null, tokenLine, tokenColumn);
            }
            case '[' -> {
                advance();
                parenDepth++;
                emit(TokenType.LBRACKET, "[", null, tokenLine, tokenColumn);
            }
            case ']' -> {
                advance();
                if (parenDepth > 0) {
                    parenDepth--;
                }
                emit(TokenType.RBRACKET, "]", null, tokenLine, tokenColumn);
            }
            case '{' -> {
                advance();
                parenDepth++;
                emit(TokenType.LBRACE, "{", null, tokenLine, tokenColumn);
            }
            case '}' -> {
                advance();
                if (parenDepth > 0) {
                    parenDepth--;
                }
                emit(TokenType.RBRACE, "}", null, tokenLine, tokenColumn);
            }
            case '+' -> {
                advance();
                if (match('=')) {
                    emit(TokenType.PLUS_ASSIGN, "+=", null, tokenLine, tokenColumn);
                } else {
                    emit(TokenType.PLUS, "+", null, tokenLine, tokenColumn);
                }
            }
            case '-' -> {
                advance();
                if (match('=')) {
                    emit(TokenType.MINUS_ASSIGN, "-=", null, tokenLine, tokenColumn);
                } else {
                    emit(TokenType.MINUS, "-", null, tokenLine, tokenColumn);
                }
            }
            case '*' -> {
                advance();
                if (match('*')) {
                    emit(TokenType.DOUBLE_STAR, "**", null, tokenLine, tokenColumn);
                } else if (match('=')) {
                    emit(TokenType.STAR_ASSIGN, "*=", null, tokenLine, tokenColumn);
                } else {
                    emit(TokenType.STAR, "*", null, tokenLine, tokenColumn);
                }
            }
            case '/' -> {
                advance();
                if (match('/')) {
                    emit(TokenType.DOUBLE_SLASH, "//", null, tokenLine, tokenColumn);
                } else if (match('=')) {
                    emit(TokenType.SLASH_ASSIGN, "/=", null, tokenLine, tokenColumn);
                } else {
                    emit(TokenType.SLASH, "/", null, tokenLine, tokenColumn);
                }
            }
            case '%' -> {
                advance();
                emit(TokenType.PERCENT, "%", null, tokenLine, tokenColumn);
            }
            case '=' -> {
                advance();
                if (match('=')) {
                    emit(TokenType.EQ, "==", null, tokenLine, tokenColumn);
                } else {
                    emit(TokenType.ASSIGN, "=", null, tokenLine, tokenColumn);
                }
            }
            case '!' -> {
                advance();
                if (!match('=')) {
                    throw error(tokenLine, tokenColumn, "unexpected character '!' (did you mean '!='?)");
                }
                emit(TokenType.NE, "!=", null, tokenLine, tokenColumn);
            }
            case '<' -> {
                advance();
                if (match('=')) {
                    emit(TokenType.LE, "<=", null, tokenLine, tokenColumn);
                } else {
                    emit(TokenType.LT, "<", null, tokenLine, tokenColumn);
                }
            }
            case '>' -> {
                advance();
                if (match('=')) {
                    emit(TokenType.GE, ">=", null, tokenLine, tokenColumn);
                } else {
                    emit(TokenType.GT, ">", null, tokenLine, tokenColumn);
                }
            }
            case ':' -> {
                advance();
                emit(TokenType.COLON, ":", null, tokenLine, tokenColumn);
            }
            case ',' -> {
                advance();
                emit(TokenType.COMMA, ",", null, tokenLine, tokenColumn);
            }
            case '.' -> {
                advance();
                emit(TokenType.DOT, ".", null, tokenLine, tokenColumn);
            }
            default -> throw error(tokenLine, tokenColumn,
                    "unexpected character " + describeChar(ch));
        }
    }

    private void readName(int tokenLine, int tokenColumn) {
        int start = pos;
        while (!isAtEnd() && isNamePart(peek())) {
            advance();
        }
        emit(TokenType.NAME, source.substring(start, pos), null, tokenLine, tokenColumn);
    }

    private void readNumber(int tokenLine, int tokenColumn) throws ScriptCompileException {
        int start = pos;
        while (!isAtEnd() && peek() >= '0' && peek() <= '9') {
            advance();
        }
        boolean isFloat = false;
        if (!isAtEnd() && peek() == '.' && peekAt(1) >= '0' && peekAt(1) <= '9') {
            isFloat = true;
            advance();
            while (!isAtEnd() && peek() >= '0' && peek() <= '9') {
                advance();
            }
        }
        String text = source.substring(start, pos);
        if (isFloat) {
            try {
                emit(TokenType.NUMBER_FLOAT, text, Double.parseDouble(text), tokenLine, tokenColumn);
            } catch (NumberFormatException e) {
                throw error(tokenLine, tokenColumn, "invalid float literal '" + text + "'");
            }
        } else {
            try {
                emit(TokenType.NUMBER_INT, text, Long.parseLong(text), tokenLine, tokenColumn);
            } catch (NumberFormatException e) {
                throw error(tokenLine, tokenColumn, "integer literal out of range: " + text);
            }
        }
    }

    private void readString(int tokenLine, int tokenColumn, char quote) throws ScriptCompileException {
        advance();
        StringBuilder builder = new StringBuilder();
        while (true) {
            if (isAtEnd() || peek() == '\n') {
                throw error(tokenLine, tokenColumn, "unterminated string literal");
            }
            char ch = peek();
            if (ch == quote) {
                advance();
                emit(TokenType.STRING, builder.toString(), builder.toString(), tokenLine, tokenColumn);
                return;
            }
            if (ch == '\\') {
                advance();
                if (isAtEnd() || peek() == '\n') {
                    throw error(tokenLine, tokenColumn, "unterminated string literal");
                }
                char escaped = peek();
                advance();
                switch (escaped) {
                    case 'n' -> builder.append('\n');
                    case 't' -> builder.append('\t');
                    case 'r' -> builder.append('\r');
                    case '\\' -> builder.append('\\');
                    case '"' -> builder.append('"');
                    case '\'' -> builder.append('\'');
                    default -> builder.append('\\').append(escaped);
                }
                continue;
            }
            builder.append(ch);
            advance();
        }
    }

    private String readIndent() {
        int start = pos;
        while (!isAtEnd() && (peek() == ' ' || peek() == '\t')) {
            advance();
        }
        return source.substring(start, pos);
    }

    private void skipHorizontalWhitespace() {
        while (!isAtEnd() && (peek() == ' ' || peek() == '\t')) {
            advance();
        }
    }

    private void skipComment() {
        while (!isAtEnd() && peek() != '\n') {
            advance();
        }
    }

    private boolean match(char expected) {
        if (!isAtEnd() && peek() == expected) {
            advance();
            return true;
        }
        return false;
    }

    private char peek() {
        return source.charAt(pos);
    }

    private char peekAt(int offset) {
        int index = pos + offset;
        return index < source.length() ? source.charAt(index) : '\0';
    }

    private boolean isAtEnd() {
        return pos >= source.length();
    }

    private char advance() {
        char ch = source.charAt(pos++);
        if (ch == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return ch;
    }

    private void emit(TokenType type, String text, Object literal, int tokenLine, int tokenColumn) {
        tokens.add(new Token(type, text, literal, tokenLine, tokenColumn));
    }

    private ScriptCompileException error(int errorLine, int errorColumn, String message) {
        return ScriptErrors.compile(filename, errorLine, errorColumn, message);
    }

    private static boolean isNameStart(char ch) {
        return ch == '_' || Character.isLetter(ch);
    }

    private static boolean isNamePart(char ch) {
        return ch == '_' || Character.isLetterOrDigit(ch);
    }

    private static String describeChar(char ch) {
        if (ch >= 0x20 && ch < 0x7f) {
            return "'" + ch + "'";
        }
        return String.format("U+%04X", (int) ch);
    }
}
