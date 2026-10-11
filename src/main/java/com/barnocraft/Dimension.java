package com.barnocraft;

public enum Dimension {
    SURFACE("Обычный мир"), HELL_FOREST("Адский лес");
    public final String title;
    Dimension(String title){this.title=title;}
    public Dimension other(){return this==SURFACE?HELL_FOREST:SURFACE;}
}
