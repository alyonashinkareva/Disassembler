package disassembler;

public class Main {
    public static void main(String[] args) {
        new Parser(args[1], args[2]).parse(args[2]);
    }
}