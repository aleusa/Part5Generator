package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        Environment.Function function = null;
        for (Ast.Field statement : ast.getFields()) {
            visit(statement);
        }
        for (Ast.Method statement : ast.getMethods()) {
            visit(statement);
        }
        function = scope.lookupFunction("main", 0);
        if(!function.getReturnType().equals(Environment.Type.INTEGER)) {
            throw new RuntimeException("main/0 needs to return an Integer (Ast.Source)");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        if(ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            requireAssignable(Environment.getType(ast.getTypeName()), ast.getValue().get().getType());
        }
        scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName()), Environment.NIL);
        ast.setVariable(scope.lookupVariable(ast.getName()));

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        List<Environment.Type> parameterTypes = new ArrayList<>();
        for (String type : ast.getParameterTypeNames()) {
            parameterTypes.add(Environment.getType(type));
        }
        Environment.Type returnType = Environment.Type.NIL;
        if(ast.getReturnTypeName().isPresent()) {
            returnType = Environment.getType(ast.getReturnTypeName().get());
        }

        Scope definitionScope = scope;
        definitionScope.defineFunction(ast.getName(), ast.getName(), parameterTypes, returnType, args -> Environment.NIL);
        ast.setFunction(definitionScope.lookupFunction(ast.getName(), ast.getParameters().size()));

        try{
            scope = new Scope(definitionScope);
            for(int i = 0; i < ast.getParameters().size(); i++){
                Environment.Type type = Environment.getType(ast.getParameterTypeNames().get(i));
                scope.defineVariable(ast.getParameters().get(i), ast.getParameters().get(i), type, Environment.NIL);
            }

            for (Ast.Stmt statement : ast.getStatements()) {
                if(statement instanceof Ast.Stmt.Return) {
                    Ast.Expr.Group groupVariable = new Ast.Expr.Group(((Ast.Stmt.Return) statement).getValue());
                    groupVariable.setType(returnType);
                    Ast.Stmt.Return returnVariable = new Ast.Stmt.Return(groupVariable);
                    visit(returnVariable);
                }
                else {
                    visit(statement);
                }
            }
        }
        finally {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        visit(ast.getExpression());
        if(!(ast.getExpression() instanceof Ast.Expr.Function)) {
            throw new RuntimeException("The expression is not an Ast.Expr.Function");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        if(ast.getValue().isPresent()) {
            visit(ast.getValue().get());
        }

        if(ast.getTypeName().isPresent()) {
            scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName().get()), Environment.NIL);
        }
        else if(ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), ast.getName(), ast.getValue().get().getType(), Environment.NIL);
        }
        else {
            throw new RuntimeException("No typename nor value (Ast.Stmt.Declaration)");
        }
        ast.setVariable(scope.lookupVariable(ast.getName()));
        if(ast.getValue().isPresent()) {
            requireAssignable(ast.getVariable().getType(), ast.getValue().get().getType());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {

        if(ast.getReceiver() instanceof Ast.Expr.Access){
            //visit and enumerate both elements to facilitate type comparison
            visit(ast.getReceiver());
            visit(ast.getValue());

            requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());
        }
        else{
            throw new RuntimeException("Expected Access Expression as receiver in Assignment Statement");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        if (ast.getThenStatements().isEmpty()) {
            throw new RuntimeException("(IF) Then Statement is empty.");
        }

        try {
            scope = new Scope(scope);
            for (Ast.Stmt stmt : ast.getThenStatements()) {
                visit(stmt);
            }
        }
        finally {
            scope = scope.getParent();
        }

        try {
            scope = new Scope(scope);
            for (Ast.Stmt stmt : ast.getElseStatements()) {
                visit(stmt);
            }
        } finally {
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        visit(ast.getValue());
        requireAssignable(Environment.Type.INTEGER_ITERABLE, ast.getValue().getType());
        if (ast.getStatements().isEmpty()) {
            throw new RuntimeException("(For) Statement is empty.");
        }

        try {
            scope = new Scope(scope);
            scope.defineVariable(ast.getName(), ast.getName(), Environment.Type.INTEGER, Environment.NIL);
            for (Ast.Stmt statement : ast.getStatements()) {
                visit(statement);
            }
        }
        finally {
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        try {
            scope = new Scope(scope);
            for (Ast.Stmt stmt : ast.getStatements()) {
                visit(stmt);
            }
        } finally {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        Ast.Expr.Group expr = (Ast.Expr.Group) ast.getValue();
        visit(expr.getExpression());
        requireAssignable(expr.getType(), expr.getExpression().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {

        Object literal = ast.getLiteral();
        if(literal instanceof BigInteger){
            if( ((BigInteger) literal).compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0  && ((BigInteger) literal).compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0){
                ast.setType(Environment.Type.INTEGER);
            }
            else{
                throw new RuntimeException("Value " + literal + " out of range for type 'Integer'");
            }
        }
        else if(literal instanceof BigDecimal){
            double decimal = ((BigDecimal) literal).doubleValue();
            if(decimal == Double.POSITIVE_INFINITY || decimal == Double.NEGATIVE_INFINITY){
                throw new RuntimeException("Value " + literal + " out of range for type 'Decimal'");
            }
            ast.setType(Environment.Type.DECIMAL);
        }
        else if(literal == null){
            ast.setType(Environment.Type.NIL);
        }
        else if(literal instanceof Boolean){
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if(literal instanceof Character){
            ast.setType(Environment.Type.CHARACTER);
        }
        else if(literal instanceof String){
            ast.setType(Environment.Type.STRING);
        }
        else{
            throw new RuntimeException("Could not recognize type of literal: " + literal);
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        visit(ast.getExpression());
        ast.setType(ast.getExpression().getType());
        if(!(ast.getExpression() instanceof Ast.Expr.Binary)) {
            throw new RuntimeException("The expression is not an Ast.Expr.Function");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        visit(ast.getLeft());
        visit(ast.getRight());
        if(ast.getOperator().equals("AND") || ast.getOperator().equals("OR")) {
            requireAssignable(Environment.Type.BOOLEAN, ast.getLeft().getType());
            requireAssignable(Environment.Type.BOOLEAN, ast.getRight().getType());
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if(ast.getOperator().equals("<") || ast.getOperator().equals("<=") || ast.getOperator().equals(">") || ast.getOperator().equals(">=") || ast.getOperator().equals("==") || ast.getOperator().equals("!=")) {
            requireAssignable(Environment.Type.COMPARABLE, ast.getLeft().getType());
            requireAssignable(Environment.Type.COMPARABLE, ast.getRight().getType());
            if(!ast.getLeft().getType().equals(ast.getRight().getType())) {
                throw new RuntimeException("Both operands must be of the same type. (< <= ...)");
            }
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if(ast.getOperator().equals("+")) {
            if(ast.getLeft().getType().getName().equals("String") || ast.getRight().getType().getName().equals("String")) {
                if(ast.getLeft().getType().getName().equals("String")) {
                    requireAssignable(Environment.Type.STRING, ast.getLeft().getType());
                    requireAssignable(Environment.Type.ANY, ast.getRight().getType());
                }
                else {
                    requireAssignable(Environment.Type.STRING, ast.getRight().getType());
                    requireAssignable(Environment.Type.ANY, ast.getLeft().getType());
                }
                ast.setType(Environment.Type.STRING);
            }
            else {
                if(ast.getLeft().getType().getName().equals("Integer")) {
                    requireAssignable(Environment.Type.INTEGER, ast.getLeft().getType());
                    requireAssignable(Environment.Type.INTEGER, ast.getRight().getType());
                    ast.setType(Environment.Type.INTEGER);
                }
                else if(ast.getLeft().getType().getName().equals("Decimal")) {
                    requireAssignable(Environment.Type.DECIMAL, ast.getLeft().getType());
                    requireAssignable(Environment.Type.DECIMAL, ast.getRight().getType());
                    ast.setType(Environment.Type.DECIMAL);
                }
                else {
                    throw new RuntimeException("LHS needs to be an Integer, a Decimal, or a String (+)");
                }
            }
        }
        else if(ast.getOperator().equals("-")  || ast.getOperator().equals("*") || ast.getOperator().equals("/")) {
            if(ast.getLeft().getType().getName().equals("Integer")) {
                requireAssignable(Environment.Type.INTEGER, ast.getLeft().getType());
                requireAssignable(Environment.Type.INTEGER, ast.getRight().getType());
                ast.setType(Environment.Type.INTEGER);
            }
            else if(ast.getLeft().getType().getName().equals("Decimal")) {
                requireAssignable(Environment.Type.DECIMAL, ast.getLeft().getType());
                requireAssignable(Environment.Type.DECIMAL, ast.getRight().getType());
                ast.setType(Environment.Type.DECIMAL);
            }
            else {
                throw new RuntimeException("LHS needs to be an Integer, a Decimal, or a String (- * /)");
            }
        }
        else {
            throw new RuntimeException("Wrong Operator (Ast.Expr.Binary ast)");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {

        //if no reciever this is not a field
        if(!ast.getReceiver().isPresent()){
            Environment.Variable var = scope.lookupVariable(ast.getName());
            ast.setVariable(var);
        }
        //this is a field
        else{
            //get reciever and visit to set its internal variable state
            Ast.Expr.Access reciever = (Ast.Expr.Access) ast.getReceiver().get();
            visit(reciever);
            //get scope of reciever object type to search for and set its field's variable in the current scope
            Environment.Type recieverClass = reciever.getType();
            ast.setVariable(recieverClass.getScope().lookupVariable(ast.getName()));
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {

        //if this is a function in the main scope
        if(!ast.getReceiver().isPresent()){
            //lookup function and verify that parameter types are assignable based on the function definition
            Environment.Function function = scope.lookupFunction(ast.getName(), ast.getArguments().size());
            for(int i = 0; i < ast.getArguments().size(); i++){
                //visit in Analyzer in order to enumerate the type of this expression
                visit(ast.getArguments().get(i));
                requireAssignable(function.getParameterTypes().get(i), ast.getArguments().get(i).getType());
            }

            ast.setFunction(function);

        }
        //if this is an object method, evaluate the receiver as an access and then define the function in the class scope
        else{

            //visit and enumerate the receiver
            visit(ast.getReceiver().get());
            //search for function in receiver class scope
            Environment.Function function = ast.getReceiver().get().getType().getScope().lookupFunction(ast.getName(), ast.getArguments().size()+1);

            //lookup function and verify that parameter types are assignable based on the function definition
            for(int i = 0; i < ast.getArguments().size(); i++){
                //visit in Analyzer in order to enumerate the type of this expression
                visit(ast.getArguments().get(i));
                requireAssignable(function.getParameterTypes().get(i+1), ast.getArguments().get(i).getType());
            }

            ast.setFunction(function);

        }

        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        //check whether types abide by assignability rules
        if(target.getName().equals(type.getName())){
            return;
        }
        else if(target.getName().equals("Any")){
            return;
        }
        else if(target.getName().equals("Comparable")){
            if(type.getName().equals("Integer") || type.getName().equals("Decimal") || type.getName().equals("Character") || type.getName().equals("String")){
                return;
            }
        }

        throw new RuntimeException(" Type " + target.getName() + " can not be assigned to type: " + type.getName());
    }

}
