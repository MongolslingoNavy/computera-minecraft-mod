package computera.script;

final class Token {
    final TokenType type;
    final String text;
    final Object literal;
    final int line;
    final int column;

    Token(TokenType type, String text, Object literal, int line, int column) {
        this.type = type;
        this.text = text;
        this.literal = literal;
        this.line = line;
        this.column = column;
    }

    String describe() {
        return switch (type) {
            case EOF -> "end of file";
            case NEWLINE -> "end of line";
            case INDENT -> "indent";
            case DEDENT -> "dedent";
            case NAME -> "'" + text + "'";
            case NUMBER_INT, NUMBER_FLOAT -> "number";
            case STRING -> "string literal";
            default -> "'" + text + "'";
        };
    }
}
