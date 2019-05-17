package cn.banny.emulator.hook.whale;

import cn.banny.emulator.Emulator;
import cn.banny.emulator.Module;
import cn.banny.emulator.Symbol;
import cn.banny.emulator.hook.BaseHook;
import cn.banny.emulator.hook.ReplaceCallback;
import com.sun.jna.Pointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

public class Whale extends BaseHook implements IWhale {

    private static final Log log = LogFactory.getLog(Whale.class);

    public static IWhale getInstance(Emulator emulator) {
        IWhale whale = emulator.get(Whale.class.getName());
        if (whale == null) {
            try {
                whale = new Whale(emulator);
                emulator.set(Whale.class.getName(), whale);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return whale;
    }

    private final Symbol WInlineHookFunction, WImportHookFunction;

    private Whale(Emulator emulator) throws IOException {
        super(emulator);

        boolean isIOS = ".dylib".equals(emulator.getLibraryExtension());
        Module module = emulator.getMemory().load(resolveLibrary(emulator, "libwhale"));
        WInlineHookFunction = module.findSymbolByName(isIOS ? "_WInlineHookFunction" : "WInlineHookFunction");
        WImportHookFunction = module.findSymbolByName(isIOS ? "_WImportHookFunction" : "WImportHookFunction");
        log.debug("WInlineHookFunction=" + WInlineHookFunction + ", WImportHookFunction=" + WImportHookFunction);

        if (WInlineHookFunction == null) {
            throw new IllegalStateException("WInlineHookFunction is null");
        }
        if (WImportHookFunction == null) {
            throw new IllegalStateException("WImportHookFunction is null");
        }
    }

    @Override
    public void WInlineHookFunction(long address, final ReplaceCallback callback) {
        final Pointer backup = emulator.getMemory().malloc(emulator.getPointerSize(), false).getPointer();
        Pointer replace = createReplacePointer(callback, backup);
        WInlineHookFunction.call(emulator, address, replace, backup);
    }

    @Override
    public void WInlineHookFunction(Symbol symbol, ReplaceCallback callback) {
        WInlineHookFunction(symbol.getAddress(), callback);
    }

    @Override
    public void WImportHookFunction(String symbol, final ReplaceCallback callback) {
        final Pointer backup = emulator.getMemory().malloc(emulator.getPointerSize(), false).getPointer();
        Pointer replace = createReplacePointer(callback, backup);
        WImportHookFunction.call(emulator, symbol, null, replace, backup);
    }

}
