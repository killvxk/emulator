package com.sun.jna;

import cn.banny.auxiliary.Inspector;
import cn.banny.emulator.Emulator;
import cn.banny.emulator.LibraryResolver;
import cn.banny.emulator.Module;
import cn.banny.emulator.Symbol;
import cn.banny.emulator.arm.ARMEmulator;
import cn.banny.emulator.arm.HookStatus;
import cn.banny.emulator.hook.ReplaceCallback;
import cn.banny.emulator.hook.hookzz.*;
import cn.banny.emulator.hook.whale.IWhale;
import cn.banny.emulator.hook.whale.Whale;
import cn.banny.emulator.hook.xhook.IxHook;
import cn.banny.emulator.hook.xhook.XHookImpl;
import cn.banny.emulator.linux.android.AndroidARM64Emulator;
import cn.banny.emulator.linux.android.AndroidResolver;
import cn.banny.emulator.linux.android.dvm.*;
import cn.banny.emulator.memory.Memory;
import cn.banny.emulator.pointer.UnicornPointer;
import unicorn.Arm64Const;
import unicorn.Unicorn;

import java.io.File;
import java.io.IOException;

public class JniDispatch64 extends AbstractJni {

    private static LibraryResolver createLibraryResolver() {
        return new AndroidResolver(23);
    }

    private static ARMEmulator createARMEmulator() {
        return new AndroidARM64Emulator("com.sun.jna");
    }

    private final ARMEmulator emulator;
    private final VM vm;
    private final Module module;

    private final DvmClass Native;

    private JniDispatch64() throws IOException {
        emulator = createARMEmulator();
//        emulator.attach().addBreakPoint(null, 0xffffe09e0L);
//        emulator.attach().addBreakPoint(null, 0xffffe0a04L);
        final Memory memory = emulator.getMemory();
        memory.setLibraryResolver(createLibraryResolver());
        memory.setCallInitFunction();

        vm = emulator.createDalvikVM(null);
        vm.setJni(this);
        DalvikModule dm = vm.loadLibrary(new File("src/test/resources/example_binaries/arm64-v8a/libjnidispatch.so"), false);
        dm.callJNI_OnLoad(emulator);
        this.module = dm.getModule();

        Native = vm.resolveClass("com/sun/jna/Native");
    }

    private void destroy() throws IOException {
        emulator.close();
        System.out.println("destroy");
    }

    public static void main(String[] args) throws Exception {
        JniDispatch64 test = new JniDispatch64();

        test.test();

        test.destroy();
    }

    private void test() throws IOException {
        IxHook xHook = XHookImpl.getInstance(emulator);
        xHook.register("libjnidispatch.so", "malloc", new ReplaceCallback() {
            @Override
            public HookStatus onCall(Emulator emulator, long originFunction) {
                Unicorn unicorn = emulator.getUnicorn();
                int size = ((Number) unicorn.reg_read(Arm64Const.UC_ARM64_REG_X0)).intValue();
                System.out.println("malloc=" + size);
                return HookStatus.RET64(unicorn, originFunction);
            }
        });
        xHook.refresh();

        IWhale whale = Whale.getInstance(emulator);
        Symbol free = emulator.getMemory().findModule("libc.so").findSymbolByName("free");
        whale.WInlineHookFunction(free, new ReplaceCallback() {
            @Override
            public HookStatus onCall(Emulator emulator, long originFunction) {
                System.out.println("WInlineHookFunction free=" + UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X0));
                return HookStatus.RET(emulator.getUnicorn(), originFunction);
            }
        });

        long start = System.currentTimeMillis();
        final int size = 0x20;
        Number ret = Native.callStaticJniMethod(emulator, "malloc(J)J", size);
        Pointer pointer = UnicornPointer.pointer(emulator, ret.intValue() & 0xffffffffL);
        assert pointer != null;
        pointer.setString(0, getClass().getName());
        vm.deleteLocalRefs();
        Inspector.inspect(pointer.getByteArray(0, size), "malloc ret=0x" + Long.toHexString(ret.longValue()) + ", offset=" + (System.currentTimeMillis() - start) + "ms");

        IHookZz hookZz = HookZz.getInstance(emulator);
        Symbol newJavaString = module.findSymbolByName("newJavaString");
        hookZz.wrap(newJavaString, new WrapCallback<Arm64RegisterContext>() {
            @Override
            public void preCall(Emulator emulator, Arm64RegisterContext ctx, HookEntryInfo info) {
                Pointer value = ctx.getXPointer(1);
                Pointer encoding = ctx.getXPointer(2);
                System.out.println("newJavaString value=" + value.getString(0) + ", encoding=" + encoding.getString(0));
            }
        });

        ret = Native.callStaticJniMethod(emulator, "getNativeVersion()Ljava/lang/String;");
        long hash = ret.intValue() & 0xffffffffL;
        StringObject version = vm.getObject(hash);
        vm.deleteLocalRefs();
        System.out.println("getNativeVersion version=" + version.getValue() + ", offset=" + (System.currentTimeMillis() - start) + "ms");

        ret = Native.callStaticJniMethod(emulator, "getAPIChecksum()Ljava/lang/String;");
        hash = ret.intValue() & 0xffffffffL;
        StringObject checksum = vm.getObject(hash);
        vm.deleteLocalRefs();
        System.out.println("getAPIChecksum checksum=" + checksum.getValue() + ", offset=" + (System.currentTimeMillis() - start) + "ms");

        ret = Native.callStaticJniMethod(emulator, "sizeof(I)I", 0);
        vm.deleteLocalRefs();
        System.out.println("sizeof POINTER_SIZE=" + ret.intValue() + ", offset=" + (System.currentTimeMillis() - start) + "ms");
    }

    @Override
    public DvmObject callStaticObjectMethod(VM vm, DvmClass dvmClass, String signature, String methodName, String args, VarArg varArg) {
        if ("java/lang/System->getProperty(Ljava/lang/String;)Ljava/lang/String;".equals(signature)) {
            StringObject string = varArg.getObject(0);
            return new StringObject(vm, System.getProperty(string.getValue()));
        }

        return super.callStaticObjectMethod(vm, dvmClass, signature, methodName, args, varArg);
    }
}
