import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("JavaDoc")
public class RESquaredAssembler
{
    public static class Section {
        public static final int INIT_SIZE = 128;

        private HashMap<String, ArrayList<Integer>> links = new HashMap<>();

        private int base = -1;
        private byte[] data;
        private int size;

        public Section()
        {
            this.data = new byte[INIT_SIZE];
            this.size = 0;
        }

        public void ensureCapacity(int size)
        {
            if (data.length >= size)
                return;

            if (size > MAX_SIZE || size + base > MAX_ADDR)
            {
                error("out of memory.");
                throw new RuntimeException();
            }

            int newSize = data.length + data.length / 2;
            if (newSize < size)
                newSize = size;

            if (newSize > MAX_SIZE)
                newSize = MAX_SIZE;

            data = Arrays.copyOf(data, newSize);
        }

        public void write(byte b)
        {
            ensureCapacity(size + 1);
            data[size++] = b;
        }

        public void write(byte[] buff)
        {
            write(buff, 0, buff.length);
        }
        public void write(byte[] buff, int offset, int length)
        {
            ensureCapacity(size + length);
            System.arraycopy(buff, offset, data, size, length);
            size += length;
        }

        public void writeShort(short s)
        {
            ensureCapacity(size + 2);
            data[size++] = (byte)s;
            data[size++] = (byte)(s >> 8);
        }

        public void writeSection(DataOutputStream dos) throws IOException
        {
            putShort(dos, (short)base);
            putShort(dos, (short)size);
            dos.write(data, 0, size);
        }

        public void writeSymbol(String label)
        {
            if (labels.containsKey(label))
            {
                writeShort(labels.get(label));
                return;
            }
            else if (!links.containsKey(label))
                links.put(label, new ArrayList<>());

            links.get(label).add(size);
            writeShort((short)-1);
        }

        public void resolveSymbol(String label, short address)
        {
            if (links.containsKey(label))
            {
                for (int addr : links.get(label))
                {
                    data[addr] = (byte)address;
                    data[addr + 1] = (byte)(address >> 8);
                }
                links.remove(label);
            }
        }
    }

    public static final int MAX_ADDR = 0xFFFF;
    public static final int MAX_SIZE = 0x7FFF;
    public static final int HEX_RADIX = 16;

    public static final byte[] SIGNATURE = {0x52, 0x45, 0x5e, 0x32, 0x00, 0x00, 0x00, 0x01}; //RE^2

    //LOADVAR %BP
    //LOADVAR %SP
    //STOREVAR %BP
    public static final byte[] CODE_ENTER = {0x4B, 0x1E, 0x4B, 0x5D, 0x4F,
            (byte) 0xAE};
    //LOADVAR %BP
    //STOREVAR %SP
    //STOREVAR %BP
    public static final byte[] CODE_LEAVE = {0x4B, 0x7E, 0x4F, 0x2D, 0x4F, 0x4E};

    private static HashMap<String, Integer> opcodes = new HashMap<>();
    private static HashMap<String, int[]> memcodes = new HashMap<>();

    private static Pattern ABSOLUTE_MEMORY = Pattern.compile("(?:0x|0)" +
            "?\\d+|[A-Za-z_]+");
    private static Pattern INDIRECT_MEMORY = Pattern.compile(
            "([+-]?(?:0x|0)?\\d+)?\\((%(?:\\d+|[ISB]P))\\)");

    private static int errors;

    private static int entryPoint = -1;
    private static String entryPointLabel = null;

    private static String file;
    private static String line;
    private static int lineNum;

    private static HashMap<String, Short> labels = new HashMap<>();

    public static void main(String[] args)
    {
        System.out.println("RE^2 Assembler v1.0");
        System.out.println("Copyright (c) 2017 theKidOfArcrania\n");

        if (args.length == 0)
        {
            System.out.println("Usage: java RESquaredAssembler <File>");
            System.exit(2);
        }

        ArrayList<Section> sections = null;
        initOpcodeMappings();

        file = args[0];
        try (Scanner in = new Scanner(new File(file)))
        {
            sections = parseFile(in);
        }
        catch (IOException e)
        {
            error("file not found: " + file);
        }

        if (errors == 0)
        {
            File output = new File(file.substring(0, file.lastIndexOf('.')) +
                    ".re");
            try (DataOutputStream dos = new DataOutputStream(new
                    FileOutputStream(output)))
            {
                dos.write(SIGNATURE);
                putShort(dos, (short)entryPoint);
                dos.write(sections.size());
                for (Section s : sections)
                    s.writeSection(dos);
            }
            catch (IOException e)
            {
                error("unable to write to file: " + output.toString());
            }
        }

        System.out.println();
        System.out.println(errors + " error(s).");
        if (errors > 0)
            System.exit(1);
    }

    private static ArrayList<Section> parseFile(Scanner in)
    {
        ArrayList<Section> sections = new ArrayList<>();

        String prevSectionLine = "";
        int prevSectionLineNum = 0;

        Section current = null;

        lineNum = 0;
        while (in.hasNextLine())
        {
            line = in.nextLine();
            lineNum++;

            String code = line.split("#")[0];
            if (code.startsWith("."))
            {
                code = code.substring(1).trim();
                if (code.equalsIgnoreCase("section"))
                {
                    if (current != null && current.base == -1)
                    {
                        error("expected: section address base.");
                        line = prevSectionLine;
                        lineNum = prevSectionLineNum;
                        traceCode(0);
                        return sections;
                    }

                    prevSectionLine = line;
                    prevSectionLineNum = lineNum;
                    current = new Section();
                    sections.add(current);
                }
                else
                    parseDirective(current, code);
                continue;
            }
            code = code.trim();
            if (code.isEmpty())
                continue;

            if (current == null)
            {
                error("expected: section header.");
                traceCode(0);
                return sections;
            }

            if (code.endsWith(":"))
            {
                code = code.substring(0, code.length() - 1);
                if (code.isEmpty() || !isLabel(code) ||
                        line.indexOf(code) != 0)
                {
                    error("expected: label containing only letters and " +
                            "underscore.");
                    traceCode(0);
                    continue;
                }

                if (current.base == -1)
                {
                    error("expected: section base address must be " +
                            "defined before labels.");
                    traceCode(0);
                    return sections;
                }
                labels.put(code, (short)(current.base + current.size));
                for (Section s : sections)
                    s.resolveSymbol(code, (short)(current.base + current.size));
            }
            else
                parseInstruction(current, code);
        }
        if (entryPoint == -1)
            error("no entry point specified.");

        HashSet<String> unresolved = new HashSet<>();
        if (entryPoint == -2)
        {
            if (labels.containsKey(entryPointLabel))
                entryPoint = labels.get(entryPointLabel);
            else
                unresolved.add(entryPointLabel);
        }

        for (Section section : sections)
            unresolved.addAll(section.links.keySet());
        for (String label : unresolved)
            error("Unresolved label `" + label + "`");

        return sections;
    }

    @SuppressWarnings("MagicNumber")
    private static void initOpcodeMappings()
    {
        opcodes.put("AND", 0x21);
        opcodes.put("DUP", 0x22);
        opcodes.put("MULT", 0x25);
        opcodes.put("NOT", 0x26);
        opcodes.put("OR", 0x2a);
        opcodes.put("SUB", 0x2b);
        opcodes.put("MOD", 0x2d);
        opcodes.put("DIV", 0x2f);
        opcodes.put("SAR", 0x3c);
        opcodes.put("LOADVAL8", 0x3d);
        opcodes.put("SHL", 0x3e);
        opcodes.put("SHR", 0x3f);
        opcodes.put("PUSH", 0x40);
        opcodes.put("LOADVAR", 0x4b);
        opcodes.put("STOREVAR", 0x4f);
        opcodes.put("POP", 0x55);
        opcodes.put("ADD", 0x5e);
        opcodes.put("EXIT", 0x6d);
        opcodes.put("LOADVAL16", 0x6f);
        opcodes.put("XOR", 0x7c);
        opcodes.put("RET", 0x7e);
        opcodes.put("OUTPUTNUM", 0xdb);
        opcodes.put("POP", 0xdc);
        opcodes.put("JNZ", 0xde);
        opcodes.put("INPUT", 0xdf);
        opcodes.put("JZ", 0xfc);
        opcodes.put("JN", 0xfe);
        opcodes.put("JP", 0xff);

        //Psuedo instructions
        opcodes.put("ENTER", -1);
        opcodes.put("LEAVE", -1);

        //Multiple-opcode instructions
        opcodes.put("JMP", -1);
        opcodes.put("CALL", -1);
        opcodes.put("LOADMEM8", -1);
        opcodes.put("LOADMEM16", -1);
        opcodes.put("STOREMEM8", -1);
        opcodes.put("STOREMEM16", -1);
        opcodes.put("OUTPUTSTR", -1);

        memcodes.put("LOADMEM8", new int[] {0x8e, 0x51, 0x6c});
        memcodes.put("LOADMEM16", new int[] {0x44, 0x6a, 0x50});
        memcodes.put("STOREMEM8", new int[] {0x56, 0x67, 0x64});
        memcodes.put("STOREMEM16", new int[] {0x57, 0x69, 0x63});
        memcodes.put("OUTPUTSTR", new int[] {0xDA, 0x6b, 0x65});
    }

    private static boolean parseDirective(Section section, String code)
    {
        int codeOffset = line.indexOf(code);

        Scanner parsing = new Scanner(code);
        if (!parsing.hasNext() || codeOffset != 1)
        {
            error("expected: directive identifier.");
            traceCode(0);
            return false;
        }

        String directive = parsing.next().toUpperCase();
        switch (directive)
        {
            case "BASE":
                if (!parsing.hasNext())
                {
                    error("expected: 16-bit address from 0 to 65535.");
                    traceCode(line.length());
                    return false;
                }

                if (section.base != -1)
                {
                    error("duplicate .BASE directives.");
                    traceCode(0);
                    return false;
                }

                try
                {
                    int addr = Integer.decode(parsing.next());
                    if (addr < 0 || addr > MAX_ADDR)
                        throw new NumberFormatException();
                    section.base = addr;
                }
                catch (NumberFormatException e)
                {
                    error("expected: 16-bit address from 0 to 65535.");
                    traceCode(codeOffset + parsing.match().start());
                    return false;
                }
                break;
            case "ENTRY":
                if (!parsing.hasNext())
                {
                    error("expected: 16-bit address from 0 to 65535.");
                    traceCode(line.length());
                    return false;
                }

                if (entryPoint != -1)
                {
                    error("duplicate .ENTRY directives.");
                    traceCode(0);
                    return false;
                }

                try
                {
                    String entry = parsing.next();
                    if (isLabel(entry))
                    {
                        entryPoint = -2;
                        entryPointLabel = entry;
                    }
                    else
                    {
                        int addr = Integer.decode(entry);
                        if (addr < 0 || addr > MAX_ADDR)
                            throw new NumberFormatException();
                        entryPoint = addr;
                    }
                }
                catch (NumberFormatException e)
                {
                    error("expected: 16-bit address from 0 to 65535 or a " +
                            "label pointing to entry point.");
                    traceCode(codeOffset + parsing.match().start());
                    return false;
                }
                break;
            case "STR":
                if (section == null)
                {
                    error("expected: section header.");
                    traceCode(0);
                    break;
                }
                if (!parsing.hasNextLine())
                {
                    error("expected: string token.");
                    traceCode(line.length());
                    return false;
                }

                String line = parsing.nextLine();
                int offset = codeOffset + parsing.match().start();
                for (char c : line.toCharArray())
                {
                    if (c > ' ')
                        break;
                    offset++;
                }
                String parsed = decodeString(line.trim(), offset);
                if (parsed == null)
                    return false;
                section.write(parsed.getBytes());
                section.write((byte)0);
                break;
            case "DB":
                if (section == null)
                {
                    error("expected: section header.");
                    traceCode(0);
                    break;
                }
                if (!parsing.hasNext())
                    error("expected: data bytes in hex values.");
                while (parsing.hasNext())
                {
                    String num = parsing.next();
                    if (!num.matches("[A-Fa-f0-9]{1,2}"))
                    {
                        error("expected: hex number from 00 to FF");
                        traceCode(codeOffset + parsing.match().start());
                        return false;
                    }
                    section.write((byte)Integer.parseInt(num, HEX_RADIX));
                }
                break;
            case "SECTION":
                break;
        }

        if (parsing.hasNextLine() && !parsing.nextLine().isEmpty())
        {
            error("unexpected tokens.");
            traceCode(parsing.match().start() + codeOffset);
            return false;
        }

        return true;
    }

    private static String decodeString(String str, int strOffset)
    {
        if (!str.startsWith("\""))
        {
            error("expected: string token.");
            traceCode(strOffset);
            return null;
        }

        boolean quoted = false;
        boolean escaped = false;
        StringBuilder ret = new StringBuilder();
        for (int i = 1; i < str.length(); i++)
        {
            char c = str.charAt(i);
            if (escaped)
            {
                int charSize = 4;
                escaped = false;
                switch (c)
                {
                    case '"': ret.append('"'); break;
                    case '\'': ret.append('\''); break;
                    case '\\': ret.append('\\'); break;
                    case '0': ret.append('\000'); break;
                    case 'n': ret.append('\n'); break;
                    case 'r': ret.append('\r'); break;
                    case 't': ret.append('\t'); break;
                    case 'x':
                        charSize = 2;
                        //PASSTHROUGH
                    case 'u':
                        if (str.length() - i - 1 < charSize) {
                            error("invalid hexadecimal.");
                            traceCode(strOffset + i);
                            return null;
                        }
                        String point = str.substring(i + 1, charSize).toUpperCase();
                        for (char hex : point.toCharArray())
                        {
                            if (!Character.isLetterOrDigit(hex) ||
                                    hex > 'F') {
                                error("invalid hexadecimal digit.");
                                traceCode(strOffset + i);
                                return null;
                            }
                        }
                        ret.append((char)Integer.parseInt(point, HEX_RADIX));
                        i += charSize;
                        break;
                    default:
                        error("invalid escape code.");
                        traceCode(strOffset + i);
                        return null;
                }
            }
            else if (c == '"')
            {
                quoted = true;
                if (i != str.length() - 1)
                {
                    error("unexpected tokens.");
                    traceCode(strOffset + i + 1);
                    return null;
                }
            }
            else if (c == '\\')
                escaped = true;
            else
                ret.append(c);
        }
        if (escaped || !quoted)
        {
            error("unexpected end of input.");
            traceCode(line.length());
            return null;
        }
        return ret.toString();
    }

    private static boolean parseInstruction(Section section, String code)
    {
        int codeOffset = line.indexOf(code);

        Scanner parsing = new Scanner(code);
        String instruction = parsing.next().toUpperCase();

        if (!opcodes.containsKey(instruction))
        {
            error("invalid instruction.");
            traceCode(parsing.match().start() + codeOffset);
            return false;
        }

        int opcode = opcodes.get(instruction);
        if (opcode != -1)
            section.write((byte)opcode);
        switch(instruction)
        {
            case "LOADVAL8":
                if (!decodeByte(section, parsing, codeOffset))
                    return false;
                break;
            case "LOADVAL16":
            case "EXIT":
                if (!decodeShort(section, parsing, codeOffset))
                    return false;
                break;
            case "LOADMEM8":
            case "LOADMEM16":
            case "STOREMEM8":
            case "STOREMEM16":
            case "OUTPUTSTR":
                if (!decodeMemory(section, parsing, codeOffset, instruction))
                    return false;
                break;
            case "LOADVAR":
            case "STOREVAR":
                if (!parsing.hasNext())
                {
                    error("expected: variable identifier");
                    traceCode(line.length());
                    return false;
                }
                Byte var = parseVariable(parsing.next());
                if (var == null)
                {
                    traceCode(codeOffset + parsing.match().start());
                    return false;
                }
                section.write(var);
                break;
            case "ENTER":
                section.write(CODE_ENTER);
                break;
            case "LEAVE":
                section.write(CODE_LEAVE);
                break;
            case "CALL":
                if (!decodeJumpAddress(section, parsing, codeOffset,
                        new byte[] {0x5a, 0x7d}))
                    return false;
                break;
            case "JMP":
                if (!decodeJumpAddress(section, parsing, codeOffset,
                        new byte[] {0x58, 0x5f}))
                    return false;
                break;
            case "JP":
            case "JN":
            case "JZ":
            case "JNZ":
                if (!decodeAddress(section, parsing, codeOffset))
                    return false;
        }


        if (parsing.hasNextLine() && !parsing.nextLine().isEmpty())
        {
            error("unexpected tokens.");
            traceCode(parsing.match().start() + codeOffset);
            return false;
        }

        return true;
    }

    private static boolean decodeMemory(Section section, Scanner parsing, int
            codeOffset, String instruction)
    {
        if (!parsing.hasNext())
        {
            error("expected: an absolute address location or a indirect " +
                    "address with an optional offset.");
            traceCode(line.length());
            return false;
        }

        int opcodes[] = memcodes.get(instruction);

        String mem = parsing.next();
        if (ABSOLUTE_MEMORY.matcher(mem).matches())
        {
            section.write((byte)opcodes[0]);
            return decodeAddress(section, new Scanner(mem), parsing.match()
                .start() + codeOffset);
        }
        else
        {
            Matcher indirectMem = INDIRECT_MEMORY.matcher(mem);
            if (!indirectMem.matches())
            {
                error("expected: an absolute address location or a indirect " +
                        "address with an optional offset.");
                traceCode(parsing.match().start() + codeOffset);
                return false;
            }

            Byte variable;
            byte offset = 0;

            try
            {
                if (indirectMem.group(1) != null)
                    offset = Byte.decode(indirectMem.group(1));
            }
            catch (NumberFormatException e)
            {
                error("expected: valid 8-bit offset for indirect memory");
                traceCode(parsing.match().start() + codeOffset);
                return false;
            }

            variable = parseVariable(indirectMem.group(2));
            if (variable == null)
            {
                traceCode(parsing.match().start() + codeOffset + indirectMem
                        .start(2));
                return false;
            }

            section.write((byte)opcodes[offset == 0 ? 1 : 2]);
            section.write(variable);
            if (offset != 0)
                section.write(offset);
            return true;
        }
    }

    private static Byte parseVariable(String var) {
        switch (var)
        {
            case "%IP":
                return RESquared.IP;
            case "%BP":
                return RESquared.BP;
            case "%SP":
                return RESquared.SP;
        }
        if (!var.startsWith("%"))
        {
            error("expected: variable identifier with the format %[NUMBER] " +
                    " or %IP/%BP/%SP");
            return null;
        }

        try
        {
            byte num = Byte.parseByte(var.substring(1));
            num--;
            if (num < 0 || num >= 16)
            {
                error("variable identifier number must be in the range 1 to " +
                        "16");
                return null;
            }
            return (byte)(num | (Double.doubleToLongBits(Math.random()) &
                    0xF) << 4);
        }
        catch (RuntimeException e)
        {
            error("expected: variable identifier with the format %[NUMBER] " +
                    " or %IP/%BP/%SP");
            return null;
        }
    }

    private static boolean decodeByte(Section section, Scanner parsing, int
            codeOffset) {
        try
        {
            if (!parsing.hasNext())
            {
                error("expected: valid signed 8-bit integer (from -128 to 127).");
                traceCode(line.length());
                return false;
            }
            section.write(Byte.decode(parsing.next()));
            return true;
        }
        catch (NumberFormatException e)
        {
            error("expected: valid signed 8-bit integer (from -128 to 127).");
            traceCode(codeOffset + parsing.match().start());
            return false;
        }
    }

    private static boolean decodeShort(Section section, Scanner parsing, int
            codeOffset) {
        try
        {
            if (!parsing.hasNext())
            {
                error("expected: valid signed 16-bit integer (from -32768 " +
                        "to 32767) or label.");
                traceCode(line.length());
                return false;
            }
            String val = parsing.next();
            if (isLabel(val))
            {
                section.writeSymbol(val);
                return true;
            }
            section.writeShort(Short.decode(val));
            return true;
        }
        catch (NumberFormatException | NoSuchElementException e)
        {
            error("expected: valid signed 16-bit integer (from -32768 to " +
                    "32767) or label.");
            traceCode(codeOffset + parsing.match().start());
            return false;
        }
    }

    private static boolean decodeJumpAddress(Section section, Scanner parsing,
                                         int codeOffset, byte[] opcodes)
    {
        try
        {
            if (!parsing.hasNext())
            {
                error("expected: 16-bit address from 0 to 65535 or label.");
                traceCode(line.length());
                return false;
            }

            String addr = parsing.next();
            if (addr.startsWith("*"))
            {
                Byte var = parseVariable(addr.substring(1));
                if (var == null)
                {
                    traceCode(codeOffset + parsing.match().start() + 1);
                    return false;
                }
                section.write(new byte[] {opcodes[1], var});
                return true;
            }

            section.write(opcodes[0]);
            if (isLabel(addr))
            {
                section.writeSymbol(addr);
                return true;
            }

            int num = Integer.decode(addr);
            if (num < 0 || num > MAX_ADDR)
                throw new NumberFormatException();

            section.writeShort((short)num);
            return true;
        }
        catch (NumberFormatException e)
        {
            error("expected: 16-bit address from 0 to 65535 or label.");
            traceCode(codeOffset + parsing.match().start());
            return false;
        }

    }

    private static boolean decodeAddress(Section section, Scanner parsing,
                                         int codeOffset)
    {

        if (!parsing.hasNext())
        {
            error("expected: 16-bit address from 0 to 65535 or label.");
            traceCode(line.length());
            return false;
        }

        String addr = parsing.next();
        if (isLabel(addr))
        {
            section.writeSymbol(addr);
            return true;
        }

        try
        {
            int num = Integer.decode(addr);
            if (num < 0 || num > MAX_ADDR)
                throw new NumberFormatException();
            section.writeShort((short)num);
            return true;
        }
        catch (NumberFormatException e)
        {
            error("expected: 16-bit address from 0 to 65535 or label.");
            traceCode(codeOffset + parsing.match().start());
            return false;
        }

    }

    private static boolean isLabel(String test)
    {
        for (char c : test.toCharArray())
        {
            if (!Character.isLetter(c) && c != '_')
                return false;
        }
        return true;
    }

    private static void putShort(DataOutputStream dos, short s) throws
            IOException
    {
        dos.writeByte((byte)s);
        dos.writeByte((byte)(s >> 8));
    }

    private static void error(String description)
    {
        System.err.println("ERROR: " + description);
        errors++;
    }

    private static void traceCode(int charNum)
    {
        System.err.println(file + ":" + lineNum + ":" + (charNum + 1));
        System.err.println(line);
        for (int i = 0; i < charNum; i++)
            System.err.print(" ");
        System.err.println("^\n");
    }
}
