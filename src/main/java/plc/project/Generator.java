package plc.project;

import java.io.PrintWriter;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        //class header
        print("public class Main {");
        newline(0);

        newline(++indent);
        //source fields
        for(int i = 0; i < ast.getFields().size(); i++){
            print(ast.getFields().get(i));

            if(i < ast.getFields().size()-1){
                newline(indent);
            }
        }

        if(!ast.getFields().isEmpty()){
            newline(0);
            newline(indent);
        }

        //java main method
        print("public static void main(String[] args) {");
        newline(++indent);
        print("System.exit(new Main().main());");
        newline(--indent);
        print("}");

        newline(0);
        newline(indent);

        //source methods
        for(int i = 0; i < ast.getMethods().size(); i++){
            print(ast.getMethods().get(i));
            newline(0);

            if(i < ast.getMethods().size()-1){
                newline(indent);
            }
        }



        newline(--indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());

        if(ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");

        return null;
    }

    public String getJVMTypeFromString(String type){
        switch (type){
            case "Any":
                return "Object";

            case "Nil":
                return "Void";

            case "IntegerIterable":
                return "Iterable<Integer>";

            case "Comparable":
                return "Comparable";

            case "Boolean":
                return "boolean";

            case "Integer":
                return "int";

            case "Decimal":
                return "double";

            case "Character":
                return "char";

            case "String":
                return "String";

            default:
                return "YouFuckedUp";
        }
    }

    @Override
    public Void visit(Ast.Method ast) {
        String returnType;
        if(ast.getReturnTypeName().isPresent()){
            returnType = getJVMTypeFromString(ast.getReturnTypeName().get());
        }
        else{
            returnType = "Void";
        }

        print(returnType, " ", ast.getName(), "(");

        for(int i = 0; i < ast.getParameterTypeNames().size(); i++){
            if(i > 0){
                print(", ");
            }

            print(getJVMTypeFromString(ast.getParameterTypeNames().get(i)), " ", ast.getParameters().get(i));
        }

        print(") {");
        if(!ast.getStatements().isEmpty()){
            //indent and print statements
            newline(++indent);
            for(int i = 0; i < ast.getStatements().size(); i++){
                print(ast.getStatements().get(i));
                if(i < ast.getStatements().size() - 1){
                    newline(indent);
                }
            }
            newline(--indent);
        }

        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        print(ast.getExpression(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());

        if(ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        print("if (", ast.getCondition(), ") {");

        if(!ast.getThenStatements().isEmpty()) {
            newline(++indent);
            for(int i = 0; i < ast.getThenStatements().size(); i++) {
                if(i != 0) {
                    newline(indent);
                }
                print(ast.getThenStatements().get(i));
            }
            newline(--indent);
        }
        print("}");

        if(!ast.getThenStatements().isEmpty() && !ast.getElseStatements().isEmpty()) {
            print(" else {");
            newline(++indent);
            for(int i = 0; i < ast.getElseStatements().size(); i++) {
                if(i != 0) {
                    newline(indent);
                }
                print(ast.getElseStatements().get(i));
            }
            newline(--indent);
            print("}");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        print("for (int ", ast.getName(), " : ", ast.getValue(), ") {");

        if(!ast.getStatements().isEmpty()) {
            newline(++indent);
            for(int i = 0; i < ast.getStatements().size(); i++) {
                if(i != 0) {
                    newline(indent);
                }
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        print("while (", ast.getCondition(), ") {");

        if(!ast.getStatements().isEmpty()) {
            newline(++indent);
            for(int i = 0; i < ast.getStatements().size(); i++) {
                if(i != 0) {
                    newline(indent);
                }
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        print("return ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        if(ast.getType().equals(Environment.Type.STRING)) {
            print("\"", ast.getLiteral(), "\"");
        }
        else if(ast.getType().equals(Environment.Type.CHARACTER)) {
            print("\'", ast.getLiteral(), "\'");
        }
        else {
            print(ast.getLiteral());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        print("(", ast.getExpression(), ")");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        print(ast.getLeft());
        if(ast.getOperator().equals("AND")) {
            print(" && ");
        }
        else if(ast.getOperator().equals("OR")) {
            print(" || ");
        }
        else {
            print(" ", ast.getOperator(), " ");
        }
        print(ast.getRight());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        if(ast.getReceiver().isPresent()) {
            print(ast.getReceiver().get(), ".");
        }
        print(ast.getVariable().getJvmName());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        if(ast.getReceiver().isPresent()) {
            print(ast.getReceiver().get(), ".");
        }

        print(ast.getFunction().getJvmName(), "(");
        for(int i = 0; i < ast.getArguments().size(); i++) {
            if(i != 0) {
                print(", ");
            }
            print(ast.getArguments().get(i));
        }
        print(")");
        return null;
    }

}