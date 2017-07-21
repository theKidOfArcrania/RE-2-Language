package com.theKidOfArcrania.re2;//RE^2

// I always wanted to create my own variant of machine language. So here it
// is, a relatively simple language. I call it Revamped Evolution of
// programming Squared, or RE^2 for short. This is a reverse engineering
// problem within a reverse engineering problem! Here's the interpreter:
// Oh... and can you test out my latest flag program? I forgot the
// combination to the program. Good luck!
//

import java.io.*;
import java.util.Arrays;
import java.util.Scanner;

@SuppressWarnings("JavaDoc")
public class RESquared {

    public static final int REGISTER_COUNT = 0x10;
    public static final int REGISTER_MASK = REGISTER_COUNT - 1;
    public static final int SP = REGISTER_COUNT - 3;
    public static final int BP = REGISTER_COUNT - 2;
    public static final int IP = REGISTER_COUNT - 1;

    public static final byte[] SIGNATURE = {0x52, 0x45, 0x5e, 0x32, 0x00, 0x00, 0x00, 0x01}; //RE^2

    public static final int MAX_ADDR = 0xFFFF;
    public static final int STACK_ADDR = 0xFFF0;

    public static short[] REGISTERS = new short[REGISTER_COUNT];
    public static byte[] MEMORY = new byte[MAX_ADDR + 1];

    public static Scanner in = new Scanner(System.in);
    public static short ipCache;

    public static void main(String[] args) throws Exception
    {
        System.out.println("RE^2 Interpreter v1.0");
        System.out.println("Copyright (c) 2017 theKidOfArcrania\n");

        if (args.length == 0)
        {
            File path = new File(RESquared.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath());
            if (path.isDirectory())
                System.out.println("Usage: java com.theKidOfArcrania.re2.RESquared <File>");
            else
                System.out.println("Usage: java -jar " + path.getName() +
                        " <file>");

            System.exit(2);
        }

        String file = args[0];
        System.out.println();
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file)))
        {
            byte[] sig = new byte[SIGNATURE.length];
            dis.readFully(sig);
            if (!Arrays.equals(SIGNATURE, sig))
                throw new Exception();

            REGISTERS[IP] = readShort(dis); //entry point
            byte sections = dis.readByte();
            if (sections < 0)
                throw new Exception();

            for (int i = 0; i < sections; i++)
            {
                int offset = readShort(dis) & MAX_ADDR;
                short size = readShort(dis);
                if (size < 0)
                    throw new Exception();
                dis.readFully(MEMORY, offset, size);
            }

            REGISTERS[BP] = REGISTERS[SP] = (short)STACK_ADDR;
            while (true)
                step();
        }
        catch (EOFException e)
        {
            System.out.println("ERROR: Binary format error.");
            System.exit(1);
        }
        catch (IOException e)
        {
            System.out.println("ERROR: File not found: " + file);
            System.exit(1);
        }
        catch (IndexOutOfBoundsException e)
        {
            System.out.println("ERROR: Segmentation Fault.");
            //e.printStackTrace();
            System.exit(3);
        }
        catch (Exception e)
        {
            System.out.println("ERROR: Binary format error.");
            System.exit(1);
        }

    }

    public static short readShort(DataInputStream dis) throws IOException
    {
        return getShort(dis.readByte(), dis.readByte());
    }

    public static byte indirectIncr(int variable)
    {
        byte val = MEMORY[REGISTERS[variable] & MAX_ADDR];
        REGISTERS[variable]++;
        return val;
    }

    public static byte indirect(int variable, int offset)
    {
        byte val = MEMORY[((variable == IP ? ipCache : REGISTERS[variable]) &
                MAX_ADDR) + offset];
        return val;
    }

    public static short getShort(byte leastSig, byte mostSig)
    {
        int val = leastSig & 0xFF;
        val |= mostSig << 8;
        return (short)val;
    }

    public static void putIndirect(byte val, int variable, int offset)
    {
        MEMORY[((variable == IP ? ipCache : REGISTERS[variable]) & MAX_ADDR) +
                offset] = val;
    }

    public static void putShort(short val, int addr)
    {
        MEMORY[addr] = (byte)val;
        MEMORY[addr + 1] = (byte)(val >> 8);
    }

    public static void putShortIndirect(short val, int variable, int offset)
    {
        putShort(val, ((variable == IP ? ipCache : REGISTERS[variable]) &
                MAX_ADDR) + offset);
    }

    public static void push(int val)
    {
        REGISTERS[SP] -= 2;
        putShortIndirect((short)val, SP, 0);
    }

    public static short pop()
    {
        REGISTERS[SP] += 2;
        return getShort(indirect(SP, -2), indirect(SP, -1));
    }

    @SuppressWarnings("MagicNumber")
    public static void step()
    {
        ipCache = REGISTERS[IP];
        int opcode = indirectIncr(IP) & 0xff;

        switch (opcode)
        {
            case 0x21: //AND
                push(pop() & pop());
                break;
            case 0x22: //DUP
                push(getShort(indirect(SP, 0), indirect(SP, 1)));
                break;
            case 0x25: //MULT
                push(pop() * pop());
                break;
            case 0x26: //NOT
                push(~pop());
                break;
            case 0x2a: //OR
                push(pop() | pop());
                break;
            case 0x2b: //SUB
                int tmp = pop();
                push(pop() - tmp);
                break;
            case 0x2d: //MOD
                tmp = pop();
                push(pop() % tmp);
                break;
            case 0x2f: //DIV
                tmp = pop();
                push(pop() / tmp);
                break;
            case 0x3c: //SAR
                tmp = pop();
                push(pop() >> tmp);
                break;
            case 0x3d: //PUSH [8-bit VALUE]
                push(indirectIncr(IP));
                break;
            case 0x3e: //SHL
                tmp = pop();
                push(pop() << tmp);
                break;
            case 0x3f: //SHR
                tmp = pop();
                push(pop() >>> tmp);
                break;
            case 0x44: //PUSH [ADDR]
                int addr = getShort(indirectIncr(IP), indirectIncr(IP))
                        & MAX_ADDR;
                push(getShort(MEMORY[addr], MEMORY[addr + 1]));
                break;
            case 0x4b: //PUSH [REG]
                push(REGISTERS[indirectIncr(IP) & REGISTER_MASK]);
                break;
            case 0x4f: //POP [REG]
                REGISTERS[indirectIncr(IP) & REGISTER_MASK] = pop();
                break;
            case 0x50: //PUSH [8-bit OFFSET]([REG])
                int var = indirectIncr(IP) & REGISTER_MASK;
                int off = indirectIncr(IP);
                push(getShort(indirect(var, off), indirect(var, off + 1)));
                break;
            case 0x51: //PUSH ([REG])
                var = indirectIncr(IP) & REGISTER_MASK;
                push(indirect(var, 0));
                break;
            case 0x56: //STOREB [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                byte val = (byte)pop();
                MEMORY[addr] = val;
                break;
            case 0x57: //STOREW [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                putShort(pop(), addr);
                break;
            case 0x58: //JMP [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                REGISTERS[IP] = (short)addr;
                break;
            case 0x5a: //CALL [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                push(REGISTERS[IP]);
                REGISTERS[IP] = (short)addr;
                break;
            case 0x5e: //ADD
                push(pop() + pop());
                break;
            case 0x5f: //JMP ([REG])
                var = indirectIncr(IP) & REGISTER_MASK;
                REGISTERS[IP] = getShort(indirect(var, 0), indirect(var, 1));
                break;
            case 0x63: //STOREW [8-bit OFFSET]([REG])
                var = indirectIncr(IP) & REGISTER_MASK;
                off = indirectIncr(IP);
                putShortIndirect(pop(), var, off);
                break;
            case 0x64: //STOREB [8-bit OFFSET]([REG])
                var = indirectIncr(IP) & REGISTER_MASK;
                off = indirectIncr(IP);
                putIndirect((byte)pop(), var, off);
                break;
            case 0x65: //OUTPUTSTR [8-bit OFFSET]([REG])
                var = indirectIncr(IP) & REGISTER_MASK;
                off = indirectIncr(IP);
                outputString(((var == IP ? ipCache : REGISTERS[var]) + off)
                        & MAX_ADDR);
                break;
            case 0x67: //STOREB ([REG])
                var = indirectIncr(IP) & REGISTER_MASK;
                putIndirect((byte)pop(), var, 0);
                break;
            case 0x69: //STOREW ([REG])
                var = indirectIncr(IP) & REGISTER_MASK;
                putShortIndirect(pop(), var, 0);
                break;
            case 0x6a: //LOADW ([REG])
                var = indirectIncr(IP) & REGISTER_MASK;
                push(getShort(indirect(var, 0), indirect(var, 1)));
                break;
            case 0x6b: //OUTPUTSTR ([REG])
                var = indirectIncr(IP) & REGISTER_MASK;
                outputString((var == IP ? ipCache : REGISTERS[var]) & MAX_ADDR);
                break;
            case 0x6c: //LOADB [8-bit OFFSET]([REG])
                var = indirectIncr(IP) & REGISTER_MASK;
                off = indirectIncr(IP);
                push(indirect(var, off));
                break;
            case 0x6d: //EXIT [16-bit STATUSCODE]
                System.exit(getShort(indirectIncr(IP), indirectIncr(IP)));
                break;
            case 0x6f: //PUSH [16-bit VALUE]
                push(getShort(indirectIncr(IP), indirectIncr(IP)));
                break;
            case 0x7c: //XOR
                push(pop() ^ pop());
                break;
            case 0x7d: //CALL ([REG])
                var = indirectIncr(IP) & REGISTER_MASK;
                push(REGISTERS[IP]);
                REGISTERS[IP] = getShort(indirect(var, 0), indirect(var, 1));
                break;
            case 0x7e: //RET
                REGISTERS[IP] = pop();
                break;
            case 0x8e: //LOADB [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                push(MEMORY[addr]);
                break;
            case 0xda: //OUTPUTSTR [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                outputString(addr);
                break;
            case 0xdb: //OUTPUTNUM
                System.out.print(pop());
                break;
            case 0xdc: //POP
                pop();
                break;
            case 0xde: //JNZ [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                if (pop() != 0)
                    REGISTERS[IP] = (short)addr;
                break;
            case 0xdf: //INPUT
                try
                {
                    push(in.nextShort());
                }
                catch (Exception e)
                {
                    System.out.println("ERROR: Invalid number entered.");
                    System.exit(4);
                }
                break;
            case 0xfc: //JZ [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                if (pop() == 0)
                    REGISTERS[IP] = (short)addr;
                break;
            case 0xfe: //JN [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                if (pop() < 0)
                    REGISTERS[IP] = (short)addr;
                break;
            case 0xff: //JP [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                if (pop() > 0)
                    REGISTERS[IP] = (short)addr;
                break;
            default:
                System.out.printf("ERROR: Invalid opcode: 0x%02x\n@0x%04x",
                        opcode, Short.toUnsignedInt(ipCache));
                System.exit(1);
                break;
        }
    }

    private static void outputString(int addr)
    {

        int count = 0;
        while (MEMORY[addr + count] != 0)
            count++;
        byte[] output = new byte[count];
        System.arraycopy(MEMORY, addr, output, 0, count);
        System.out.print(new String(output));
    }
}
