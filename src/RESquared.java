//RE^2

// I always wanted to create my own variant of machine language. So here it
// is, a relatively simple language. I call it Revamped Evolution of
// programming Squared, or RE^2 for short. This is a reverse engineering
// problem within a reverse engineering problem! Here's the interpreter:
// Oh... and can you test out my latest flag program? I forgot the
// combination to the program. Good luck!
//

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

@SuppressWarnings("JavaDoc")
public class RESquared {

    public static final int SP = 13;
    public static final int BP = 14;
    public static final int IP = 15;
    public static final byte[] SIGNATURE = {0x52, 0x45, 0x5e, 0x32, 0x00, 0x00, 0x00, 0x01}; //RE^2

    public static final int MAX_ADDR = 0xFFFF;
    public static final int STACK_ADDR = 0xFFF0;
    public static final int VARIABLE_MASK = 0xF;
    public static short[] VARIABLES = new short[16];
    public static byte[] MEMORY = new byte[MAX_ADDR + 1];

    public static Scanner in = new Scanner(System.in);
    public static short ipCache;

    public static void main(String[] args)
    {
        System.out.println("RE^2 Interpreter v1.0");
        System.out.println("Copyright (c) 2017 theKidOfArcrania\n");

        if (args.length == 0)
        {
            System.out.println("Usage: java RESquared <File>");
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

            VARIABLES[IP] = readShort(dis); //entry point
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

            VARIABLES[BP] = VARIABLES[SP] = (short)STACK_ADDR;
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
            System.out.println("ERROR: Segmentation Fault");
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
        byte val = MEMORY[VARIABLES[variable] & MAX_ADDR];
        VARIABLES[variable]++;
        return val;
    }

    public static byte indirect(int variable, int offset)
    {
        byte val = MEMORY[((variable == IP ? ipCache : VARIABLES[variable]) &
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
        MEMORY[((variable == IP ? ipCache : VARIABLES[variable]) & MAX_ADDR) +
                offset] = val;
    }

    public static void putShort(short val, int addr)
    {
        MEMORY[addr] = (byte)val;
        MEMORY[addr + 1] = (byte)(val >> 8);
    }

    public static void putShortIndirect(short val, int variable, int offset)
    {
        putShort(val, ((variable == IP ? ipCache : VARIABLES[variable]) &
                MAX_ADDR) + offset);
    }

    public static void push(int val)
    {
        VARIABLES[SP] -= 2;
        putShortIndirect((short)val, SP, 0);
    }

    public static short pop()
    {
        VARIABLES[SP] += 2;
        return getShort(indirect(SP, -2), indirect(SP, -1));
    }

    @SuppressWarnings("MagicNumber")
    public static void step()
    {
        ipCache = VARIABLES[IP];
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
            case 0x3d: //LOADVAL8 [8-bit VALUE]
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
            case 0x44: //LOADMEM16 [ADDR]
                int addr = getShort(indirectIncr(IP), indirectIncr(IP))
                        & MAX_ADDR;
                push(getShort(MEMORY[addr], MEMORY[addr + 1]));
                break;
            case 0x4b: //LOADVAR [VAR]
                push(VARIABLES[indirectIncr(IP) & VARIABLE_MASK]);
                break;
            case 0x4f: //STOREVAR [VAR]
                VARIABLES[indirectIncr(IP) & VARIABLE_MASK] = pop();
                break;
            case 0x50: //LOADMEM16 [8-bit OFFSET]([VAR])
                int var = indirectIncr(IP) & VARIABLE_MASK;
                int off = indirectIncr(IP);
                push(getShort(indirect(var, off), indirect(var, off + 1)));
                break;
            case 0x51: //LOADMEM8 ([VAR])
                var = indirectIncr(IP) & VARIABLE_MASK;
                push(indirect(var, 0));
                break;
            case 0x56: //STOREMEM8 [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                byte val = (byte)pop();
                MEMORY[addr] = val;
                break;
            case 0x57: //STOREMEM16 [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                putShort(pop(), addr);
                break;
            case 0x58: //JMP [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                VARIABLES[IP] = (short)addr;
                break;
            case 0x5a: //CALL [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                push(VARIABLES[IP]);
                VARIABLES[IP] = (short)addr;
                break;
            case 0x5e: //ADD
                push(pop() + pop());
                break;
            case 0x5f: //JMP *[VAR]
                var = indirectIncr(IP) & VARIABLE_MASK;
                VARIABLES[IP] = getShort(indirect(var, 0), indirect(var, 1));
                break;
            case 0x63: //STOREMEM16 [8-bit OFFSET]([VAR])
                var = indirectIncr(IP) & VARIABLE_MASK;
                off = indirectIncr(IP);
                putShortIndirect(pop(), var, off);
                break;
            case 0x64: //STOREMEM8 [8-bit OFFSET]([VAR])
                var = indirectIncr(IP) & VARIABLE_MASK;
                off = indirectIncr(IP);
                putIndirect((byte)pop(), var, off);
                break;
            case 0x65: //OUTPUTSTR [8-bit OFFSET]([VAR])
                var = indirectIncr(IP) & VARIABLE_MASK;
                off = indirectIncr(IP);
                outputString(((var == IP ? ipCache : VARIABLES[var]) + off)
                        & MAX_ADDR);
                break;
            case 0x67: //STOREMEM8 ([VAR])
                var = indirectIncr(IP) & VARIABLE_MASK;
                putIndirect((byte)pop(), var, 0);
                break;
            case 0x69: //STOREMEM16 ([VAR])
                var = indirectIncr(IP) & VARIABLE_MASK;
                putShortIndirect(pop(), var, 0);
                break;
            case 0x6a: //LOADMEM16 ([VAR])
                var = indirectIncr(IP) & VARIABLE_MASK;
                push(getShort(indirect(var, 0), indirect(var, 1)));
                break;
            case 0x6b: //OUTPUTSTR ([VAR])
                var = indirectIncr(IP) & VARIABLE_MASK;
                outputString((var == IP ? ipCache : VARIABLES[var]) & MAX_ADDR);
                break;
            case 0x6c: //LOADMEM8 [8-bit OFFSET]([VAR])
                var = indirectIncr(IP) & VARIABLE_MASK;
                off = indirectIncr(IP);
                push(indirect(var, off));
                break;
            case 0x6d: //EXIT [16-bit STATUSCODE]
                System.exit(getShort(indirectIncr(IP), indirectIncr(IP)));
                break;
            case 0x6f: //LOADVAL16 [16-bit VALUE]
                push(getShort(indirectIncr(IP), indirectIncr(IP)));
                break;
            case 0x7c: //XOR
                push(pop() ^ pop());
                break;
            case 0x7d: //CALL *[VAR]
                var = indirectIncr(IP) & VARIABLE_MASK;
                push(VARIABLES[IP]);
                VARIABLES[IP] = indirect(var, 0);
                break;
            case 0x7e: //RET
                VARIABLES[IP] = pop();
                break;
            case 0x8e: //LOADMEM8 [ADDR]
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
                    VARIABLES[IP] = (short)addr;
                break;
            case 0xdf: //INPUT [VAR]
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
                    VARIABLES[IP] = (short)addr;
                break;
            case 0xfe: //JN [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                if (pop() < 0)
                    VARIABLES[IP] = (short)addr;
                break;
            case 0xff: //JP [ADDR]
                addr = getShort(indirectIncr(IP), indirectIncr(IP)) & MAX_ADDR;
                if (pop() > 0)
                    VARIABLES[IP] = (short)addr;
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
