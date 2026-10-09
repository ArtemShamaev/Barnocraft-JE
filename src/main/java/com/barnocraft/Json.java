package com.barnocraft;

import java.util.*;

/** Small strict JSON codec for the versioned mod protocol; no external runtime dependency. */
final class Json {
    static Map<String,Object> obj(Object value) {
        if(!(value instanceof Map<?,?>)) throw new IllegalArgumentException("Expected object");
        @SuppressWarnings("unchecked") Map<String,Object> result=(Map<String,Object>)value; return result;
    }
    static List<?> list(Object value) { if(!(value instanceof List<?> l)) throw new IllegalArgumentException("Expected array");return l; }
    static String str(Map<String,Object> m,String k,String fallback) {
        Object v=m.get(k);if(v==null)return fallback;if(!(v instanceof String))throw new IllegalArgumentException(k+": expected string");return (String)v;
    }
    static double num(Map<String,Object> m,String k,double fallback) {
        Object v=m.get(k);if(v==null)return fallback;if(!(v instanceof Number n) || !Double.isFinite(n.doubleValue()))throw new IllegalArgumentException(k+": expected finite number");return ((Number)v).doubleValue();
    }
    static int integer(Map<String,Object> m,String k,int fallback,int min,int max) {
        double n=num(m,k,fallback);if(n!=Math.rint(n) || n<min || n>max)throw new IllegalArgumentException(k+": out of range");return (int)n;
    }
    static String stringify(Object v) { StringBuilder s=new StringBuilder();write(s,v);return s.toString(); }
    private static void write(StringBuilder s,Object v) {
        if(v==null) {s.append("null");return;}
        if(v instanceof String text) {
            s.append('"');for(char c:text.toCharArray()) switch(c) {
                case '"' -> s.append("\\\""); case '\\' -> s.append("\\\\");case '\n' -> s.append("\\n");case '\r' -> s.append("\\r");case '\t' -> s.append("\\t");
                default -> {if(c<32)s.append(String.format("\\u%04x",(int)c));else s.append(c);}
            }s.append('"');return;
        }
        if(v instanceof Number n) {if(!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("Non-finite JSON number");s.append(v);return;}
        if(v instanceof Boolean) {s.append(v);return;}
        if(v instanceof Map<?,?> map) {s.append('{');boolean first=true;for(var e:map.entrySet()){if(!first)s.append(',');first=false;write(s,e.getKey().toString());s.append(':');write(s,e.getValue());}s.append('}');return;}
        if(v instanceof Iterable<?> list) {s.append('[');boolean first=true;for(Object x:list){if(!first)s.append(',');first=false;write(s,x);}s.append(']');return;}
        throw new IllegalArgumentException("Unsupported JSON value "+v.getClass());
    }
    static Object parse(String text) {Parser p=new Parser(text);Object v=p.value(0);p.space();if(p.i!=text.length())throw new IllegalArgumentException("Trailing JSON data");return v;}
    private static final class Parser {
        final String s;int i;Parser(String s){this.s=s;}
        void space(){while(i<s.length() && Character.isWhitespace(s.charAt(i)))i++;}
        char take(){if(i>=s.length())throw new IllegalArgumentException("Incomplete JSON");return s.charAt(i++);}
        Object value(int depth){
            if(depth>32)throw new IllegalArgumentException("JSON nesting limit");space();char c=take();
            if(c=='"')return string();
            if(c=='{' || c=='['){boolean object=c=='{';Map<String,Object> m=new LinkedHashMap<>();List<Object> l=new ArrayList<>();space();
                if(i<s.length() && s.charAt(i)==(object?'}':']')){i++;return object?m:l;}
                while(true){space();if(object){if(take()!='"')throw new IllegalArgumentException("Expected key");String key=string();space();if(take()!=':')throw new IllegalArgumentException("Expected colon");if(m.containsKey(key))throw new IllegalArgumentException("Duplicate key");m.put(key,value(depth+1));}else l.add(value(depth+1));
                    space();char end=take();if(end==(object?'}':']'))return object?m:l;if(end!=',')throw new IllegalArgumentException("Expected comma");}
            }
            if(c=='t' && s.startsWith("rue",i)){i+=3;return true;}if(c=='f' && s.startsWith("alse",i)){i+=4;return false;}if(c=='n' && s.startsWith("ull",i)){i+=3;return null;}
            int start=i-1;while(i<s.length() && "0123456789.eE+-".indexOf(s.charAt(i))>=0)i++;
            String n=s.substring(start,i);if(!n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw new IllegalArgumentException("Invalid number");
            double d=Double.parseDouble(n);if(!Double.isFinite(d))throw new IllegalArgumentException("Number overflow");return d;
        }
        String string(){StringBuilder b=new StringBuilder();while(true){char c=take();if(c=='"')return b.toString();if(c<32)throw new IllegalArgumentException("Control character");if(c=='\\'){c=take();c=switch(c){case '"','\\','/' -> c;case 'n' -> '\n';case 'r' -> '\r';case 't' -> '\t';case 'b' -> '\b';case 'f' -> '\f';case 'u' -> {if(i+4>s.length())throw new IllegalArgumentException("Unicode escape");char u=(char)Integer.parseInt(s.substring(i,i+4),16);i+=4;yield u;}default -> throw new IllegalArgumentException("Invalid escape");};}b.append(c);}}
    }
}
