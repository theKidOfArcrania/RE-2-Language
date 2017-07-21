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
