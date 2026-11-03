package Main.instruction;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Two-pass assembler for the educational MS51FB9AE simulator instruction subset. */
public final class AssemblyAssembler {
    private static final Pattern LABEL = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*):\\s*(.*)$");
    private AssemblyAssembler() { }

    public static int[] assemble(String source) {
        if (source == null || source.trim().isEmpty()) throw new IllegalArgumentException("Program is empty.");
        List<String> lines = new ArrayList<>();
        Map<String, Integer> labels = new HashMap<>();
        int pc = 0;
        String[] rawLines = source.replace("\r", "").split("\n");
        for (int lineNo = 0; lineNo < rawLines.length; lineNo++) {
            String line = stripComment(rawLines[lineNo]).trim();
            if (line.isEmpty()) continue;
            Matcher m = LABEL.matcher(line);
            if (m.matches()) {
                String name = m.group(1).toUpperCase(Locale.ROOT);
                if (labels.put(name, pc) != null) throw error(lineNo, "Duplicate label: " + name);
                line = m.group(2).trim();
                if (line.isEmpty()) continue;
            }
            lines.add((lineNo + 1) + "|" + pc + "|" + line);
            pc += instructionSize(line, lineNo);
            if (pc > 16384) throw error(lineNo, "Program exceeds 16 KB program memory.");
        }
        int[] output = new int[pc];
        int out = 0;
        for (String item : lines) {
            String[] parts = item.split("\\|", 3);
            int lineNo = Integer.parseInt(parts[0]) - 1;
            int address = Integer.parseInt(parts[1]);
            String line = parts[2];
            List<String> t = tokenize(line);
            String op = t.get(0).toUpperCase(Locale.ROOT);
            try {
                int[] encoded = encode(op, t.subList(1, t.size()), address, labels);
                for (int b : encoded) output[out++] = b & 0xFF;
            } catch (RuntimeException ex) {
                throw error(lineNo, ex.getMessage());
            }
        }
        return output;
    }

    private static String stripComment(String line) {
        int semicolon = line.indexOf(';');
        int slash = line.indexOf("//");
        int cut = semicolon < 0 ? slash : (slash < 0 ? semicolon : Math.min(semicolon, slash));
        return cut < 0 ? line : line.substring(0, cut);
    }

    private static List<String> tokenize(String line) {
        String normalized = line.replace(',', ' ').trim();
        return Arrays.asList(normalized.split("\\s+"));
    }

    private static int instructionSize(String line, int lineNo) {
        List<String> t = tokenize(line);
        String op = t.get(0).toUpperCase(Locale.ROOT);
        switch (op) {
            case "MOV": {
                require(t, 3);
                String dst = t.get(1).toUpperCase(Locale.ROOT), src = t.get(2).toUpperCase(Locale.ROOT);
                if (dst.equals("A") && src.startsWith("#")) return 2;
                if (dst.matches("R[0-7]") && src.startsWith("#")) return 2;
                if (dst.equals("B") && src.startsWith("#")) return 3;
                if (dst.equals("A") || dst.matches("0X[0-9A-F]+") || dst.matches("[0-9]+")) return 2;
                throw error(lineNo, "Unsupported MOV form.");
            }
            case "ADD": case "SUBB": case "ANL": case "ORL": case "XRL": require(t, 3); return 2;
            case "SJMP": case "JZ": case "JNZ": require(t, 2); return 2;
            case "JB": case "JNB": require(t, 3); return 3;
            case "SETB": case "CLR": case "CPL": require(t, 2); return 2;
            case "INC": case "DEC": case "MUL": case "PUSH": case "POP": require(t, 2); return 1;
            case "NOP": case "HALT": require(t, 1); return 1;
            default: throw error(lineNo, "Unknown instruction: " + op);
        }
    }

    private static int[] encode(String op, List<String> a, int address, Map<String,Integer> labels) {
        switch (op) {
            case "MOV": {
                String d = a.get(0).toUpperCase(Locale.ROOT), s = a.get(1).toUpperCase(Locale.ROOT);
                if (d.equals("A") && s.startsWith("#")) return pair(0x74, value(s.substring(1)));
                if (d.matches("R[0-7]") && s.startsWith("#")) return pair(0x78 + Integer.parseInt(d.substring(1)), value(s.substring(1)));
                if (d.equals("B") && s.startsWith("#")) return new int[]{0x75, 0xF0, value(s.substring(1))};
                if (d.equals("A")) return pair(0xE5, value(s));
                if (s.equals("A")) return pair(0xF5, value(d));
                throw new IllegalArgumentException("Supported MOV forms: MOV A,#imm; MOV Rn,#imm; MOV B,#imm; MOV A,direct; MOV direct,A");
            }
            case "ADD": return pair(0x24, immediate(a.get(1)));
            case "SUBB": return pair(0x94, immediate(a.get(1)));
            case "ANL": return pair(0x54, immediate(a.get(1)));
            case "ORL": return pair(0x44, immediate(a.get(1)));
            case "XRL": return pair(0x64, immediate(a.get(1)));
            case "INC": if (!a.get(0).equalsIgnoreCase("A")) throw new IllegalArgumentException("Only INC A is supported"); return new int[]{0x04};
            case "DEC": if (!a.get(0).equalsIgnoreCase("A")) throw new IllegalArgumentException("Only DEC A is supported"); return new int[]{0x14};
            case "MUL": if (!a.get(0).equalsIgnoreCase("AB")) throw new IllegalArgumentException("Use MUL AB"); return new int[]{0xA4};
            case "PUSH": if (!a.get(0).equalsIgnoreCase("A")) throw new IllegalArgumentException("Use PUSH A"); return new int[]{0xC0};
            case "POP": if (!a.get(0).equalsIgnoreCase("A")) throw new IllegalArgumentException("Use POP A"); return new int[]{0xD0};
            case "NOP": return new int[]{0x00};
            case "HALT": return new int[]{0xFF};
            case "SJMP": return relative(0x80, a.get(0), address + 2, labels);
            case "JZ": return relative(0x60, a.get(0), address + 2, labels);
            case "JNZ": return relative(0x70, a.get(0), address + 2, labels);
            case "JB": return bitRelative(0x20, a.get(0), a.get(1), address + 3, labels);
            case "JNB": return bitRelative(0x30, a.get(0), a.get(1), address + 3, labels);
            case "SETB": return pair(0xD2, value(a.get(0)));
            case "CLR": return pair(0xC2, value(a.get(0)));
            case "CPL": return pair(0xB2, value(a.get(0)));
            default: throw new IllegalArgumentException("Unsupported instruction: " + op);
        }
    }

    private static int[] relative(int opcode, String target, int next, Map<String,Integer> labels) {
        int offset = target.startsWith("#") ? value(target.substring(1)) : (labels.containsKey(target.toUpperCase(Locale.ROOT)) ? labels.get(target.toUpperCase(Locale.ROOT)) - next : Integer.parseInt(target));
        if (offset < -128 || offset > 127) throw new IllegalArgumentException("Relative jump is outside -128..127: " + offset);
        return pair(opcode, offset & 0xFF);
    }
    private static int[] bitRelative(int opcode, String bit, String target, int next, Map<String,Integer> labels) {
        int b = value(bit);
        if (b < 0 || b > 0x7F) throw new IllegalArgumentException("Bit address must be 0x00..0x7F (bit-addressable RAM)");
        int offset = labels.containsKey(target.toUpperCase(Locale.ROOT)) ? labels.get(target.toUpperCase(Locale.ROOT)) - next : Integer.parseInt(target);
        if (offset < -128 || offset > 127) throw new IllegalArgumentException("Relative jump is outside -128..127");
        return new int[]{opcode, b, offset & 0xFF};
    }
    private static int immediate(String s) { if (!s.startsWith("#")) throw new IllegalArgumentException("Immediate operand must start with #"); return value(s.substring(1)); }
    private static int value(String s) {
        String v = s.trim().toUpperCase(Locale.ROOT);
        int n;
        if (v.startsWith("#")) v = v.substring(1);
        if (v.endsWith("H")) n = Integer.parseInt(v.substring(0, v.length()-1), 16);
        else if (v.startsWith("0X")) n = Integer.parseInt(v.substring(2), 16);
        else if (v.matches("[0-9A-F]+") && v.matches(".*[A-F].*")) n = Integer.parseInt(v, 16);
        else n = Integer.parseInt(v);
        if (n < 0 || n > 255) throw new IllegalArgumentException("Byte value must be 0..255: " + s);
        return n;
    }
    private static int[] pair(int op, int operand) { return new int[]{op, operand}; }
    private static void require(List<String> t, int count) { if (t.size() != count) throw new IllegalArgumentException("Wrong number of operands for " + t.get(0)); }
    private static IllegalArgumentException error(int line, String message) { return new IllegalArgumentException("Line " + (line + 1) + ": " + message); }
}
