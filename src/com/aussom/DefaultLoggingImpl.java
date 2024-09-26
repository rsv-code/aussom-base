package com.aussom;

public class DefaultLoggingImpl implements LoggingInt {
    public static int TRACE = 0;
    public static int DEBUG = 1;
    public static int INFO = 2;
    public static int WARNING = 3;
    public static int ERROR = 4;

    private int level = INFO;

    public void setLevel(int Level) {
		this.level = Level;
	}

    @Override
    public void log(String Str) {
        if (this.level <= INFO)
            System.out.println(Str);
    }

    @Override
    public void trc(String Str) {
        if (this.level <= TRACE)
            System.out.println("[trace] " + Str);
    }

    @Override
    public void dbg(String Str) {
        if (this.level <= DEBUG)
            System.out.println("[debug] " + Str);
    }

    @Override
    public void info(String Str) {
        if (this.level <= INFO)
            System.out.println("[info] " + Str);
    }

    @Override
    public void warn(String Str) {
        if (this.level <= WARNING)
            System.out.println("[warning] " + Str);
    }

    @Override
    public void err(String Str) {
        if (this.level <= ERROR)
            System.out.println("[error] " + Str);
    }

    @Override
    public void print(String Text) {
        System.out.print(Text);
    }

    @Override
    public void println(String Text) {
        System.out.println(Text);
    }
}
