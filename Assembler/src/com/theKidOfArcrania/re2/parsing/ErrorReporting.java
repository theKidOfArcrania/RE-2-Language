package com.theKidOfArcrania.re2.parsing;

public class ErrorReporting
{
    public static final ErrorReporting NOTHING = new ErrorReporting
            (ErrorLevel.NONE, "");

    private final ErrorLevel level;
    private final String description;

    public ErrorReporting(ErrorLevel level, String description)
    {
        this.level = level;
        this.description = description;
    }

    public ErrorLevel getLevel()
    {
        return level;
    }

    public String getDescription()
    {
        return description;
    }
}
