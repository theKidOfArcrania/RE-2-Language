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

package com.theKidOfArcrania.re2.parsing;

import com.theKidOfArcrania.re2.RESquared;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.regex.Matcher;

import static com.theKidOfArcrania.re2.RESquared.MAX_ADDR;


@SuppressWarnings("JavaDoc")
public class TokenReader implements Closeable
{
    private final String fileName;
    private final Scanner in;

    private boolean debugMode;

    private int errors;
    private int warnings;

    private boolean emptyLine;
    private int lineNum;
    private int codeOffset;
    private Scanner lineReader;
    private ArrayList<String> lines;

    private TokenType tokenType;
    private Matcher tokenMatch;

    private EnumMap<ErrorSituation, ErrorReporting> defaultReporting = new EnumMap<>(ErrorSituation.class);
    private Deque<EnumMap<ErrorSituation, ErrorReporting>> reportingStates = new LinkedList<>();

    public TokenReader(File file) throws FileNotFoundException
    {
        fileName = file.getName();
        in = new Scanner(file);
        errors = 0;
        warnings = 0;

        lineReader = null;
        lineNum = 0;
        lines = new ArrayList<>();

        initErrorSituations();
    }

    @Override
    public void close()
    {
        lineReader.close();
    }

    /* ***********************
     * Line-processing functionality.
     * ***********************/

    public boolean checkEndLine()
    {
        tokenType = null;
        tokenMatch = null;

        if (lineReader.hasNext())
        {
            lineReader.next();
            reportSituation(ErrorSituation.ERROR_EXTRA_TOKEN);
            traceCodeToken(0);
            return false;
        }
        return true;
    }

    public String currentLine()
    {
        if (lineReader == null)
            return null;
        return lines.get(lineNum - 1);
    }

    public int getLineNum()
    {
        return lineNum;
    }

    public boolean hasNextLine()
    {
        return in.hasNextLine();
    }

    public void nextLine()
    {
        String line = in.nextLine();
        lines.add(line);
        processLine(line);
        lineNum = lines.size();
    }

    public void visitLine(int lineNum)
    {
        processLine(lines.get(lineNum - 1));
        this.lineNum = lineNum;
    }

    public String remaining()
    {
        if (lineReader == null)
            throw new IllegalStateException("Not reading a line.");
        if (lineReader.hasNextLine())
            return lineReader.nextLine();
        return "";
    }

    private void processLine(String line)
    {
        String code = line.split("#")[0].trim();
        codeOffset = line.indexOf(code);

        if (code.isEmpty())
            emptyLine = true;

        lineReader = new Scanner(code);

        tokenType = null;
        tokenMatch = null;
    }

    /* ***********************
     * Token-processing functionality.
     * ***********************/

    public String currentToken()
    {
        if (lineReader == null)
            throw new IllegalStateException("Not reading a line.");
        return lineReader.match().group();
    }

    public boolean hasNextToken()
    {
        if (lineReader == null)
            throw new IllegalStateException("Not reading a line.");
        return lineReader.hasNext();
    }

    public String nextToken()
    {
        if (lineReader == null)
            throw new IllegalStateException("Not reading a line.");

        tokenType = null;
        tokenMatch = null;

        if (!lineReader.hasNext())
        {
            reportSituation(ErrorSituation.ERROR_MISSING_TOKEN);
            traceCode(lines.get(lineNum - 1).length());
            return null;
        }

        String token = lineReader.next();
        for (TokenType type : TokenType.values())
        {
            Matcher match = type.match(token);
            if (match.matches())
            {
                tokenType = type;
                tokenMatch = match;
                break;
            }
        }

        if (tokenType == null)
        {
            tokenType = TokenType.INVALID;
            reportSituation(ErrorSituation.ERROR_INVALID_TOKEN);
            traceCodeToken(0);
        }

        return token;
    }

    public byte[] tokenBinary()
    {
        if (tokenType == null)
            throw new IllegalStateException("No current token.");
        switch (tokenType)
        {
            case REGISTER:
                int reg = parseRegister(tokenMatch.group());
                if (reg == -1)
                {
                    reportSituation(ErrorSituation.ERROR_INVALID_REGISTER);
                    traceCodeToken(0);
                    return null;
                }
                return new byte[] {(byte)reg};
            case INDIRECT:
                reg = parseRegister(tokenMatch.group(2));
                if (reg == -1)
                {
                    reportSituation(ErrorSituation.ERROR_INVALID_REGISTER);
                    traceCodeToken(tokenMatch.start(2));
                    return null;
                }

                if (tokenMatch.group(1) == null)
                    return new byte[] {(byte) reg};

                try
                {
                    return new byte[] {(byte) reg, Byte.decode(tokenMatch.group(1))};
                }
                catch (NumberFormatException e)
                {
                    reportSituation(ErrorSituation.ERROR_INVALID_OFFSET);
                    traceCodeToken(tokenMatch.start(1));
                    return null;
                }
            case NUMBER:
                Integer num = tokenNumber();
                if (num == null)
                    return null;
                int val = num;
                if (val >= Byte.MIN_VALUE && val <= Byte.MAX_VALUE )
                    return new byte[]{(byte)val};
                else
                    return new byte[]{(byte)val, (byte)(val >> 8)};
            case ADDRESS:
                num = tokenNumber(0, MAX_ADDR);
                if (num == null)
                    return null;
                val = num;
                return new byte[]{(byte)val, (byte)(val >> 8)};
            case SYMBOL:
                throw new IllegalStateException("Token must not be a symbolic type.");
            case INVALID:
                throw new IllegalStateException("Invalid token.");
            default:
                throw new InternalError();
        }
    }

    public TokenType tokenType()
    {
        if (tokenType == null)
            throw new IllegalStateException("No current token.");
        return tokenType;
    }

    public Integer tokenNumber()
    {
        if (tokenType == TokenType.ADDRESS)
            return tokenNumber(0, MAX_ADDR);
        else
            return tokenNumber(Short.MIN_VALUE, Short.MAX_VALUE);
    }

    public Integer tokenNumber(int min, int max)
    {
        if (tokenType == null)
            throw new IllegalStateException("No current token.");

        if (tokenType != TokenType.NUMBER && tokenType != TokenType.ADDRESS && tokenType != TokenType.INVALID)
        {
            reportSituation(ErrorSituation.ERROR_NUMBER_PARSE);
            traceCodeToken(0);
            return null;
        }

        try
        {
            int value = Integer.decode(tokenMatch.group(1));
            if (value < min || value > max)
            {
                reportSituation(ErrorSituation.ERROR_NUMBER_RANGE, "\\$MIN", "" + min, "\\$MAX", "" + max);
                traceCodeToken(0);
                return null;
            }
            return value;
        }
        catch (NumberFormatException e)
        {
            reportSituation(ErrorSituation.ERROR_NUMBER_PARSE);
            traceCodeToken(0);
            return null;
        }
    }
    public int tokenPosition()
    {
        return lineReader.match().start() + codeOffset;
    }



    private int parseRegister(String reg)
    {
        switch (reg.toUpperCase())
        {
            case "%IP":
                return RESquared.IP;
            case "%BP":
                return RESquared.BP;
            case "%SP":
                return RESquared.SP;
        }

        try
        {
            byte num = Byte.parseByte(reg.substring(1));
            if (num < 0 || num >= RESquared.REGISTER_COUNT)
                return -1;
            return num;
        }
        catch (RuntimeException e)
        {
            return -1;
        }
    }

    /* ***********************
     * Error-processing functionality.
     * ***********************/

    public void saveErrorReporting()
    {
        reportingStates.push(defaultReporting.clone());
    }

    public void restoreErrorReporting()
    {
        defaultReporting.clear();
        defaultReporting.putAll(reportingStates.pop());
    }

    public ErrorReporting getDefaultReporting(ErrorSituation situation)
    {
        return defaultReporting.get(situation);
    }

    public ErrorReporting setDefaultReporting(ErrorSituation situation, ErrorReporting report)
    {
        Objects.requireNonNull(report, "Must have non-null report");
        return defaultReporting.put(situation, report);
    }

    public void setDebugMode(boolean debugMode)
    {
        this.debugMode = debugMode;
    }

    public boolean isDebugMode()
    {
        return debugMode;
    }

    public int getErrors()
    {
        return errors;
    }

    public int getWarnings()
    {
        return warnings;
    }

    public void reportSituation(ErrorSituation situation, String... expansions)
    {
        log(defaultReporting.get(situation), expansions);
    }

    public void traceCodeToken(int offset)
    {
        traceCode(codeOffset + lineReader.match().start() + offset);
    }

    public void traceCode(int charInd)
    {
        if (lineReader == null)
            throw new IllegalStateException("Not reading a line.");

        System.err.println(fileName + ":" + lineNum + ":" + (charInd + 1));
        System.err.println(currentLine());
        for (int i = 0; i < charInd; i++)
            System.err.print(" ");
        System.err.println("^\n");
    }

    public void error(String description, String... expansions)
    {
        log(new ErrorReporting(ErrorLevel.ERROR, description));
    }

    public void log(ErrorReporting err, String... expansions)
    {
        if (expansions.length % 2 == 1)
            throw new IllegalArgumentException("Expansion arguments must be even");
        switch (err.getLevel())
        {
            case ERROR:
                errors++;
                break;
            case WARNING:
                warnings++;
                break;
            case DEBUG:
                if (!debugMode)
                    return;
            case NONE:
                return;
        }
        String descript = err.getDescription();
        for (int i = 0; i < expansions.length; i+=2)
            descript = descript.replaceAll(expansions[i], expansions[i+1]);

        System.err.println(err.getLevel() + descript);
    }

    private void initErrorSituations()
    {
        for (ErrorSituation sit : ErrorSituation.values())
            defaultReporting.put(sit, ErrorReporting.NOTHING);
        this.setDefaultReporting(ErrorSituation.ERROR_EXTRA_TOKEN, new ErrorReporting(ErrorLevel.ERROR,
                "expected: line terminator or end of line."));
        this.setDefaultReporting(ErrorSituation.ERROR_INVALID_OFFSET, new ErrorReporting(ErrorLevel.ERROR,
                "expected: valid 8-bit number offset for indirect address."));
        this.setDefaultReporting(ErrorSituation.ERROR_INVALID_REGISTER, new ErrorReporting(ErrorLevel.ERROR,
                "expected: valid register number (0 to 15) or IP, BP, SP prefixed by `%`."));
        this.setDefaultReporting(ErrorSituation.ERROR_INVALID_TOKEN, new ErrorReporting(ErrorLevel.ERROR,
                "expected: invalid character(s)."));
        this.setDefaultReporting(ErrorSituation.ERROR_MISSING_TOKEN, new ErrorReporting(ErrorLevel.ERROR,
                "expected: missing token."));
        this.setDefaultReporting(ErrorSituation.ERROR_NUMBER_PARSE, new ErrorReporting(ErrorLevel.ERROR,
                "expected: valid hexadecimal or decimal number from $MIN to $MAX."));
        this.setDefaultReporting(ErrorSituation.ERROR_NUMBER_RANGE, new ErrorReporting(ErrorLevel.ERROR,
                "expected: valid hexadecimal or decimal number from $MIN to $MAX."));
    }
}
