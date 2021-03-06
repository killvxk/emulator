package cn.banny.unidbg.spi;

import capstone.Capstone;

import java.io.PrintStream;

/**
 * disassembler
 * Created by zhkl0228 on 2017/5/9.
 */

public interface Disassembler {

    boolean printAssemble(PrintStream out, long address, int size);
    Capstone.CsInsn[] disassemble(long address, int size, long count);
    Capstone.CsInsn[] disassemble(long address, byte[] code, boolean thumb);

}
