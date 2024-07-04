package disassembler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static disassembler.Commands.decToBin;

record AnswerPair<First, Second>(First first, Second second) {
    @Override
    public String toString() {
        return String.format("%s %s", first, second);
    }
}

class RISCV {
    private RISCV() {
    }

    protected static String parseIL(final String funct3) {
        return switch (funct3) {
            case "000" -> "lb";
            case "001" -> "lh";
            case "010" -> "lw";
            case "100" -> "lbu";
            case "101" -> "lhu";
            default -> throw new Error("I", funct3);
        };
    }

    protected static String parseICsr(final String funct3) {
        return switch (funct3) {
            case "001" -> "csrrw";
            case "010" -> "csrrs";
            case "011" -> "csrrc";
            case "101" -> "csrrwi";
            case "110" -> "csrrsi";
            case "111" -> "csrrci";
            default -> throw new Error("I", funct3);
        };
    }

    protected static String parseISr(final String funct3, final String funct7) {
        return switch (funct3) {
            case "000" -> "addi";
            case "001" -> {
                if (Commands.isZeroes(funct7)) {
                    yield "slli";
                } else {
                    throw new Error("I", funct7);
                }
            }
            case "010" -> "slti";
            case "011" -> "sltiu";
            case "100" -> "xori";
            case "101" -> switch (funct7) {
                case "0000000" -> "srli";
                case "0100000" -> "srai";
                default -> throw new Error("I", funct7);
            };
            case "110" -> "ori";
            case "111" -> "andi";
            default -> throw new Error("I", funct3);
        };
    }

    protected static String parseS(final String funct3) {
        return switch (funct3) {
            case "000" -> "sb";
            case "001" -> "sh";
            case "010" -> "sw";
            default -> throw new Error("S", funct3);
        };
    }

    protected static String parseU(final String opcode) {
        return switch (opcode) {
            case "0110111" -> "lui";
            case "0010111" -> "auipc";
            default -> throw new Error("U", opcode);
        };
    }

    protected static String parseB(final String funct3) {
        return switch (funct3) {
            case "000" -> "beq";
            case "001" -> "bne";
            case "100" -> "blt";
            case "101" -> "bge";
            case "110" -> "bltu";
            case "111" -> "bgeu";
            default -> throw new Error("B", funct3);
        };
    }

    protected static String parseR(final String funct7, final String funct3) {
        return switch (funct7) {
            case "0000000" -> switch (funct3) {
                case "000" -> "add";
                case "001" -> "sll";
                case "010" -> "slt";
                case "011" -> "sltu";
                case "100" -> "xor";
                case "101" -> "srl";
                case "110" -> "or";
                case "111" -> "and";
                default -> throw new Error("R", funct3);
            };
            case "0100000" -> switch (funct3) {
                case "000" -> "sub";
                case "101" -> "sra";
                default -> throw new Error("R", funct3);
            };
            case "0000001" -> switch (funct3) {
                case "000" -> "mul";
                case "001" -> "mulh";
                case "010" -> "mulhsu";
                case "011" -> "mulhu";
                case "100" -> "div";
                case "101" -> "divu";
                case "110" -> "rem";
                case "111" -> "remu";
                default -> throw new Error("R", funct3);
            };
            default -> throw new Error("R", funct7);
        };
    }
}

class Symtab {
    private Symtab() {
    }

    protected static String getStringIndex(final int other) {
        return switch (other) {
            case 0 -> "UNDEF";
            case 0xff00 -> "LOPROC";
            case 0xff01 -> "AFTER";
            case 0xff02 -> "AMD64_LCOMMON";
            case 0xff1f -> "HIPROC";
            case 0xff20 -> "LOOS";
            case 0xff3f -> "HIOS";
            case 0xfff1 -> "ABS";
            case 0xfff2 -> "COMMON";
            case 0xffff -> "HIRESERVE";
            default -> other + "";
        };
    }

    protected static String getStringVis(final int other) {
        return switch (other) {
            case 0 -> "DEFAULT";
            case 1 -> "INTERNAL";
            case 2 -> "HIDDEN";
            case 3 -> "PROTECTED";
            case 4 -> "EXPORTED";
            case 5 -> "SINGLETON";
            case 6 -> "ELIMINATE";
            default -> "UNKNOWN";
        };
    }

    protected static String getStringBind(final int info) {
        return switch (info) {
            case 0 -> "LOCAL";
            case 1 -> "GLOBAL";
            case 2 -> "WEAK";
            case 10 -> "LOOS";
            case 12 -> "HIOS";
            case 13 -> "LOPROC";
            case 15 -> "HIPROC";
            default -> "UNKNOWN";
        };
    }

    protected static String getStringType(final int info) {
        return switch (info) {
            case 0 -> "NOTYPE";
            case 1 -> "OBJECT";
            case 2 -> "FUNC";
            case 3 -> "SECTION";
            case 4 -> "FILE";
            case 5 -> "COMMON";
            case 6 -> "TLS";
            case 10 -> "LOOS";
            case 12 -> "HIOS";
            case 13 -> "LOPROC";
            case 14 -> "SPARC_REGISTER";
            case 15 -> "HIPROC";
            default -> "UNKNOWN";
        };
    }
}

class Commands {
    protected static Map<Integer, Integer> symtabMap = null;
    protected static ArrayList<Symtable> symtapList = null;
    protected static int text_addr;

    private Commands() {
    }

    protected static AnswerPair<String[], Boolean> parseCommand(int[] bytes, int left) {
        return new AnswerPair<>(Commands.parseRiscV(bytes, left), true);
    }

    private static String[] parseRiscV(int[] bytes, int left) {
        StringBuilder sb = new StringBuilder().append(decToBin(bytes[left + 3])).append(decToBin(bytes[left + 2])).append(decToBin(bytes[left + 1])).append(decToBin(bytes[left]));
        String opcode = sb.substring(25, 32);
        String rd = sb.substring(20, 25);
        String funct3 = sb.substring(17, 20);
        String rs1 = sb.substring(12, 17);
        String rs2 = sb.substring(7, 12);
        String funct7 = sb.substring(0, 7);

        if (sb.toString().equals("00000000000000000000000001110011")) {
            return new String[]{"ecall"};
        } else if (sb.toString().equals("00000000000100000000000001110011")) {
            return new String[]{"ebreak"};
        }

        return switch (opcode) {
            case "0110011" -> new String[]{RISCV.parseR(funct7, funct3), reg(rd), reg(rs1), reg(rs2)};
            case "1100011" -> {
                int imm_b = Integer.parseUnsignedInt((sb.charAt(0) + "").repeat(20) + sb.charAt(24) + sb.substring(1, 7) + sb.substring(20, 24) + "0", 2);
                yield new String[]{RISCV.parseB(funct3), reg(rs1), reg(rs2), getLabel(imm_b + 4)};
            }
            case "0100011" -> {
                int imm_s = Integer.parseUnsignedInt(Character.toString(sb.charAt(0)).repeat(20) + sb.substring(0, 7) + sb.substring(20, 25), 2);
                yield new String[]{
                        String.format("%7s %s, %s(%s)", RISCV.parseS(funct3), reg(rs2), "" + imm_s, reg(rs1))
                };
            }
            case "0110111", "0010111" -> {
                int imm_u = Integer.parseUnsignedInt(sb.substring(0, 20) + "0".repeat(12), 2);
                yield new String[]{RISCV.parseU(opcode), reg(rd), imm_u + ""};
            }
            case "1110011" -> new String[]{RISCV.parseICsr(funct3), reg(rd), reg(sb.substring(0, 12)), reg(rs1)};
            case "0010011" -> {
                int imm_i = Integer.parseUnsignedInt(Character.toString(sb.charAt(0)).repeat(20) + sb.substring(0, 12), 2);
                yield new String[]{
                        RISCV.parseISr(funct3, funct7), reg(rd), reg(rs1), "" + (funct3.equals("101") || funct3.equals("001") ? Integer.parseUnsignedInt(sb.substring(7, 12), 2) : imm_i)
                };
            }
            case "0000011" -> {
                int imm_i = Integer.parseUnsignedInt(Character.toString(sb.charAt(0)).repeat(20) + sb.substring(0, 12), 2);
                yield new String[]{
                        String.format("%7s %s, %s(%s)", RISCV.parseIL(funct3), reg(rd), "" + imm_i, reg(rs1))
                };
            }
            case "1101111" -> {
                int imm_j = Integer.parseUnsignedInt(Character.toString(sb.charAt(0)).repeat(12) + sb.substring(12, 20) + sb.charAt(11) + sb.substring(1, 11) + "0", 2);
                yield new String[]{"jal", reg(rd), getLabel(imm_j)};
            }
            case "1100111" -> {
                int imm_i = Integer.parseUnsignedInt(Character.toString(sb.charAt(0)).repeat(20) + sb.substring(0, 12), 2);
                yield new String[]{"jalr", reg(rd), reg(rs1), "" + imm_i};
            }
            case "0001111" -> new String[] {"fence" + (funct3.equals("001") ? ".i" : "")};
            default -> throw new Error("Risc-V", sb.toString());
        };
    }

    private static String reg(String a) {
        int reg = Integer.parseInt(a, 2);
        if (reg == 0) {
            return "zero";
        } else if (reg == 1) {
            return "ra";
        } else if (reg == 2) {
            return "sp";
        } else if (reg == 3) {
            return "gp";
        } else if (reg == 4) {
            return "tp";
        } else if (reg == 5) {
            return "t0";
        } else if (6 <= reg && reg <= 7) {
            String s = "t";
            s += (char) (reg - 5 + '0');
            return s;
        } else if (reg == 8) {
            return "s0";
        } else if (reg == 9) {
            return "s1";
        } else if (10 <= reg && reg <= 11) {
            String s = "a";
            s += (char) (reg - 10 + '0');
            return s;
        } else if (12 <= reg && reg <= 17) {
            String s = "a";
            s += (char) (reg - 10 + '0');
            return s;
        } else if (18 <= reg && reg <= 27) {
            String s = "s";
            s += (char) (reg - 16 + '0');
            return s;
        } else if (28 <= reg && reg <= 31) {
            String s = "t";
            s += (char) (reg - 25 + '0');
            return s;
        }
        return null;
    }

    protected static String decToBin(int b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(b % 2);
            b /= 2;
        }
        return sb.reverse().toString();
    }

    protected static boolean isZeroes(final String x) {
        return x.equals("0".repeat(x.length()));
    }

    private static String getLabel(int i) {
        final int addr_command = text_addr + i;
        if (symtabMap.containsKey(addr_command)) {
            return symtapList.get(symtabMap.get(addr_command)).getName();
        }
        symtabMap.put(addr_command, symtapList.size());
        symtapList.add(new Symtable(String.format("L%d", addr_command)));
        return getLabel(i);
    }
}

public class Parser {
    private final int[] bytes;
    private final FileOutput out;
    private final Map<String, Section> sections = new LinkedHashMap<>();
    private final ArrayList<Symtable> symtables = new ArrayList<>();
    private final Map<Integer, Integer> symTabNodesDict = new HashMap<>();

    public Parser(String fileIn, String fileOut) {
        final byte[] bytes1;
        try {
            bytes1 = Files.readAllBytes(Paths.get("src/" + fileIn));
        } catch (IOException e) {
            throw new Error(String.format("file \"%s\" the file's format must be .elf: ", e.getMessage()));
        }
        bytes = new int[bytes1.length];
        for (int i = 0; i < bytes1.length; i++) {
            bytes[i] = ((bytes1[i] < 0) ? 256 : 0) + bytes1[i];
        }
        try {
            out = new FileOutput(fileOut);
        } catch (IOException e) {
            throw new Error(String.format("Couldn't open file \"%s\"", fileOut));
        }
    }

    public void parse(String fileOut) {
        parseHeader();
        parseSymtab();
        try {
            parseAndDumpText();
        } catch (IOException e) {
            throw new Error(".text", "Couldn't write to file", e.getMessage());
        }
        try {
            dumpSymtab();
        } catch (IOException e) {
            throw new Error(".symtab", "Couldn't write to file", e.getMessage());
        }
        try {
            out.close();
        } catch (IOException e) {
            throw new Error(String.format("Couldn't close file \"%s\"", fileOut));
        }
        System.out.printf("Everything's good, you can see the result in  \"%s\"%n", fileOut);
    }

    private void parseHeader() {
        final int e_shoff = cnt(32, 4);
        final int e_shentsiz = cnt(46, 2);
        final int e_shnum = cnt(48, 2);
        final int e_shstrndx = cnt(50, 2);
        final int go = e_shoff + e_shentsiz * e_shstrndx;
        final int sh_offset12 = cnt(go + 16, 4);
        for (int j = 0, tmp = e_shoff; j < e_shnum; j++, tmp += 40) {
            String name = getName(sh_offset12 + cnt(tmp, 4));
            int[] param = new int[10];
            for (int r = 0; r < 10; r++) {
                param[r] = cnt(tmp + 4 * r, 4);
            }
            sections.put(name, new Section(param));
        }
    }

    private void parseAndDumpText() throws IOException {
        Section Text = sections.get(".text");
        out.writeToFIle(".text\n");
        int ind = Text.offset;
        final int size = Text.size;
        final int addr = Text.addr;
        for (int i = 0; i < size; i += 2) {
            AnswerPair<String[], Boolean> ans = Commands.parseCommand(bytes, ind);
            String label = (symTabNodesDict.containsKey(addr + i) && symtables.get(symTabNodesDict.get(addr + i)).getType().equals("FUNC")) ? symtables.get(symTabNodesDict.get(addr + i)).getName() : "";
            if (!label.isEmpty()) {
                out.writeToFIle(String.format("%08x   <%s>:\n", (addr + i), label));
            }
            out.writeToFIle(String.format("   %05x:\t", addr + i));
            out.writeToFIle(String.format("   %08x:\t", addr));
            int len = ans.first().length;
            out.writeToFIle(String.format("%7s\t", ans.first()[0]));
            for (int j = 1; j < len; j++) {
                out.writeToFIle(String.format("%s", ans.first()[j]));
                if (j != len - 1) {
                    out.writeToFIle(",\t");
                }
            }
            out.newLine();
            ind += 2;
            if (ans.second()) {
                i += 2;
                ind += 2;
            }
        }
        out.newLine();
    }

    private void parseSymtab() {
        final Section Symtab = sections.get(".symtab");
        final int ind = Symtab.offset;
        final int num = Symtab.size / 16;
        for (int i = 0; i < num; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 4; j++) {
                sb.append(new StringBuilder(decToBin(bytes[ind + i * 16 + 12 + j])).reverse());
            }
            final String name = getName(cnt(ind + i * 16, 4) + sections.get(".strtab").offset);
            final int value = cnt(ind + i * 16 + 4, 4);
            final int size = cnt(ind + i * 16 + 8, 4);
            final int info = Integer.parseInt(new StringBuilder(sb.substring(0, 8)).reverse().toString(), 2);
            final int other = Integer.parseInt(new StringBuilder(sb.substring(8)).reverse().toString(), 2);
            symTabNodesDict.put(value, symtables.size());
            symtables.add(new Symtable(i, name, value, size, info, other));
        }
        updateDataInParserCommands();
    }

    private void updateDataInParserCommands() {
        Commands.symtapList = symtables;
        Commands.symtabMap = symTabNodesDict;
        Commands.text_addr = sections.get(".text").addr;
    }

    private void dumpSymtab() throws IOException {
        out.writeToFIle("\n");
        out.writeToFIle(".symtab\n");
        out.writeToFIle(String.format("%s %-15s %7s %-8s %-8s %-8s %6s %s\n", "Symbol", "Value", "Size", "Type", "Bind", "Vis", "Index", "Name"));
        for (Symtable node : symtables) {
            out.writeToFIle(node.toNewString());
        }
    }

    protected int cnt(final int left, final int num) {
        int ans = 0;
        for (int i = num - 1; i >= 0; i--) {
            ans = ans * 256 + bytes[left + i];
        }
        return ans;
    }

    protected String getName(int left) {
        final StringBuilder sb = new StringBuilder();
        while (bytes[left] != 0) {
            sb.append((char) bytes[left++]);
        }
        return sb.toString();
    }
}