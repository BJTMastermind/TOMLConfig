package red.jackf.tomlconfig.parser;

import red.jackf.tomlconfig.exceptions.ParsingException;
import red.jackf.tomlconfig.exceptions.TokenizationException;
import red.jackf.tomlconfig.parser.data.*;
import red.jackf.tomlconfig.parser.token.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

public class TOMLParser {
    public static final TOMLParser INSTANCE = new TOMLParser();
    private final TOMLTokenizer TOKENIZER = new TOMLTokenizer();

    public TOMLParser() {
    }

    public TOMLTable parse(String contents) throws TokenizationException, ParsingException {
        List<Token> tokens = TOKENIZER.tokenize(contents);
        return toTable(tokens);
    }

    private TOMLArray getArray(ListIterator<Token> iter) throws ParsingException {
        TOMLArray array = new TOMLArray();
        boolean expectingSeparator = false;
        while (iter.hasNext()) {
            Token token = iter.next();
            if (token instanceof EndOfLineToken) continue;
            if (token instanceof ArrayEndToken) return array;
            if (expectingSeparator) {
                if (token instanceof SeparatorToken) {
                    expectingSeparator = false;
                }
            } else {
                if (token instanceof InlineTableBeginToken) {
                    array.addData(getInlineTable(iter));
                } else if (token instanceof ArrayBeginToken) {
                    array.addData(getArray(iter));
                } else if (token instanceof StringToken) {
                    array.addData(new TOMLString(((StringToken) token).getText()));
                } else if (token instanceof MultilineStringToken) {
                    array.addData(new TOMLString(((MultilineStringToken) token).getText()));
                } else if (token instanceof IntegerToken) {
                    array.addData(new TOMLInteger(((IntegerToken) token).getValue()));
                } else if (token instanceof FloatToken) {
                    array.addData(new TOMLFloat(((FloatToken) token).getValue()));
                } else if (token instanceof BooleanToken) {
                    array.addData(new TOMLBoolean(((BooleanToken) token).getValue()));
                } else {
                    throw new ParsingException("Expecting value in array, got " + token);
                }
                expectingSeparator = true;
            }
        }
        throw new ParsingException("Reached end of file while parsing array");
    }

    private TOMLTable getInlineTable(ListIterator<Token> iter) throws ParsingException {
        TOMLTable table = new TOMLTable();
        boolean expectingKeyOrEnd = true;
        TOMLKey currentKey = null;
        while (iter.hasNext()) {
            Token token = iter.next();
            if (expectingKeyOrEnd) {
                if (token instanceof InlineTableEndToken) {
                    table.seal(TOMLTable.Sealed.FULL);
                    return table;
                } else {
                    iter.previous();
                    currentKey = getNextKey(iter);
                    expectingKeyOrEnd = false;
                }
            } else { // getting the value
                if (token instanceof InlineTableBeginToken) {
                    table.addData(currentKey, getInlineTable(iter));
                } else if (token instanceof ArrayBeginToken) {
                    table.addData(currentKey, getArray(iter));
                } else if (token instanceof StringToken) {
                    table.addData(currentKey, new TOMLString(((StringToken) token).getText()));
                } else if (token instanceof MultilineStringToken) {
                    table.addData(currentKey, new TOMLString(((MultilineStringToken) token).getText()));
                } else if (token instanceof IntegerToken) {
                    table.addData(currentKey, new TOMLInteger(((IntegerToken) token).getValue()));
                } else if (token instanceof FloatToken) {
                    table.addData(currentKey, new TOMLFloat(((FloatToken) token).getValue()));
                } else if (token instanceof BooleanToken) {
                    table.addData(currentKey, new TOMLBoolean(((BooleanToken) token).getValue()));
                } else {
                    throw new ParsingException("Expecting value in inline table, got " + token);
                }
                Token nextToken = iter.next();
                if (nextToken instanceof InlineTableEndToken) {
                    table.seal(TOMLTable.Sealed.FULL);
                    return table;
                } else if (!(nextToken instanceof SeparatorToken)) {
                    throw new ParsingException("Expecting separator or end of inline table, got " + token);
                }
                expectingKeyOrEnd = true;
            }
        }
        throw new ParsingException("Reached end of file while parsing inline table");
    }

    private TOMLKey getNextKey(ListIterator<Token> iter) throws ParsingException {
        List<Token> key = new ArrayList<>();
        boolean expectingSeparatorOrEnd = false;
        while (iter.hasNext()) {
            Token token = iter.next();
            if (!expectingSeparatorOrEnd) {
                if (token instanceof StringToken) {
                    key.add(token);
                } else if (token instanceof BareStringToken) {
                    key.add(token);
                } else {
                    throw new ParsingException("Expected String token, got " + token + " when defining key");
                }
                expectingSeparatorOrEnd = true;
            } else {
                if (token instanceof SeparatorToken) {
                    expectingSeparatorOrEnd = false;
                } else if (token instanceof AssignmentToken) {
                    return TOMLKey.of(key);
                } else {
                    throw new ParsingException("Expected separator or assignment token, got " + token + " when defining key");
                }
            }
        }
        throw new ParsingException("Reached end of file while parsing key");
    }

    public TOMLTable toTable(List<Token> tokens) throws ParsingException {
        System.out.println(tokens);
        TOMLTable root = new TOMLTable();
        TOMLTable current = root;
        State state = State.BASE;
        ListIterator<Token> iter = tokens.listIterator();

        List<Token> nameList = new ArrayList<>();

        while (iter.hasNext()) {
            Token token = iter.next();
            if (token instanceof EndOfLineToken) {
                assertState(state, State.BASE, State.BASE_END_OF_LINE);
                state = State.BASE;

            } else if (token instanceof TableBeginToken) {
                assertState(state, State.BASE);
                state = State.TABLE_NAMING_EXPECTING_STRING;
            } else if (token instanceof TableEndToken) {
                assertState(state, State.TABLE_NAMING_EXPECTING_END_OR_JOINER);
                state = State.BASE_END_OF_LINE;

                TOMLKey tableName = TOMLKey.of(nameList);
                nameList.clear();
                current.seal(TOMLTable.Sealed.PARTIAL);
                TOMLValue table = root.getData(tableName);
                if (table instanceof TOMLTable) {
                    current = (TOMLTable) table;
                } else if (table == null) {
                    current = new TOMLTable();
                    root.addData(tableName, current);
                } else {
                    throw new ParsingException("Attempting to treat " + table.getClass().getSimpleName() + " as table.");
                }
            } else if (token instanceof KeyJoinToken) {
                assertState(state, State.KEY_EXPECTING_JOINER_OR_ASSIGN, State.TABLE_NAMING_EXPECTING_END_OR_JOINER, State.TABLE_ARRAY_NAMING_EXPECTING_END_OR_JOINER);
                switch (state) {
                    case KEY_EXPECTING_JOINER_OR_ASSIGN:
                        state = State.KEY_JOINER_EXPECTING_STRING;
                        break;
                    case TABLE_NAMING_EXPECTING_END_OR_JOINER:
                        state = State.TABLE_NAMING_EXPECTING_STRING;
                        break;
                    case TABLE_ARRAY_NAMING_EXPECTING_END_OR_JOINER:
                        state = State.TABLE_ARRAY_NAMING_EXPECTING_STRING;
                        break;
                }
            } else if (token instanceof AssignmentToken) {
                assertState(state, State.KEY_EXPECTING_JOINER_OR_ASSIGN);
                state = State.ASSIGNING_VALUE;
            } else if (token instanceof ArrayBeginToken) {
                assertState(state, State.ASSIGNING_VALUE);
                current.addData(TOMLKey.of(nameList), getArray(iter));
                state = State.BASE;
                nameList.clear();
            } else if (token instanceof InlineTableBeginToken) {
                assertState(state, State.ASSIGNING_VALUE);
                current.addData(TOMLKey.of(nameList), getInlineTable(iter));
                state = State.BASE;
                nameList.clear();
            } else if (token instanceof TableArrayBeginToken) {
                assertState(state, State.BASE);
                state = State.TABLE_ARRAY_NAMING_EXPECTING_STRING;
            } else if (token instanceof TableArrayEndToken) {
                assertState(state, State.TABLE_ARRAY_NAMING_EXPECTING_END_OR_JOINER);
                state = State.BASE_END_OF_LINE;

                TOMLKey tableArrayName = TOMLKey.of(nameList);
                nameList.clear();
                current.seal(TOMLTable.Sealed.PARTIAL);
                TOMLValue existing = root.getData(tableArrayName);
                if (existing instanceof TOMLTableArray) {
                    current = (TOMLTable) existing;
                } else if (existing == null) {
                    current = new TOMLTableArray();
                    root.addData(tableArrayName, current);
                } else {
                    throw new ParsingException("Attempting to treat " + existing.getClass().getSimpleName() + " as table array.");
                }
                ((TOMLTableArray) current).increaseIndex();

            } else if (token instanceof BareStringToken) {
                assertState(state, State.BASE, State.KEY_JOINER_EXPECTING_STRING, State.TABLE_NAMING_EXPECTING_STRING, State.TABLE_ARRAY_NAMING_EXPECTING_STRING);
                switch (state) {
                    case BASE:
                    case KEY_JOINER_EXPECTING_STRING:
                        nameList.add(token);
                        state = State.KEY_EXPECTING_JOINER_OR_ASSIGN;
                        break;
                    case TABLE_NAMING_EXPECTING_STRING:
                        nameList.add(token);
                        state = State.TABLE_NAMING_EXPECTING_END_OR_JOINER;
                        break;
                    case TABLE_ARRAY_NAMING_EXPECTING_STRING:
                        nameList.add(token);
                        state = State.TABLE_ARRAY_NAMING_EXPECTING_END_OR_JOINER;
                        break;
                }
            } else if (token instanceof StringToken) {
                assertState(state, State.BASE, State.KEY_JOINER_EXPECTING_STRING, State.TABLE_NAMING_EXPECTING_STRING, State.ASSIGNING_VALUE, State.TABLE_ARRAY_NAMING_EXPECTING_STRING);
                switch (state) {
                    case BASE:
                    case KEY_JOINER_EXPECTING_STRING:
                        nameList.add(token);
                        state = State.KEY_EXPECTING_JOINER_OR_ASSIGN;
                        break;
                    case TABLE_NAMING_EXPECTING_STRING:
                        nameList.add(token);
                        state = State.TABLE_NAMING_EXPECTING_END_OR_JOINER;
                        break;
                    case TABLE_ARRAY_NAMING_EXPECTING_STRING:
                        nameList.add(token);
                        state = State.TABLE_ARRAY_NAMING_EXPECTING_END_OR_JOINER;
                        break;
                    case ASSIGNING_VALUE:
                        current.addData(TOMLKey.of(nameList), new TOMLString(((StringToken) token).getText()));
                        state = State.BASE_END_OF_LINE;
                        nameList.clear();
                        break;
                }

                // just value assignments
            } else if (token instanceof MultilineStringToken) {
                assertState(state, State.ASSIGNING_VALUE);
                current.addData(TOMLKey.of(nameList), new TOMLString(((MultilineStringToken) token).getText()));
                state = State.BASE_END_OF_LINE;
                nameList.clear();
            } else if (token instanceof IntegerToken) {
                assertState(state, State.ASSIGNING_VALUE);
                current.addData(TOMLKey.of(nameList), new TOMLInteger(((IntegerToken) token).getValue()));
                state = State.BASE_END_OF_LINE;
                nameList.clear();
            } else if (token instanceof BooleanToken) {
                assertState(state, State.ASSIGNING_VALUE);
                current.addData(TOMLKey.of(nameList), new TOMLBoolean(((BooleanToken) token).getValue()));
                state = State.BASE_END_OF_LINE;
                nameList.clear();
            } else if (token instanceof FloatToken) {
                assertState(state, State.ASSIGNING_VALUE);
                current.addData(TOMLKey.of(nameList), new TOMLFloat(((FloatToken) token).getValue()));
                state = State.BASE_END_OF_LINE;
                nameList.clear();
            }
        }

        return root;
    }

    // Asserts that the given state is one of `expected`.
    private static void assertState(State current, State... expected) throws ParsingException {
        for (State state : expected) if (state == current) return;
        throw new ParsingException("Expected state to be one of " + Arrays.toString(expected) + ", was " + current);
    }

    private enum State {
        BASE, // inside a normal table
        BASE_END_OF_LINE, // requires a newline, then back to above
        ASSIGNING_VALUE, // outside of inline tables, expecting the value token
        KEY_EXPECTING_JOINER_OR_ASSIGN,
        KEY_JOINER_EXPECTING_STRING,
        TABLE_NAMING_EXPECTING_END_OR_JOINER,
        TABLE_NAMING_EXPECTING_STRING,
        TABLE_ARRAY_NAMING_EXPECTING_END_OR_JOINER,
        TABLE_ARRAY_NAMING_EXPECTING_STRING,

    }
}
