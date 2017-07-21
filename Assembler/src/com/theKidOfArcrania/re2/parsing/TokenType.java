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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum TokenType
{
    REGISTER("%(?:\\d+|[ISBisb]P)"), SYMBOL("[A-Za-z_][A-Za-z0-9_]*"),
    NUMBER("\\$([+-]?(?:0x[A-Fa-f0-9]+|0[0-7]+|[1-9][0-9]*|0))"),
    LABEL("[A-Za-z_][A-Za-z0-9_]*:"), ADDRESS("(0x[A-Fa-f0-9]+|0[0-7]+|[1-9][0-9]*|0)"),
    INDIRECT("([+-]?(?:0x[A-Fa-f0-9]+|0[0-7]+|[1-9][0-9]*|0))?\\((%(?:\\d+|[ISBisb]P))\\)"),
    DIRECTIVE("\\.[A-Za-z_][A-Za-z0-9_]*"), INVALID("$.+^");

    private final Pattern pattern;

    TokenType(String pattern)
    {
        this.pattern = Pattern.compile(pattern);
    }

    public Matcher match(String token)
    {
        return pattern.matcher(token);
    }
}
