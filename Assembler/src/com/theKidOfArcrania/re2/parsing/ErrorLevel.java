package com.theKidOfArcrania.re2.parsing;

public enum ErrorLevel
{
    ERROR("Error"), WARNING("Warning"), INFO(""), DEBUG(""), NONE("");

    private final String mode;

    ErrorLevel(String mode)
    {
        this.mode = mode;
    }

    public String getMode()
    {
        return mode;
    }

    @Override
    public String toString()
    {
        String m = getMode();
        if (m.isEmpty())
            return "";
        else
            return m + ": ";
    }
}
