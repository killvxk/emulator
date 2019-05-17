package cn.banny.emulator;

import cn.banny.emulator.pointer.UnicornPointer;
import com.sun.jna.Pointer;

public abstract class Symbol {

    private final String name;

    public Symbol(String name) {
        this.name = name;
    }

    public abstract Number[] call(Emulator emulator, Object... args);

    public  abstract long getAddress();

    public abstract long getValue();

    public abstract boolean isUndef();

    public Pointer createPointer(Emulator emulator) {
        return UnicornPointer.pointer(emulator, getAddress());
    }

    public String getName() {
        return name;
    }

    public abstract String getModuleName();

    @Override
    public String toString() {
        return name;
    }
}
