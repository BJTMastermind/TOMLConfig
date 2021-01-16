package red.jackf.tomlconfig.parser;

import red.jackf.tomlconfig.parser.token.*;
import red.jackf.tomlconfig.parser.token.processing.*;

import java.util.*;
import java.util.regex.Matcher;

import static red.jackf.tomlconfig.parser.Patterns.*;

public class TOMLParser {
    // https://toml.io/en/v1.0.0
    public static final TOMLParser INSTANCE = new TOMLParser();

    public void parse(String contents) {
        List<Token> rawTokens = tokenize(contents);
        List<Token> tokens = preprocess(rawTokens);
        System.out.println(tokens);
    }

    // Process out the ambiguous tokens
    public List<Token> preprocess(List<Token> tokens) {
        List<Token> processed = new ArrayList<>();
        boolean tableName = false;
        boolean tableArrayName = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            Token last = i > 0 ? tokens.get(i - 1) : null;
            Token next = i < tokens.size() - 1 ? tokens.get(i + 1) : null;

            Token toAdd = token;

            if (toAdd instanceof LeftBracketToken) { // [
                if (processed.size() > 0) {
                    if (last instanceof AssignmentToken || last instanceof SeparatorToken || last instanceof ArrayBeginToken) {
                        toAdd = new ArrayBeginToken(toAdd.getIndex());
                    } else {
                        toAdd = new TableBeginToken(toAdd.getIndex());
                    }
                } else {
                    toAdd = new TableBeginToken(toAdd.getIndex());
                }
            } else if (toAdd instanceof RightBracketToken) { // ]
                if (tableName) {
                    toAdd = new TableEndToken(toAdd.getIndex());
                } else {
                    toAdd = new ArrayEndToken(toAdd.getIndex());
                }
            } else if (toAdd instanceof DoubleLeftBracketToken) { // [[
                if (last instanceof EndOfLineToken) {
                    toAdd = new TableArrayBeginToken(toAdd.getIndex());
                } else {
                    processed.add(new ArrayBeginToken(toAdd.getIndex()));
                    toAdd = new ArrayBeginToken(toAdd.getIndex() + 1);
                }
            } else if (toAdd instanceof DoubleRightBracketToken) { // ]]
                if (tableArrayName) {
                    toAdd = new TableArrayEndToken(toAdd.getIndex());
                } else {
                    processed.add(new ArrayEndToken(toAdd.getIndex()));
                    toAdd = new ArrayEndToken(toAdd.getIndex() + 1);
                }
            }

            // Correcting token types (boolean, int, float as keys even thought you shouldn't be doing that)
            if (next instanceof AssignmentToken) {
                System.out.println(toAdd);
                if (toAdd instanceof IntegerToken) {
                    toAdd = new BareStringToken(toAdd.getIndex(), ((IntegerToken) toAdd).getRaw());
                } else if (toAdd instanceof FloatToken && ((FloatToken) toAdd).getRaw().matches("^[eE0-9-_]*$")) {
                    toAdd = new BareStringToken(toAdd.getIndex(), ((FloatToken) toAdd).getRaw());
                } else if (toAdd instanceof BooleanToken) {
                    toAdd = new BareStringToken(toAdd.getIndex(), ((BooleanToken) token).getValue() ? "true" : "false");
                }
            }

            processed.add(toAdd);


            if (tableName) {
                if (last instanceof TableBeginToken || last instanceof KeyJoinToken) tableName = toAdd instanceof BareStringToken || toAdd instanceof StringToken;
                else tableName = toAdd instanceof KeyJoinToken;
            } else {
                if (toAdd instanceof TableBeginToken) tableName = true;
            }

            if (tableArrayName) {
                if (last instanceof TableArrayBeginToken || last instanceof KeyJoinToken) tableArrayName = toAdd instanceof BareStringToken || toAdd instanceof StringToken;
                else tableArrayName = toAdd instanceof KeyJoinToken;
            } else {
                if (toAdd instanceof TableArrayBeginToken) tableArrayName = true;
            }
        }

        return processed;
    }

    // Get initial tokens from the string
    public List<Token> tokenize(String contents) {
        StringReader reader = new StringReader(contents);
        List<Token> tokens = new ArrayList<>();
        while (!reader.ended()) {
            Token token = getNextToken(reader);
            tokens.add(token);
        }
        return tokens;
    }

    public Token getNextToken(StringReader contents) {
        String toRead = contents.getRemaining();
        Matcher matcher;
        Token token;

        // remove preceding whitespace
        matcher = WHITESPACE.matcher(toRead);
        if (matcher.find()) {
            contents.addIndex(matcher.end());
            toRead = contents.getRemaining();
        }

        if ((matcher = COMMENT.matcher(toRead)).find()) {
            token = new CommentToken(contents.index);

        } else if ((matcher = END_OF_LINE.matcher(toRead)).find()) {
            token = new EndOfLineToken(contents.index);

        } else if ((matcher = ASSIGNMENT.matcher(toRead)).find()) {
            token = new AssignmentToken(contents.index);

        } else if ((matcher = KEY_JOIN.matcher(toRead)).find()) {
            token = new KeyJoinToken(contents.index);

        } else if ((matcher = DOUBLE_LEFT_BRACKET.matcher(toRead)).find()) { // table array begin or two left brackets (below)
            token = new DoubleLeftBracketToken(contents.index);
        } else if ((matcher = DOUBLE_RIGHT_BRACKET.matcher(toRead)).find()) { // table array end or two left brackets (below)
            token = new DoubleRightBracketToken(contents.index);

        } else if ( (matcher = LEFT_BRACKET.matcher(toRead)).find()) { // table name begin or array begin
            token = new LeftBracketToken(contents.index);
        } else if ((matcher = RIGHT_BRACKET.matcher(toRead)).find()) { // table name end or array end
            token = new RightBracketToken(contents.index);

        } else if ( (matcher = INLINE_TABLE_BEGIN.matcher(toRead)).find()) {
            token = new InlineTableBeginToken(contents.index);
        } else if ((matcher = INLINE_TABLE_END.matcher(toRead)).find()) {
            token = new InlineTableEndToken(contents.index);

        } else if ((matcher = SEPARATOR.matcher(toRead)).find()) {
            token = new SeparatorToken(contents.index); // array separator or inline table separator

        } else if ((matcher = BOOLEAN.matcher(toRead)).find()) {
            token = new BooleanToken(contents.index, matcher.group());

        } else if ((matcher = SPECIAL_FLOAT.matcher(toRead)).find()) {
            token = new FloatToken(contents.index, matcher.group(1), true);
        } else if ((matcher = STANDARD_FLOAT.matcher(toRead)).find()) {
            token = new FloatToken(contents.index, matcher.group(1), false);

        } else if ((matcher = DEC_INTEGER.matcher(toRead)).find()) {
            token = new IntegerToken(contents.index, matcher.group(1), 10);
        } else if ((matcher = HEX_INTEGER.matcher(toRead)).find()) {
            token = new IntegerToken(contents.index, matcher.group(1), 16);
        } else if ((matcher = OCT_INTEGER.matcher(toRead)).find()) {
            token = new IntegerToken(contents.index, matcher.group(1), 8);
        } else if ((matcher = BIN_INTEGER.matcher(toRead)).find()) {
            token = new IntegerToken(contents.index, matcher.group(1), 2);

        } else if ((matcher = BARE_STRING.matcher(toRead)).find()) { // Bare Strings (Keys)
            token = new BareStringToken(contents.index, matcher.group("contents"));

        } else if ((matcher = BASIC_MULTILINE_STRING.matcher(toRead)).find()) { // Multi Line Strings
            token = new MultilineStringToken(contents.index, matcher.group("contents"), MultilineStringToken.Type.BASIC);
        } else if ((matcher = LITERAL_MULTILINE_STRING.matcher(toRead)).find()) {
            token = new MultilineStringToken(contents.index, matcher.group("contents"), MultilineStringToken.Type.LITERAL);

        } else if ((matcher = BASIC_STRING.matcher(toRead)).find()) { // Single Line Strings
            token = new StringToken(contents.index, matcher.group("contents"), StringToken.Type.BASIC);
        } else if ((matcher = LITERAL_STRING.matcher(toRead)).find()) {
            token = new StringToken(contents.index, matcher.group("contents"), StringToken.Type.LITERAL);

        } else {
            throw new IllegalArgumentException("Unknown token at " + contents.index + ": '" + toRead.substring(0, 15) + "'");
        }
        contents.addIndex(matcher.end());
        return token;
    }

    private static class StringReader {
        private final String source;
        private int index = 0;

        public StringReader(String source) {
            this.source = source;
        }

        public void addIndex(int offset) {
            index = Math.min(index + offset, source.length());
        }

        public boolean ended() {
            return index >= source.length();
        }

        public String getRemaining() {
            return source.substring(index);
        }
    }
}