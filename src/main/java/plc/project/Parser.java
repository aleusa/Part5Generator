package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Field> fields = new ArrayList<>();
        List<Ast.Method> methods = new ArrayList<>();
        while(match("LET")) {
            fields.add(parseField());
        }
        while(match("DEF")) {
            methods.add(parseMethod());
        }
        if(match("LET")){
            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
            throw new ParseException("No fields after methods " + index, index);
        }
        return new Ast.Source(fields, methods);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException {
        if(match(Token.Type.IDENTIFIER)){
            String identifier = tokens.get(-1).getLiteral();
            String typeName = "";
            //in field we are required to match a type declaration
            if(match(":")){
                //colon must be followed by Identifier
                if(match(Token.Type.IDENTIFIER)){
                    typeName = tokens.get(-1).getLiteral();
                }
                else{
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected Type Name after ':' at index " + index, index);
                }
            }
            else{
                int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                throw new ParseException("Expected ':' after field declaration at index " + index, index);
            }

            //optional assignment block
            if(match("=")){
                Ast.Expr expr = parseExpression();
                if(match(";")){
                    return new Ast.Field(identifier, typeName, Optional.of(expr));
                }
                else {
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected ; " + index, index);
                }
            }
            //otherwise expect semicolon (end of statement)
            else {
                if(match(";")){
                    return new Ast.Field(identifier, typeName, Optional.empty());
                }
                else {
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected ; " + index, index);
                }
            }
        }
        else {
            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
            throw new ParseException("Expected identifier " + index, index);
        }
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException {
        List<String> parameters = new ArrayList<>();
        List<String> parameterTypeNames = new ArrayList<>();
        Optional<String> returnTypeName = Optional.empty();
        List<Ast.Stmt> statements = new ArrayList<>();

        if(match(Token.Type.IDENTIFIER)) {
            String identifier = tokens.get(-1).getLiteral();
            if(match("(")){
                //if there is content before closing parenthesis, parse this content
                if(!peek(")")){
                    //parse identifiers within parentheses for arguments
                    if(match(Token.Type.IDENTIFIER)){
                        String identifierValue = tokens.get(-1).getLiteral();
                        String typeValue = "";
                        //check for mandatory type declaration
                        if(match(":")){
                            if(match(Token.Type.IDENTIFIER)){
                                typeValue = tokens.get(-1).getLiteral();
                            }
                            else{
                                int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                                throw new ParseException("Expected identifier at " + index, index);
                            }
                        }
                        else{
                            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                            throw new ParseException("Expected ':' in method declaration at " + index, index);
                        }

                        parameters.add(identifierValue);
                        parameterTypeNames.add(typeValue);

                    }
                    else {
                        int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                        throw new ParseException("Expected identifier " + index, index);
                    }

                    //if multiple parameters exist they should be preceded by commas, parse these and add to list
                    while(match(",")){
                        if(match(Token.Type.IDENTIFIER)){
                            String identifierValue = tokens.get(-1).getLiteral();
                            String typeValue = "";
                            //check for mandatory type declaration
                            if(match(":")){
                                if(match(Token.Type.IDENTIFIER)){
                                    typeValue = tokens.get(-1).getLiteral();
                                }
                                else{
                                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                                    throw new ParseException("Expected identifier at " + index, index);
                                }
                            }
                            else{
                                int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                                throw new ParseException("Expected ':' in method declaration at " + index, index);
                            }

                            parameters.add(identifierValue);
                            parameterTypeNames.add(typeValue);
                        }
                        else {
                            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                            throw new ParseException("Expected identifier " + index, index);
                        }
                    }
                }

                if(match(")")){
                    //optionally match a return type declaration
                    if(match(":")){
                        if(match(Token.Type.IDENTIFIER)){
                            returnTypeName = Optional.of(tokens.get(-1).getLiteral());
                        }
                        else{
                            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                            throw new ParseException("Expected identifier at index " + index, index);
                        }
                    }

                    //match mandatory statements block
                    if(match("DO")){
                        while(!peek("END")) {
                            Ast.Stmt stmt = parseStatement();
                            statements.add(stmt);
                        }
                        if(match("END")){
                            return new Ast.Method(identifier, parameters, parameterTypeNames, returnTypeName, statements);
                        }
                        else{
                            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                            throw new ParseException("Expected END " + index, index);
                        }
                    }
                    else {
                        int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                        throw new ParseException("Expected DO " + index, index);
                    }
                }
                else {
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected ) " + index, index);
                }
            }
            else {
                int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                throw new ParseException("Expected ( " + index, index);
            }
        }
        else {
            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
            throw new ParseException("Expected identifier " + index, index);
        }
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Stmt parseStatement() throws ParseException {
        if(match("LET")){
            return parseDeclarationStatement();
        }
        else if(match("IF")){
            return parseIfStatement();
        }
        else if(match("FOR")){
            return parseForStatement();
        }
        else if(match("WHILE")){
            return parseWhileStatement();
        }
        else if(match("RETURN")){
            return parseReturnStatement();
        }
        else{
            Ast.Expr left = parseExpression();
            if(match("=")) {
                Ast.Expr right = parseExpression();
                if(!match(";")) {
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected ; " + index, index);
                }
                return new Ast.Stmt.Assignment(left, right);
            }
            else{
                if(match(";")){
                    return new Ast.Stmt.Expression(left);
                }
                else{
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected ; " + index, index);
                }
            }
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Stmt.Declaration parseDeclarationStatement() throws ParseException {
        if(match(Token.Type.IDENTIFIER)){
            String identifier = tokens.get(-1).getLiteral();
            String typeName = "";
            //if match colon then type is declared
            if(match(":")){
                //colon must be followed by Identifier
                if(match(Token.Type.IDENTIFIER)){
                    typeName = tokens.get(-1).getLiteral();
                }
                else{
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected Type Name after ':' at index " + index, index);
                }
            }
            //handle typing parameter to Ast.Stmt.Declaration
            Optional<String> type = typeName.equals("") ? Optional.empty() : Optional.of(typeName);

            //optional assignment block
            if(match("=")){
                Ast.Expr expr = parseExpression();
                if(match(";")){
                    return new Ast.Stmt.Declaration(identifier, type, Optional.of(expr));
                }
                else {
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected ; " + index, index);
                }
            }
            //otherwise expect semicolon (end of statement)
            else {
                if(match(";")){
                    return new Ast.Stmt.Declaration(identifier, type, Optional.empty());
                }
                else {
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected ; " + index, index);
                }
            }
        }
        else {
            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
            throw new ParseException("Expected identifier " + index, index);
        }
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Stmt.If parseIfStatement() throws ParseException {
        Ast.Expr expr = parseExpression();
        if(!match("DO")){
            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
            throw new ParseException("Expected DO " + index, index);
        }
        List<Ast.Stmt> firstS = new ArrayList<>();
        List<Ast.Stmt> secondS = new ArrayList<>();

        while(!peek("ELSE") && !peek("END")) {
            Ast.Stmt stmt1 = parseStatement();
            firstS.add(stmt1);
        }
        if(match("ELSE")){
            while(!peek("END")) {
                Ast.Stmt stmt2 = parseStatement();
                secondS.add(stmt2);
            }
        }
        if(!match("END")){
            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
            throw new ParseException("Expected END " + index, index);
        }
        return new Ast.Stmt.If(expr, firstS, secondS);
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Stmt.For parseForStatement() throws ParseException {
        List<Ast.Stmt> statements = new ArrayList<>();
        if(match(Token.Type.IDENTIFIER)){
            String identifier = tokens.get(-1).getLiteral();
            if(match("IN")){
                Ast.Expr expr = parseExpression();
                if(match("DO")){
                    while(!peek("END")) {
                        Ast.Stmt stmt = parseStatement();
                        statements.add(stmt);
                    }
                    if(match("END")){
                        return new Ast.Stmt.For(identifier, expr, statements);
                    }
                    else{
                        int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                        throw new ParseException("Expected END " + index, index);
                    }
                }
                else{
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected DO " + index, index);
                }
            }
            else{
                int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                throw new ParseException("Expected IN " + index, index);
            }
        }
        else {
            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
            throw new ParseException("Expected identifier " + index, index);
        }
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Stmt.While parseWhileStatement() throws ParseException {
        List<Ast.Stmt> statements = new ArrayList<>();
        Ast.Expr expr = parseExpression();
        if(match("DO")){
            while(!peek("END")) {
                Ast.Stmt stmt = parseStatement();
                statements.add(stmt);
            }
            if(match("END")){
                return new Ast.Stmt.While(expr, statements);
            }
            else{
                int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                throw new ParseException("Expected END " + index, index);
            }
        }
        else{
            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
            throw new ParseException("Expected DO " + index, index);
        }
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Stmt.Return parseReturnStatement() throws ParseException {
        Ast.Expr expr = parseExpression();
        if(match(";")){
            return new Ast.Stmt.Return(expr);
        }
        else {
            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
            throw new ParseException("Expected ; " + index, index);
        }
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expr parseExpression() throws ParseException {
        //send tokens down the graph to be classified
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expr parseLogicalExpression() throws ParseException {
        Ast.Expr comparisonExpression = parseEqualityExpression();
        Ast.Expr comparisonRightHand = null;
        String operator = null;

        while(peek("OR") || peek("AND")) {
            if (match("OR")) {
                comparisonRightHand = parseEqualityExpression();
                operator = "OR";
                comparisonExpression = new Ast.Expr.Binary(operator, comparisonExpression, comparisonRightHand);
            } else if (match("AND")) {
                comparisonRightHand = parseEqualityExpression();
                operator = "AND";
                comparisonExpression = new Ast.Expr.Binary(operator, comparisonExpression, comparisonRightHand);
            }
        }
        //if this has a logical operator return the binary expression
        return comparisonExpression;
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expr parseEqualityExpression() throws ParseException {
        String[] comparison_operators = {"!=", "==", ">=", ">", "<=", "<"};
        String operator = null;
        Ast.Expr additiveRightHand = null;
        Ast.Expr additive = parseAdditiveExpression();

        //check for existence of operators in token stream
        while(peek("!=") || peek("==") || peek(">=") || peek(">") || peek("<=") || peek("<")) {
            for (int i = 0; i < comparison_operators.length; i++) {
                if (match(comparison_operators[i])) {
                    operator = comparison_operators[i];
                    additiveRightHand = parseAdditiveExpression();
                    additive = new Ast.Expr.Binary(operator, additive, additiveRightHand);
                    break;
                }
            }
        }
        return additive;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expr parseAdditiveExpression() throws ParseException {
        Ast.Expr multiplicative = parseMultiplicativeExpression();
        String operator = null;
        Ast.Expr multiplicativeRightHand = null;

        while(peek("+") || peek("-")) {
            if (match("+")) {
                multiplicativeRightHand = parseMultiplicativeExpression();
                operator = "+";
                multiplicative = new Ast.Expr.Binary(operator, multiplicative, multiplicativeRightHand);
            } else if (match("-")) {
                multiplicativeRightHand = parseMultiplicativeExpression();
                operator = "-";
                multiplicative = new Ast.Expr.Binary(operator, multiplicative, multiplicativeRightHand);
            }
        }
        return multiplicative;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expr parseMultiplicativeExpression() throws ParseException {
        Ast.Expr secondary = parseSecondaryExpression();
        String operator = null;
        Ast.Expr secondaryRightHand = null;

        while(peek("*") || peek("/")) {
            if (match("*")) {
                secondaryRightHand = parseSecondaryExpression();
                operator = "*";
                secondary = new Ast.Expr.Binary(operator, secondary, secondaryRightHand);
            } else if (match("/")) {
                secondaryRightHand = parseSecondaryExpression();
                operator = "/";
                secondary = new Ast.Expr.Binary(operator, secondary, secondaryRightHand);
            }
        }
        return secondary;
    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    public Ast.Expr parseSecondaryExpression() throws ParseException {
        Ast.Expr primaryExpression = parsePrimaryExpression();

        if(peek(".")){
            Ast.Expr function_call = null;
            while(match(".")){
                if(match(Token.Type.IDENTIFIER)){
                    //record field/function identifier literal
                    String field_name = tokens.get(-1).getLiteral();
                    if(match("(")){
                        List<Ast.Expr> arguments = new ArrayList<Ast.Expr>();

                        //if there is content before closing parenthesis, parse this content
                        if(!peek(")")){
                            //parse expression within parentheses for arguments
                            Ast.Expr innerExpression = parseExpression();
                            arguments.add(innerExpression);

                            //if multiple parameters exist they should be preceded by commas, parse these and add to list
                            while(match(",")){
                                innerExpression = parseExpression();
                                arguments.add(innerExpression);
                            }
                        }

                        if(match(")")){
                            if(function_call == null){
                                //if this is the first function call, the receiver is the primary expression
                                function_call = new Ast.Expr.Function(Optional.of(primaryExpression), field_name, arguments);
                            }
                            else{
                                //for subsequent function calls, the receiver is what was already evaluated from the left
                                //Ex: x.func1().func2() --> receiver: x.func1(), name: func2, args: []
                                function_call = new Ast.Expr.Function(Optional.of(function_call), field_name, arguments);
                            }
                        }
                        else{
                            int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                            throw new ParseException("Expected Closing Parenthesis \")\" " + index, index);
                        }
                    }
                    else{
                        //For cases such as "x.y" acknowledge access
                        if(function_call == null){
                            //for first access
                            function_call = new Ast.Expr.Access(Optional.of(primaryExpression), field_name);
                        }
                        else{
                            //in the case of x.y.z or x.func().y or x.y.func().z.func2() etc
                            function_call = new Ast.Expr.Access(Optional.of(function_call), field_name);
                        }
                    }
                }
                else{
                    //if no identifier after open parenthesis
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected Valid Identifier " + index, index);
                }
            }

            return function_call;

        }
        else{
            //if no "." in token stream
            return primaryExpression;
        }
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expr parsePrimaryExpression() throws ParseException {
        if(match("NIL")){
            return new Ast.Expr.Literal(null);
        }
        else if(match("TRUE")){
            return new Ast.Expr.Literal(true);
        }
        else if(match("FALSE")){
            return new Ast.Expr.Literal(false);
        }
        else if(match(Token.Type.INTEGER)){
            return new Ast.Expr.Literal(new BigInteger(tokens.get(-1).getLiteral()));
        }
        else if(match(Token.Type.DECIMAL)){
            return new Ast.Expr.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
        }
        else if(match(Token.Type.CHARACTER)){
            String literal = tokens.get(-1).getLiteral();
            literal = literal.substring(1, literal.length()-1);

            if(literal.length() > 1){
                if(literal.charAt(0) != '\\'){
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Invalid Character " + index, index);
                }

                char escapedChar = literal.charAt(1);
                char result;
                switch(escapedChar){
                    case 'b':
                        result = '\b';
                        break;
                    case 'n':
                        result = '\n';
                        break;
                    case 'r':
                        result = '\r';
                        break;
                    case 't':
                        result = '\t';
                        break;
                    case '\'':
                        result = '\'';
                        break;
                    case '\"':
                        result = '\"';
                        break;
                    case '\\':
                        result = '\\';
                        break;
                    default:
                        int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                        throw new ParseException("Invalid Escape Character " + index, index);
                }

                return new Ast.Expr.Literal(new Character(result));
            }
            else{
                return new Ast.Expr.Literal(new Character(literal.charAt(0)));
            }

        }
        else if(match(Token.Type.STRING)){
            String literal = tokens.get(-1).getLiteral();
            literal = literal.substring(1, literal.length()-1);
            String[] escapeVals = {"\\n", "\\b", "\\r", "\\t", "\\'", "\\\"", "\\\\"};
            String[] replaceVals = {"\n", "\b", "\r", "\t", "\'", "\"", "\\"};

            for(int i = 0; i < escapeVals.length; i++){
                literal = literal.replace(escapeVals[i], replaceVals[i]);
            }

            return new Ast.Expr.Literal(literal);
        }
        else if(match("(")){
            Ast.Expr innerExpression = parseExpression();
            if(match(")")){
                return new Ast.Expr.Group(innerExpression);
            }
            else{
                int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                throw new ParseException("Expected Closing Parenthesis \")\" " + index, index);
            }
        }
        else if(match(Token.Type.IDENTIFIER)){
            String identifier_name = tokens.get(-1).getLiteral();
            if(match("(")){

                List<Ast.Expr> arguments = new ArrayList<Ast.Expr>();

                if(!peek(")")){
                    Ast.Expr innerExpression = parseExpression();
                    arguments.add(innerExpression);

                    while(match(",")){
                        innerExpression = parseExpression();
                        arguments.add(innerExpression);
                    }
                }

                if(match(")")){
                    return new Ast.Expr.Function(Optional.empty(), identifier_name, arguments);
                }
                else{
                    int index = tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected Closing Parenthesis \")\" " + index, index);
                }

            }
            else{
                return new Ast.Expr.Access(Optional.empty(), identifier_name);
            }
        }
        else{
            if(tokens.has(0)){
                throw new ParseException("Invalid Expression " + tokens.get(0).getIndex(), tokens.get(0).getIndex());
            }
            else {
                String Ename = tokens.get(-1).getLiteral();
                throw new ParseException("Invalid Expression " + (Ename.length() + tokens.get(-1).getIndex()), Ename.length() + tokens.get(-1).getIndex());
            }
        }

    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for(int i = 0; i < patterns.length; i++){
            if(!tokens.has(i)){
                return false;
            }
            else if(patterns[i] instanceof Token.Type){
                if(patterns[i] != tokens.get(i).getType()){
                    return false;
                }
            }
            else if(patterns[i] instanceof String){
                if(!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            }
            else{
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }

        return true;

    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {

        boolean peek = peek(patterns);

        if(peek){
            for(int i = 0; i < patterns.length; i++){
                tokens.advance();
            }
        }

        return peek;

    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}