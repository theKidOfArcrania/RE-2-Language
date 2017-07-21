/*
 * Copyright (c) 2017 theKidOfArcrania
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.theKidOfArcrania.re2;

import com.theKidOfArcrania.re2.parsing.*;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static com.theKidOfArcrania.re2.RESquared.MAX_ADDR;

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

        public boolean ensureCapacity(int size)
        {
            if (data.length >= size)
                return true;

            if (size > MAX_SIZE || size + base > MAX_ADDR)
            {
                reader.error("out of memory.");
                return false;
            }

            int newSize = data.length + data.length / 2;
            if (newSize < size)
                newSize = size;

            if (newSize > MAX_SIZE)
                newSize = MAX_SIZE;

            data = Arrays.copyOf(data, newSize);
            return true;
        }

        public void write(byte b)
        {
            if (!ensureCapacity(size + 1))
                return;
            data[size++] = b;
        }

        public void write(byte[] buff)
        {
            write(buff, 0, buff.length);
        }
        public void write(byte[] buff, int offset, int length)
        {
            if (!ensureCapacity(size + length))
                return;
            System.arraycopy(buff, offset, data, size, length);
            size += length;
        }

        public void writeShort(short s)
        {
            if (!ensureCapacity(size + 2))
                return;
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

    public static final int MAX_SIZE = 0x7FFF;
    public static final int HEX_RADIX = 16;

    public static final byte[] SIGNATURE = {0x52, 0x45, 0x5e, 0x32, 0x00, 0x00, 0x00, 0x01}; //RE^2

    //PUSH %BP
    //PUSH %SP
    //POP %BP
    public static final byte[] CODE_ENTER = {0x4B, 0x1E, 0x4B, 0x5D, 0x4F, (byte) 0xAE};

    //PUSH %BP
    //POP %SP
    //POP %BP
    public static final byte[] CODE_LEAVE = {0x4B, 0x7E, 0x4F, 0x2D, 0x4F, 0x4E};

    private static HashMap<String, Integer> opcodes = new HashMap<>();
    private static HashMap<String, int[]> subopcodes = new HashMap<>();

    private static int entryPoint = -1;
    private static String entryPointLabel = null;

    private static HashMap<String, Short> labels = new HashMap<>();

    private static TokenReader reader;

    public static void main(String[] args) throws Exception
    {
        System.out.println("RE^2 Assembler v1.0");
        System.out.println("Copyright (c) 2017 theKidOfArcrania\n");

        if (args.length == 0)
        {
            File path = new File(RESquaredAssembler.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath());
            if (path.isDirectory())
                System.out.println("Usage: java com.theKidOfArcrania.re2.RESquaredAssembler <File>");
            else
                System.out.println("Usage: java -jar " + path.getName() +
                        " <file>");

            System.exit(2);
        }

        ArrayList<Section> sections = null;
        initOpcodeMappings();

        String file = args[0];
        int errors = 0;
        int warnings = 0;
        try (TokenReader in = new TokenReader(new File(file)))
        {
            sections = parseFile(in);
            errors = in.getErrors();
            warnings = in.getWarnings();
        }
        catch (IOException e)
        {
            System.err.println("Error: file not found: " + file);
            errors++;
        }

        if (errors == 0 && sections != null)
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
                System.err.println("Error: unable to write to file: " + output);
                errors++;
            }
        }

        System.out.println();
        System.out.println(errors + " error(s).");
        System.out.println(warnings + " warning(s).");
        if (errors > 0)
            System.exit(1);
    }



    private static ArrayList<Section> parseFile(TokenReader reader)
    {
        RESquaredAssembler.reader = reader;

        ArrayList<Section> sections = new ArrayList<>();

        int prevSectionLineNum = 0;

        Section current = null;
        while (reader.hasNextLine())
        {
            if (reader.getLineNum() > 0)
                reader.checkEndLine();
            reader.nextLine();

            if (!reader.hasNextToken())
                continue;

            String first = reader.nextToken();
            if (first.startsWith("."))
            {
                first = first.substring(1).toUpperCase();
                if (first.equals("SECTION"))
                {
                    if (current != null && current.base == -1)
                    {
                        reader.visitLine(prevSectionLineNum);
                        reader.error("expected: section address base.");
                        reader.traceCode(0);
                        return sections;
                    }

                    prevSectionLineNum = reader.getLineNum();
                    current = new Section();
                    sections.add(current);
                }
                else
                {
                    reader.saveErrorReporting();
                    parseDirective(current, first);
                    reader.restoreErrorReporting();
                }
                continue;
            }

            if (current == null)
            {
                reader.error("expected: section header.");
                reader.traceCode(0);
                continue;
            }

            if (first.endsWith(":"))
            {
                first = first.substring(0, first.length() - 1);
                if (reader.tokenType() != TokenType.LABEL)
                {
                    reader.error("expected: label containing only letters and underscore.");
                    reader.traceCodeToken(0);
                    continue;
                }

                if (current.base == -1)
                {
                    reader.error("expected: section base address must be defined before labels.");
                    reader.traceCodeToken(0);
                    continue;
                }

                labels.put(first, (short)(current.base + current.size));
                for (Section s : sections)
                    s.resolveSymbol(first, (short)(current.base + current.size));
            }
            else
            {
                reader.saveErrorReporting();
                parseInstruction(current, first);
                reader.restoreErrorReporting();
            }
        }
        reader.checkEndLine();

        if (entryPoint == -1)
            reader.error("no entry point specified.");

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
            reader.error("Unresolved label `" + label + "`");

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
        opcodes.put("SHL", 0x3e);
        opcodes.put("SHR", 0x3f);
        opcodes.put("ADD", 0x5e);
        opcodes.put("EXIT", 0x6d);
        opcodes.put("XOR", 0x7c);
        opcodes.put("RET", 0x7e);
        opcodes.put("OUTPUTNUM", 0xdb);
        opcodes.put("INPUT", 0xdf);

        //Psuedo instructions
        opcodes.put("ENTER", -1);
        opcodes.put("LEAVE", -1);

        //sub-opcodes
        //{REGISTER, SYMBOL, NUMBER, NUMBER 16-bit, ADDRESS, INDIRECT, INDIRECT w/ offset}
        subopcodes.put("PUSH", new int[] {0x4b, 0x6f, 0x3d, 0x6f, -1, -1, -1});
        subopcodes.put("POP", new int[] {0x4f, -1, -1, -1, -1, -1, -1});
        subopcodes.put("LOADB", new int[] {-1, 0x8e, -1, -1, 0x8e, 0x51, 0x6c});
        subopcodes.put("LOADW", new int[] {-1, 0x44, -1, -1, 0x44, 0x6a, 0x50});
        subopcodes.put("STOREB", new int[] {-1, 0x56, -1, -1, 0x56, 0x67, 0x64});
        subopcodes.put("STOREW", new int[] {-1, 0x57, -1, -1, 0x57, 0x69, 0x63});
        subopcodes.put("JMP", new int[] {-1, 0x58, -1, -1, 0x58, 0x5f, -1});
        subopcodes.put("CALL", new int[] {-1, 0x5a, -1, -1, 0x5a, 0x7d, -1});
        subopcodes.put("JNZ", new int[] {-1, 0xde, -1, -1, 0xde, -1, -1});
        subopcodes.put("JZ", new int[] {-1, 0xfc, -1, -1, 0xfc, -1, -1});
        subopcodes.put("JN", new int[] {-1, 0xfe, -1, -1, 0xfe, -1, -1});
        subopcodes.put("JP", new int[] {-1, 0xff, -1, -1, 0xff, -1, -1});
        subopcodes.put("OUTPUTSTR", new int[] {-1, 0xda, -1, -1, 0xda, 0x6b, 0x65});

        for (String instruct : subopcodes.keySet())
            opcodes.put(instruct, -1);
    }

    private static boolean parseDirective(Section section, String directive)
    {
        switch (directive)
        {
            case "BASE":
                if (section.base != -1)
                {
                    reader.error("duplicate .BASE directives.");
                    reader.traceCodeToken(0);
                    return false;
                }

                reader.setDefaultReporting(ErrorSituation.ERROR_MISSING_TOKEN, new ErrorReporting(ErrorLevel.ERROR,
                        "expected: 16-bit integer address."));
                if (reader.nextToken() == null)
                    return false;

                if (reader.tokenType() != TokenType.ADDRESS)
                {
                    reader.reportSituation(ErrorSituation.ERROR_MISSING_TOKEN);
                    reader.traceCodeToken(0);
                    return false;
                }

                Integer addr = reader.tokenNumber();
                if (addr == null)
                    return false;
                section.base = addr;
                break;
            case "ENTRY":
                if (entryPoint != -1)
                {
                    reader.error("duplicate .ENTRY directives.");
                    reader.traceCodeToken(0);
                    return false;
                }

                reader.setDefaultReporting(ErrorSituation.ERROR_MISSING_TOKEN, new ErrorReporting(ErrorLevel.ERROR,
                        "expected: 16-bit address or label to entry point."));
                if (reader.nextToken() == null || reader.tokenType() == TokenType.INVALID)
                    return false;

                switch (reader.tokenType())
                {
                    case ADDRESS:
                        break;
                    case SYMBOL:
                        entryPoint = -2;
                        entryPointLabel = reader.currentToken();
                        break;
                    default:
                        reader.reportSituation(ErrorSituation.ERROR_MISSING_TOKEN);
                        reader.traceCodeToken(0);
                        return false;
                }
                break;
            case "STR":
                if (section == null)
                {
                    reader.error("expected: section header.");
                    reader.traceCode(0);
                    return false;
                }
                String line = reader.remaining();
                if (line.isEmpty())
                {
                    reader.error("expected: string token.");
                    reader.traceCode(reader.currentLine().length());
                    return false;
                }

                int offset = reader.tokenPosition();
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
                    reader.error("expected: section header.");
                    reader.traceCode(0);
                    break;
                }

                String hex = reader.remaining();
                Scanner parsing = new Scanner(hex);
                if (!parsing.hasNext())
                {
                    reader.error("expected: data bytes in hex values.");
                    reader.traceCodeToken(0);
                }

                while (parsing.hasNext())
                {
                    String num = parsing.next();
                    if (!num.matches("[A-Fa-f0-9]{1,2}"))
                    {
                        reader.error("expected: hex number from 00 to FF");
                        reader.traceCodeToken(parsing.match().start());
                        return false;
                    }
                    section.write((byte)Integer.parseInt(num, HEX_RADIX));
                }
                break;
            case "SECTION":
                break;
            default:
                reader.error("invalid directive.");
                reader.traceCodeToken(0);
                return false;
        }

        return reader.checkEndLine();
    }

    private static String decodeString(String str, int strOffset)
    {
        if (!str.startsWith("\""))
        {
            reader.error("expected: string token.");
            reader.traceCode(strOffset);
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
                            reader.error("invalid hexadecimal.");
                            reader.traceCode(strOffset + i);
                            return null;
                        }
                        String point = str.substring(i + 1, charSize).toUpperCase();
                        for (char hex : point.toCharArray())
                        {
                            if (!Character.isLetterOrDigit(hex) ||
                                    hex > 'F') {
                                reader.error("invalid hexadecimal digit.");
                                reader.traceCode(strOffset + i);
                                return null;
                            }
                        }
                        ret.append((char)Integer.parseInt(point, HEX_RADIX));
                        i += charSize;
                        break;
                    default:
                        reader.error("invalid escape code.");
                        reader.traceCode(strOffset + i);
                        return null;
                }
            }
            else if (c == '"')
            {
                quoted = true;
                if (i != str.length() - 1)
                {
                    reader.error("unexpected tokens.");
                    reader.traceCode(strOffset + i + 1);
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
            reader.error("unexpected end of input.");
            reader.traceCode(reader.currentLine().length());
            return null;
        }
        return ret.toString();
    }

    private static boolean parseInstruction(Section section, String instruction)
    {
        instruction = instruction.toUpperCase();

        if (!opcodes.containsKey(instruction))
        {
            reader.error("invalid instruction.");
            reader.traceCodeToken(0);
            return false;
        }

        int opcode = opcodes.get(instruction);
        if (opcode != -1)
            section.write((byte)opcode);

        if (instruction.equalsIgnoreCase("POP") && !reader.hasNextToken())
            return true;

        if (subopcodes.containsKey(instruction))
            return decodeOperands(section, instruction);

        switch (instruction) {
            case "EXIT":
                reader.setDefaultReporting(ErrorSituation.ERROR_MISSING_TOKEN, new ErrorReporting(ErrorLevel.ERROR,
                        "expected: valid 16-bit hexadecimal or decimal immediate value."));
                if (reader.nextToken() == null)
                    return false;

                if (reader.tokenType() != TokenType.NUMBER)
                {
                    reader.reportSituation(ErrorSituation.ERROR_MISSING_TOKEN);
                    reader.traceCodeToken(0);
                    return false;
                }

                Integer num = reader.tokenNumber();
                if (num == null)
                    return false;
                section.writeShort(num.shortValue());
                break;
            case "ENTER":
                section.write(CODE_ENTER);
                break;
            case "LEAVE":
                section.write(CODE_LEAVE);
                break;
        }

        return reader.checkEndLine();
    }

    private static boolean decodeOperands(Section section, String instruction)
    {
        int opcodes[] = subopcodes.get(instruction);
        reader.setDefaultReporting(ErrorSituation.ERROR_MISSING_TOKEN, new ErrorReporting(ErrorLevel.ERROR,
                expectedOperands(opcodes)));
        if (reader.nextToken() == null)
            return false;

        TokenType optype = reader.tokenType();
        switch (optype)
        {
            case SYMBOL:
                section.write((byte)opcodes[TokenType.SYMBOL.ordinal()]);
                section.writeSymbol(reader.currentToken());
                return true;
            case LABEL:
            case DIRECTIVE:
                reader.reportSituation(ErrorSituation.ERROR_INVALID_TOKEN);
                reader.traceCodeToken(0);
                return false;
            case INVALID:
                return false;
        }

        int opindex = optype.ordinal();
        byte[] operands = reader.tokenBinary();
        if (optype == TokenType.NUMBER || optype == TokenType.INDIRECT)
            opindex += operands.length - 1;

        if (opcodes[opindex] == -1)
        {
            reader.reportSituation(ErrorSituation.ERROR_MISSING_TOKEN);
            reader.traceCodeToken(0);
            return false;
        }

        section.write((byte)opcodes[opindex]);
        section.write(operands);
        return true;
    }

    private static String expectedOperands(int[] allowedOpcodes)
    {
        ArrayList<String> allowed = new ArrayList<>();
        if (allowedOpcodes[0] != -1)
            allowed.add("register identifier");
        if (allowedOpcodes[2] != -1 || allowedOpcodes[3] != -1)
            allowed.add("hexadecimal/decimal immediate value");
        if (allowedOpcodes[3] != -1)
            allowed.add("hexadecimal/decimal address");
        if (allowedOpcodes[5] != -1)
            allowed.add("indirect address pointer" + (allowedOpcodes[6] != -1 ? " (optional offset)" : ""));
        if (allowedOpcodes[1] != -1)
            allowed.add("label identifier");

        StringBuilder sb = new StringBuilder("expected: valid ");
        if (allowed.isEmpty())
            throw new IllegalArgumentException("No allowed opcodes.");
        if (allowed.size() == 1)
            sb.append(allowed.get(0));
        else if (allowed.size() == 2)
            sb.append(allowed.get(0)).append(" or ").append(allowed.get(1));
        else
        {
            boolean first = true;
            for (String item : allowed)
            {
                if (first)
                    first = false;
                else
                    sb.append(", or ");
                sb.append(item);
            }
        }
        return sb.append(".").toString();
    }

    private static void putShort(DataOutputStream dos, short s) throws
            IOException
    {
        dos.writeByte((byte)s);
        dos.writeByte((byte)(s >> 8));
    }
}
