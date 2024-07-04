package disassembler;

public class Error extends RuntimeException {
    protected Error(final String sectionName, final String message, final String cause) {
        super(String.format("%s while parsing \"%s\" section: %s", message, sectionName, cause));
    }

    protected Error(final String type, final String message) {
        super(String.format("invalid \"%s\"-type instruction: %s", type, message));
    }

    protected Error(final String message) {
        super(String.format("Couldn't start parsing : %s", message));
    }
}
